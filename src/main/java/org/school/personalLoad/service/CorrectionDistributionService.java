package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.CorrectionDistributionDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CorrectionDistributionService {

    private final CorrectionSpecialistCatalogEntryRepository specialistRepository;
    private final CorrectionSpecialistStaffRepository staffRepository;
    private final CorrectionScheduleGroupRepository groupRepository;
    private final CorrectionStudentAssignmentRepository assignmentRepository;
    private final StudentSupportDocumentCorrectionRepository correctionRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final PpkProtocolRepository ppkRepository;
    private final OvzApplicationChoiceRepository applicationChoiceRepository;

    @Transactional(readOnly = true)
    public CorrectionDistributionDtos.Overview overview(String academicYear) {
        Map<NeedKey, Need> needs = needs(academicYear);
        Set<Long> signedStudents = signedStudents(academicYear);
        Map<NeedKey, CorrectionStudentAssignment> assignments = validAssignments(academicYear, needs);

        Map<Long, List<Need>> bySpecialist = needs.values().stream()
                .filter(need -> signedStudents.contains(need.student().getId()))
                .collect(Collectors.groupingBy(need -> need.specialist().getId(), LinkedHashMap::new, Collectors.toList()));

        CorrectionDistributionDtos.Overview result = new CorrectionDistributionDtos.Overview();
        result.setDirections(specialistRepository.findAllByOrderByNameAsc().stream()
                .filter(CorrectionSpecialistCatalogEntry::isActive)
                .map(specialist -> directionSummary(specialist, bySpecialist.getOrDefault(specialist.getId(), List.of()), assignments))
                .filter(item -> item.getNeededCount() > 0 || staffRepository
                        .findAllBySpecialist_IdAndActiveTrueOrderByTeacher_FioTeacherAsc(item.getSpecialistId()).size() > 0)
                .toList());
        result.setStaff(staffRepository.findAllByOrderBySpecialist_NameAscTeacher_FioTeacherAsc().stream()
                .map(staff -> staffSummary(staff, academicYear, assignments.values())).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public CorrectionDistributionDtos.Directory directory(String academicYear) {
        CorrectionDistributionDtos.Directory result = new CorrectionDistributionDtos.Directory();
        result.setSpecialists(specialistRepository.findAllByOrderByNameAsc().stream()
                .filter(CorrectionSpecialistCatalogEntry::isActive).map(this::specialistOption).toList());
        LocalDate today = LocalDate.now();
        result.setEmployees(teacherRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .filter(teacher -> teacher.getDismissalDate() == null || !teacher.getDismissalDate().isBefore(today))
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(this::employeeOption).toList());
        Map<NeedKey, Need> needs = needs(academicYear);
        Collection<CorrectionStudentAssignment> assignments = validAssignments(academicYear, needs).values();
        result.setStaff(staffRepository.findAllByOrderBySpecialist_NameAscTeacher_FioTeacherAsc().stream()
                .map(staff -> staffSummary(staff, academicYear, assignments)).toList());
        return result;
    }

    @Transactional
    public CorrectionDistributionDtos.StaffSummary saveStaff(String academicYear,
                                                              CorrectionDistributionDtos.StaffSaveRequest request) {
        if (request == null || request.getSpecialistId() == null || request.getEmployeeId() == null) {
            throw new IllegalArgumentException("Выберите направление и сотрудника");
        }
        CorrectionSpecialistCatalogEntry specialist = specialistRepository.findById(request.getSpecialistId())
                .orElseThrow(() -> new IllegalArgumentException("Направление коррекционной работы не найдено"));
        TeacherDirectoryEntry teacher = teacherRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Кадровая карточка сотрудника не найдена"));
        CorrectionSpecialistStaff staff;
        if (request.getId() == null) {
            staff = staffRepository.findBySpecialist_IdAndTeacher_Id(specialist.getId(), teacher.getId())
                    .orElseGet(CorrectionSpecialistStaff::new);
        } else {
            staff = staffRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Привязка специалиста не найдена"));
            if ((!Objects.equals(staff.getSpecialist().getId(), specialist.getId())
                    || !Objects.equals(staff.getTeacher().getId(), teacher.getId()))
                    && (assignmentRepository.countByAcademicYearAndStaff_Id(academicYear, staff.getId()) > 0
                    || groupRepository.countByAcademicYearAndStaff_Id(academicYear, staff.getId()) > 0)) {
                throw new IllegalArgumentException("Нельзя заменить направление или сотрудника: уже есть группы или закреплённые дети");
            }
        }
        staff.setSpecialist(specialist);
        staff.setTeacher(teacher);
        staff.setActive(request.isActive());
        staff.setUpdatedAt(LocalDateTime.now());
        staff = staffRepository.save(staff);
        return staffSummary(staff, academicYear, validAssignments(academicYear, needs(academicYear)).values());
    }

    @Transactional(readOnly = true)
    public CorrectionDistributionDtos.Schedule schedule(String academicYear, Long staffId) {
        CorrectionSpecialistStaff staff = requireStaff(staffId);
        Map<NeedKey, Need> needs = needs(academicYear);
        Map<NeedKey, CorrectionStudentAssignment> assignments = validAssignments(academicYear, needs);
        Set<Long> signedStudents = signedStudents(academicYear);

        CorrectionDistributionDtos.Schedule result = new CorrectionDistributionDtos.Schedule();
        result.setSelectedStaff(staffSummary(staff, academicYear, assignments.values()));
        result.setGroups(groupRepository.findAllByAcademicYearAndStaff_IdOrderByWeekdayAscStartTimeAsc(academicYear, staffId)
                .stream().map(group -> groupView(group, academicYear, needs)).toList());
        result.setAvailableStudents(needs.values().stream()
                .filter(need -> Objects.equals(need.specialist().getId(), staff.getSpecialist().getId()))
                .filter(need -> signedStudents.contains(need.student().getId()))
                .filter(need -> !assignments.containsKey(need.key()))
                .sorted(Comparator.comparing((Need need) -> currentClass(need.student().getId(), academicYear), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(need -> need.student().getCurrentFullName(), String.CASE_INSENSITIVE_ORDER))
                .map(need -> studentNeedView(need, academicYear, true, null)).toList());
        return result;
    }

    @Transactional
    public CorrectionDistributionDtos.GroupView saveGroup(String academicYear,
                                                            CorrectionDistributionDtos.GroupSaveRequest request) {
        validateGroupRequest(request);
        CorrectionSpecialistStaff staff = requireStaff(request.getStaffId());
        if (!staff.isActive()) throw new IllegalArgumentException("Сотрудник отключён в справочнике специалистов");
        CorrectionScheduleGroup group = request.getId() == null ? new CorrectionScheduleGroup()
                : groupRepository.findById(request.getId())
                .filter(item -> Objects.equals(item.getAcademicYear(), academicYear))
                .orElseThrow(() -> new IllegalArgumentException("Группа не найдена"));
        if (group.getId() != null && !Objects.equals(group.getStaff().getId(), staff.getId())) {
            throw new IllegalArgumentException("Нельзя перенести существующую группу к другому специалисту");
        }
        ensureNoTimeConflict(academicYear, staff.getId(), request.getWeekday(), request.getStartTime(),
                request.getDurationMinutes(), group.getId());
        group.setAcademicYear(academicYear);
        group.setStaff(staff);
        group.setWeekday(request.getWeekday());
        group.setStartTime(request.getStartTime());
        group.setDurationMinutes(request.getDurationMinutes());
        if (group.getId() == null) {
            group.setSequenceNumber(groupRepository.maxSequenceNumber(academicYear, staff.getId()) + 1);
        }
        group.setUpdatedAt(LocalDateTime.now());
        group = groupRepository.save(group);

        Map<NeedKey, Need> needs = needs(academicYear);
        Set<Long> signedStudents = signedStudents(academicYear);
        LinkedHashSet<Long> requestedStudents = new LinkedHashSet<>(Objects.requireNonNullElse(request.getStudentIds(), List.of()));
        List<CorrectionStudentAssignment> current = assignmentRepository
                .findAllByAcademicYearAndGroup_Id(academicYear, group.getId());
        for (CorrectionStudentAssignment assignment : current) {
            if (!requestedStudents.contains(assignment.getStudent().getId())) assignmentRepository.delete(assignment);
        }
        for (Long studentId : requestedStudents) {
            NeedKey key = new NeedKey(studentId, staff.getSpecialist().getId());
            Need need = needs.get(key);
            if (need == null) {
                throw new IllegalArgumentException("У выбранного ребёнка нет потребности в направлении «"
                        + staff.getSpecialist().getName() + "»");
            }
            if (!signedStudents.contains(studentId)) {
                throw new IllegalArgumentException("Распределение доступно только после подписания ППк");
            }
            CorrectionStudentAssignment assignment = assignmentRepository
                    .findByAcademicYearAndStudent_IdAndSpecialist_Id(academicYear, studentId, staff.getSpecialist().getId())
                    .orElseGet(CorrectionStudentAssignment::new);
            if (assignment.getId() != null && !Objects.equals(assignment.getGroup().getId(), group.getId())) {
                throw new IllegalArgumentException(need.student().getCurrentFullName()
                        + " уже закреплён в другой группе этого направления");
            }
            assignment.setAcademicYear(academicYear);
            assignment.setStudent(need.student());
            assignment.setSpecialist(staff.getSpecialist());
            assignment.setStaff(staff);
            assignment.setGroup(group);
            assignment.setUpdatedAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
        }
        assignmentRepository.flush();
        return groupView(group, academicYear, needs);
    }

    @Transactional
    public void deleteGroup(String academicYear, Long groupId) {
        CorrectionScheduleGroup group = groupRepository.findById(groupId)
                .filter(item -> Objects.equals(item.getAcademicYear(), academicYear))
                .orElseThrow(() -> new IllegalArgumentException("Группа не найдена"));
        assignmentRepository.deleteAllByGroup_Id(groupId);
        assignmentRepository.flush();
        groupRepository.delete(group);
    }

    @Transactional
    public void clearStudentAssignments(String academicYear, Long studentId) {
        assignmentRepository.deleteAllByAcademicYearAndStudent_Id(academicYear, studentId);
    }

    @Transactional
    public void reconcileStudentAssignments(String academicYear, Long studentId) {
        Set<NeedKey> valid = needs(academicYear).keySet();
        assignmentRepository.findAllByAcademicYearAndStudent_Id(academicYear, studentId).stream()
                .filter(assignment -> !valid.contains(new NeedKey(
                        assignment.getStudent().getId(), assignment.getSpecialist().getId())))
                .forEach(assignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public CorrectionDistributionDtos.StudentDistribution studentDistribution(String academicYear, Long studentId) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        Map<NeedKey, Need> needs = needs(academicYear);
        Map<NeedKey, CorrectionStudentAssignment> assignments = validAssignments(academicYear, needs);
        boolean signed = signedStudents(academicYear).contains(studentId);
        List<CorrectionDistributionDtos.StudentNeedView> directions = needs.values().stream()
                .filter(need -> Objects.equals(need.student().getId(), studentId))
                .sorted(Comparator.comparing(need -> need.specialist().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(need -> studentNeedView(need, academicYear, signed, assignments.get(need.key()))).toList();
        CorrectionDistributionDtos.StudentDistribution result = new CorrectionDistributionDtos.StudentDistribution();
        result.setStudentId(studentId);
        result.setFullName(student.getCurrentFullName());
        result.setClassName(currentClass(studentId, academicYear));
        result.setPpkSigned(signed);
        result.setNeededCount(directions.size());
        result.setAssignedCount((int) directions.stream().filter(CorrectionDistributionDtos.StudentNeedView::isAssigned).count());
        result.setDirections(directions);
        return result;
    }

    @Transactional(readOnly = true)
    public OvzStageStatus studentStageStatus(String academicYear, Long studentId) {
        return studentStageStatuses(academicYear).getOrDefault(studentId, OvzStageStatus.NOT_RELEASED);
    }

    @Transactional(readOnly = true)
    public Map<Long, OvzStageStatus> studentStageStatuses(String academicYear) {
        Map<NeedKey, Need> needs = needs(academicYear);
        Set<Long> signed = signedStudents(academicYear);
        Map<NeedKey, CorrectionStudentAssignment> assignments = validAssignments(academicYear, needs);
        Map<Long, List<Need>> byStudent = needs.values().stream()
                .collect(Collectors.groupingBy(need -> need.student().getId()));
        Map<Long, OvzStageStatus> result = new HashMap<>();
        byStudent.forEach((studentId, studentNeeds) -> {
            long assigned = studentNeeds.stream().filter(need -> assignments.containsKey(need.key())).count();
            OvzStageStatus status = !signed.contains(studentId) || assigned == 0
                    ? OvzStageStatus.NOT_RELEASED
                    : assigned >= studentNeeds.size() ? OvzStageStatus.COMPLETED : OvzStageStatus.PRINTED;
            result.put(studentId, status);
        });
        return result;
    }

    private Map<NeedKey, Need> needs(String academicYear) {
        Map<NeedKey, Need> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        Set<String> refused = applicationChoiceRepository.findAllByAcademicYear(academicYear).stream()
                .filter(choice -> !choice.isAgreed())
                .map(choice -> choice.getStudent().getId() + "\u0000" + choice.getSpecialistName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        correctionRepository.findAllByDocument_AcademicYearOrderBySpecialist_NameAsc(academicYear).stream()
                .filter(correction -> correction.getDocument().getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION
                        || correction.getDocument().getDocumentType() == StudentSupportDocumentType.CPMPC_RECOMMENDATION)
                .filter(correction -> correction.getDocument().getValidFrom() == null
                        || !correction.getDocument().getValidFrom().isAfter(today))
                .filter(correction -> correction.getDocument().getValidTo() == null
                        || !correction.getDocument().getValidTo().isBefore(today))
                .filter(correction -> !refused.contains(correction.getDocument().getStudent().getId() + "\u0000"
                        + correction.getSpecialist().getName().toLowerCase(Locale.ROOT)))
                .forEach(correction -> {
                    StudentProfile student = correction.getDocument().getStudent();
                    Need need = new Need(student, correction.getSpecialist());
                    result.putIfAbsent(need.key(), need);
                });
        return result;
    }

    private Set<Long> signedStudents(String academicYear) {
        return ppkRepository.findAllByAcademicYearOrderByMeetingDateDescSequenceNumberDesc(academicYear).stream()
                .filter(protocol -> protocol.getStudent() != null)
                .filter(protocol -> protocol.getProtocolType() != PpkProtocolType.IOM)
                .filter(protocol -> protocol.getStatus() == OvzStageStatus.COMPLETED)
                .map(protocol -> protocol.getStudent().getId()).collect(Collectors.toSet());
    }

    private Map<NeedKey, CorrectionStudentAssignment> validAssignments(String academicYear, Map<NeedKey, Need> needs) {
        return assignmentRepository.findAllByAcademicYear(academicYear).stream()
                .filter(assignment -> needs.containsKey(new NeedKey(
                        assignment.getStudent().getId(), assignment.getSpecialist().getId())))
                .collect(Collectors.toMap(assignment -> new NeedKey(assignment.getStudent().getId(),
                                assignment.getSpecialist().getId()), Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private CorrectionDistributionDtos.DirectionSummary directionSummary(
            CorrectionSpecialistCatalogEntry specialist, List<Need> needs,
            Map<NeedKey, CorrectionStudentAssignment> assignments) {
        CorrectionDistributionDtos.DirectionSummary result = new CorrectionDistributionDtos.DirectionSummary();
        result.setSpecialistId(specialist.getId());
        result.setSpecialistName(specialist.getName());
        result.setNeededCount(needs.size());
        result.setAssignedCount((int) needs.stream().filter(need -> assignments.containsKey(need.key())).count());
        result.setUnassignedCount(result.getNeededCount() - result.getAssignedCount());
        return result;
    }

    private CorrectionDistributionDtos.StaffSummary staffSummary(
            CorrectionSpecialistStaff staff, String academicYear,
            Collection<CorrectionStudentAssignment> assignments) {
        CorrectionDistributionDtos.StaffSummary result = new CorrectionDistributionDtos.StaffSummary();
        result.setStaffId(staff.getId());
        result.setSpecialistId(staff.getSpecialist().getId());
        result.setSpecialistName(staff.getSpecialist().getName());
        result.setEmployeeId(staff.getTeacher().getId());
        result.setEmployeeName(staff.getTeacher().getFioTeacher());
        result.setPosition(staff.getTeacher().getPrimaryPosition());
        result.setPersonnelNumber(staff.getTeacher().getPersonnelNumber());
        result.setActive(staff.isActive());
        result.setAssignedCount(assignments.stream().filter(assignment -> Objects.equals(assignment.getStaff().getId(), staff.getId())).count());
        result.setGroupCount(groupRepository.countByAcademicYearAndStaff_Id(academicYear, staff.getId()));
        return result;
    }

    private CorrectionDistributionDtos.GroupView groupView(CorrectionScheduleGroup group, String academicYear,
                                                            Map<NeedKey, Need> needs) {
        CorrectionDistributionDtos.GroupView result = new CorrectionDistributionDtos.GroupView();
        CorrectionSpecialistStaff staff = group.getStaff();
        String displayName = "№" + group.getSequenceNumber();
        result.setId(group.getId());
        result.setStaffId(staff.getId());
        result.setSpecialistId(staff.getSpecialist().getId());
        result.setSpecialistName(staff.getSpecialist().getName());
        result.setEmployeeName(staff.getTeacher().getFioTeacher());
        result.setSequenceNumber(group.getSequenceNumber());
        result.setDisplayName(displayName);
        result.setFullName(staff.getSpecialist().getName() + " - " + surname(staff.getTeacher().getFioTeacher()) + " - " + displayName);
        result.setWeekday(group.getWeekday());
        result.setStartTime(group.getStartTime());
        result.setDurationMinutes(group.getDurationMinutes());
        result.setStudents(assignmentRepository.findAllByAcademicYearAndGroup_Id(academicYear, group.getId()).stream()
                .filter(assignment -> needs.containsKey(new NeedKey(
                        assignment.getStudent().getId(), assignment.getSpecialist().getId())))
                .sorted(Comparator.comparing(assignment -> assignment.getStudent().getCurrentFullName(), String.CASE_INSENSITIVE_ORDER))
                .map(assignment -> studentNeedView(new Need(assignment.getStudent(), assignment.getSpecialist()),
                        academicYear, true, assignment)).toList());
        return result;
    }

    private CorrectionDistributionDtos.StudentNeedView studentNeedView(
            Need need, String academicYear, boolean available, CorrectionStudentAssignment assignment) {
        CorrectionDistributionDtos.StudentNeedView result = new CorrectionDistributionDtos.StudentNeedView();
        result.setStudentId(need.student().getId());
        result.setFullName(need.student().getCurrentFullName());
        result.setClassName(currentClass(need.student().getId(), academicYear));
        result.setSpecialistId(need.specialist().getId());
        result.setSpecialistName(need.specialist().getName());
        result.setDistributionAvailable(available);
        result.setAssigned(assignment != null);
        if (assignment != null) {
            result.setStaffId(assignment.getStaff().getId());
            result.setEmployeeId(assignment.getStaff().getTeacher().getId());
            result.setEmployeeName(assignment.getStaff().getTeacher().getFioTeacher());
            result.setGroupId(assignment.getGroup().getId());
            result.setGroupName("№" + assignment.getGroup().getSequenceNumber());
        }
        return result;
    }

    private CorrectionDistributionDtos.SpecialistOption specialistOption(CorrectionSpecialistCatalogEntry specialist) {
        CorrectionDistributionDtos.SpecialistOption result = new CorrectionDistributionDtos.SpecialistOption();
        result.setId(specialist.getId()); result.setName(specialist.getName()); return result;
    }

    private CorrectionDistributionDtos.EmployeeOption employeeOption(TeacherDirectoryEntry teacher) {
        CorrectionDistributionDtos.EmployeeOption result = new CorrectionDistributionDtos.EmployeeOption();
        result.setId(teacher.getId()); result.setFullName(teacher.getFioTeacher());
        result.setPosition(teacher.getPrimaryPosition()); result.setPersonnelNumber(teacher.getPersonnelNumber()); return result;
    }

    private CorrectionSpecialistStaff requireStaff(Long id) {
        if (id == null) throw new IllegalArgumentException("Выберите специалиста");
        return staffRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Специалист не найден"));
    }

    private void validateGroupRequest(CorrectionDistributionDtos.GroupSaveRequest request) {
        if (request == null || request.getStaffId() == null || request.getWeekday() == null || request.getStartTime() == null
                || request.getDurationMinutes() == null) throw new IllegalArgumentException("Заполните специалиста, день, время и продолжительность");
        if (request.getWeekday() < 1 || request.getWeekday() > 5) throw new IllegalArgumentException("Расписание ведётся с понедельника по пятницу");
        if (request.getDurationMinutes() < 10 || request.getDurationMinutes() > 60 || request.getDurationMinutes() % 5 != 0) {
            throw new IllegalArgumentException("Продолжительность должна быть от 10 до 60 минут с шагом 5 минут");
        }
    }

    private void ensureNoTimeConflict(String academicYear, Long staffId, int weekday, LocalTime start,
                                      int duration, Long ignoredGroupId) {
        LocalTime end = start.plusMinutes(duration);
        boolean conflict = groupRepository.findAllByAcademicYearAndStaff_IdOrderByWeekdayAscStartTimeAsc(academicYear, staffId).stream()
                .filter(group -> !Objects.equals(group.getId(), ignoredGroupId))
                .filter(group -> group.getWeekday() == weekday)
                .anyMatch(group -> start.isBefore(group.getStartTime().plusMinutes(group.getDurationMinutes()))
                        && group.getStartTime().isBefore(end));
        if (conflict) throw new IllegalArgumentException("У специалиста уже есть группа в это время");
    }

    private String currentClass(Long studentId, String academicYear) {
        return enrollmentRepository.findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(studentId, academicYear)
                .map(StudentClassEnrollment::getClassName)
                .orElseGet(() -> enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                        .stream().map(StudentClassEnrollment::getClassName).findFirst().orElse(""));
    }

    private String surname(String fullName) {
        String value = Objects.toString(fullName, "").trim();
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private record NeedKey(Long studentId, Long specialistId) {}
    private record Need(StudentProfile student, CorrectionSpecialistCatalogEntry specialist) {
        NeedKey key() { return new NeedKey(student.getId(), specialist.getId()); }
    }
}
