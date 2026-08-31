package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.OvzSpecialistWorkspaceDtos;
import org.school.personalLoad.dto.contingent.OvzSpecialistWorkspaceDtos.CompletionStatus;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OvzSpecialistWorkspaceService {

    private static final int MAX_TEXT_LENGTH = 10_000;

    private final CorrectionStudentAssignmentRepository assignmentRepository;
    private final OvzSpecialistSupportEntryRepository entryRepository;
    private final OvzSpecialistWorkspaceSettingsRepository settingsRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public OvzSpecialistWorkspaceDtos.Overview overview(String academicYear, SessionUser session) {
        Access access = access(session);
        List<CorrectionStudentAssignment> allAssignments = assignmentRepository.findAllByAcademicYear(academicYear);
        Set<Long> accessibleStudents = accessibleStudentIds(allAssignments, access);
        Map<EntryKey, OvzSpecialistSupportEntry> entries = entryRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.toMap(this::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Map<Long, List<CorrectionStudentAssignment>> byStudent = allAssignments.stream()
                .filter(assignment -> accessibleStudents.contains(assignment.getStudent().getId()))
                .collect(Collectors.groupingBy(assignment -> assignment.getStudent().getId(), LinkedHashMap::new, Collectors.toList()));

        List<OvzSpecialistWorkspaceDtos.ChildSummary> children = byStudent.values().stream()
                .map(assignments -> childSummary(academicYear, assignments, entries, access))
                .sorted(Comparator.comparing(OvzSpecialistWorkspaceDtos.ChildSummary::getClassName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(OvzSpecialistWorkspaceDtos.ChildSummary::getFullName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        OvzSpecialistWorkspaceDtos.Overview result = new OvzSpecialistWorkspaceDtos.Overview();
        result.setCurrentUserName(session.getFullName());
        result.setCurrentTeacherId(access.teacherId());
        result.setResponsible(access.responsible());
        result.setCanManageSettings(access.canManageSettings());
        result.setResponsibleEmployeeId(access.responsibleTeacherId());
        result.setResponsibleEmployeeName(access.responsibleTeacherName());
        result.setChildCount(children.size());
        result.setCompletedCount((int) children.stream()
                .filter(child -> child.getCurrentUserStatus() == CompletionStatus.COMPLETED).count());
        result.setIncompleteCount(result.getChildCount() - result.getCompletedCount());
        result.setChildren(children);
        return result;
    }

    @Transactional(readOnly = true)
    public OvzSpecialistWorkspaceDtos.ChildDetail child(String academicYear, Long studentId, SessionUser session) {
        Access access = access(session);
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        List<CorrectionStudentAssignment> assignments = assignmentRepository
                .findAllByAcademicYearAndStudent_Id(academicYear, studentId);
        ensureStudentAccess(assignments, access);
        Map<Long, OvzSpecialistSupportEntry> entries = entryRepository
                .findAllByAcademicYearAndStudent_Id(academicYear, studentId).stream()
                .collect(Collectors.toMap(entry -> entry.getSpecialist().getId(), Function.identity(), (left, right) -> left));

        OvzSpecialistWorkspaceDtos.ChildDetail result = new OvzSpecialistWorkspaceDtos.ChildDetail();
        result.setStudentId(student.getId());
        result.setFullName(student.getCurrentFullName());
        result.setClassName(currentClass(studentId, academicYear));
        result.setResponsible(access.responsible());
        result.setEntries(assignments.stream()
                .sorted(assignmentComparator())
                .map(assignment -> entryView(assignment, entries.get(assignment.getSpecialist().getId()), access))
                .toList());
        return result;
    }

    @Transactional
    public OvzSpecialistWorkspaceDtos.SupportEntry saveEntry(String academicYear,
                                                              Long studentId,
                                                              OvzSpecialistWorkspaceDtos.SupportEntryRequest request,
                                                              SessionUser session) {
        if (request == null || request.getSpecialistId() == null) {
            throw new IllegalArgumentException("Не указано направление работы специалиста");
        }
        Access access = access(session);
        CorrectionStudentAssignment assignment = assignmentRepository
                .findByAcademicYearAndStudent_IdAndSpecialist_Id(academicYear, studentId, request.getSpecialistId())
                .orElseThrow(() -> new IllegalArgumentException("Ребёнок не закреплён за специалистом этого направления"));
        if (!editable(assignment, access)) {
            throw new ForbiddenException("Можно заполнять только своё направление сопровождения");
        }

        OvzSpecialistSupportEntry entry = entryRepository
                .findByAcademicYearAndStudent_IdAndSpecialist_Id(academicYear, studentId, request.getSpecialistId())
                .orElseGet(OvzSpecialistSupportEntry::new);
        entry.setAcademicYear(academicYear);
        entry.setStudent(assignment.getStudent());
        entry.setSpecialist(assignment.getSpecialist());
        entry.setChildDeficits(text(request.getChildDeficits(), "Основные дефициты ребёнка"));
        entry.setChildResources(text(request.getChildResources(), "Ресурсы ребёнка"));
        entry.setAnnualTasks(text(request.getAnnualTasks(), "Основные задачи развития на год"));
        entry.setPlannedResults(text(request.getPlannedResults(), "Планируемые результаты"));
        entry.setUpdatedByUserId(session.getId());
        entry.setUpdatedByName(session.getFullName());
        entry.setUpdatedAt(LocalDateTime.now());
        entry = entryRepository.save(entry);
        return entryView(assignment, entry, access);
    }

    @Transactional(readOnly = true)
    public OvzSpecialistWorkspaceDtos.SettingsView settings(SessionUser session) {
        Access access = access(session);
        OvzSpecialistWorkspaceDtos.SettingsView result = new OvzSpecialistWorkspaceDtos.SettingsView();
        result.setResponsibleEmployeeId(access.responsibleTeacherId());
        result.setResponsibleEmployeeName(access.responsibleTeacherName());
        result.setCanManage(access.canManageSettings());
        LocalDate today = LocalDate.now();
        result.setEmployees(teacherRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .filter(teacher -> teacher.getDismissalDate() == null || !teacher.getDismissalDate().isBefore(today))
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(this::employeeOption)
                .toList());
        return result;
    }

    @Transactional
    public OvzSpecialistWorkspaceDtos.SettingsView updateSettings(
            OvzSpecialistWorkspaceDtos.SettingsRequest request, SessionUser session) {
        Access access = access(session);
        if (!access.canManageSettings()) {
            throw new ForbiddenException("Назначать ответственного может пользователь с правом редактирования раздела ОВЗ");
        }
        Long teacherId = request == null ? null : request.getResponsibleEmployeeId();
        TeacherDirectoryEntry teacher = teacherId == null ? null : teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Кадровая карточка ответственного не найдена"));
        OvzSpecialistWorkspaceSettings settings = settingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(OvzSpecialistWorkspaceSettings::new);
        settings.setResponsibleTeacher(teacher);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);
        return settings(session);
    }

    private OvzSpecialistWorkspaceDtos.ChildSummary childSummary(
            String academicYear,
            List<CorrectionStudentAssignment> assignments,
            Map<EntryKey, OvzSpecialistSupportEntry> entries,
            Access access) {
        CorrectionStudentAssignment first = assignments.get(0);
        List<OvzSpecialistWorkspaceDtos.SpecialistStatus> statuses = assignments.stream()
                .sorted(assignmentComparator())
                .map(assignment -> specialistStatus(assignment,
                        entries.get(new EntryKey(assignment.getStudent().getId(), assignment.getSpecialist().getId())), access))
                .toList();
        List<OvzSpecialistWorkspaceDtos.SpecialistStatus> ownStatuses = access.responsible()
                ? statuses
                : statuses.stream().filter(OvzSpecialistWorkspaceDtos.SpecialistStatus::isEditable).toList();

        OvzSpecialistWorkspaceDtos.ChildSummary result = new OvzSpecialistWorkspaceDtos.ChildSummary();
        result.setStudentId(first.getStudent().getId());
        result.setFullName(first.getStudent().getCurrentFullName());
        result.setClassName(currentClass(first.getStudent().getId(), academicYear));
        result.setOverallStatus(aggregate(statuses.stream().map(OvzSpecialistWorkspaceDtos.SpecialistStatus::getStatus).toList()));
        result.setCurrentUserStatus(aggregate(ownStatuses.stream().map(OvzSpecialistWorkspaceDtos.SpecialistStatus::getStatus).toList()));
        result.setSpecialists(statuses);
        return result;
    }

    private OvzSpecialistWorkspaceDtos.SupportEntry entryView(CorrectionStudentAssignment assignment,
                                                               OvzSpecialistSupportEntry entry,
                                                               Access access) {
        OvzSpecialistWorkspaceDtos.SupportEntry result = new OvzSpecialistWorkspaceDtos.SupportEntry();
        fillStatus(result, assignment, entry, access);
        if (entry != null) {
            result.setChildDeficits(entry.getChildDeficits());
            result.setChildResources(entry.getChildResources());
            result.setAnnualTasks(entry.getAnnualTasks());
            result.setPlannedResults(entry.getPlannedResults());
            result.setUpdatedByUserId(entry.getUpdatedByUserId());
            result.setUpdatedByName(entry.getUpdatedByName());
            result.setUpdatedAt(entry.getUpdatedAt());
        }
        return result;
    }

    private OvzSpecialistWorkspaceDtos.SpecialistStatus specialistStatus(
            CorrectionStudentAssignment assignment, OvzSpecialistSupportEntry entry, Access access) {
        OvzSpecialistWorkspaceDtos.SpecialistStatus result = new OvzSpecialistWorkspaceDtos.SpecialistStatus();
        fillStatus(result, assignment, entry, access);
        return result;
    }

    private void fillStatus(OvzSpecialistWorkspaceDtos.SpecialistStatus result,
                            CorrectionStudentAssignment assignment,
                            OvzSpecialistSupportEntry entry,
                            Access access) {
        result.setSpecialistId(assignment.getSpecialist().getId());
        result.setSpecialistName(assignment.getSpecialist().getName());
        result.setStaffId(assignment.getStaff().getId());
        result.setEmployeeId(assignment.getStaff().getTeacher().getId());
        result.setEmployeeName(assignment.getStaff().getTeacher().getFioTeacher());
        result.setStatus(status(entry));
        result.setEditable(editable(assignment, access));
    }

    private boolean editable(CorrectionStudentAssignment assignment, Access access) {
        return access.responsible() || Objects.equals(access.teacherId(), assignment.getStaff().getTeacher().getId());
    }

    private Set<Long> accessibleStudentIds(List<CorrectionStudentAssignment> assignments, Access access) {
        if (access.responsible()) {
            return assignments.stream().map(assignment -> assignment.getStudent().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (access.teacherId() == null) return Set.of();
        return assignments.stream()
                .filter(assignment -> Objects.equals(access.teacherId(), assignment.getStaff().getTeacher().getId()))
                .map(assignment -> assignment.getStudent().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void ensureStudentAccess(List<CorrectionStudentAssignment> assignments, Access access) {
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Ребёнок ещё не распределён за специалистами");
        }
        if (access.responsible()) return;
        boolean assigned = assignments.stream()
                .anyMatch(assignment -> Objects.equals(access.teacherId(), assignment.getStaff().getTeacher().getId()));
        if (!assigned) throw new ForbiddenException("Ребёнок не закреплён за текущим специалистом");
    }

    private Access access(SessionUser session) {
        if (session == null) throw new ForbiddenException("Требуется вход в систему");
        if (!session.canViewTab(AppTab.OVZ)) {
            throw new ForbiddenException("Нет доступа к разделу ОВЗ");
        }
        AppUser appUser = appUserRepository.findById(session.getId())
                .orElseThrow(() -> new ForbiddenException("Учётная запись не найдена"));
        OvzSpecialistWorkspaceSettings settings = settingsRepository.findFirstByOrderByIdAsc().orElse(null);
        TeacherDirectoryEntry responsible = settings == null ? null : settings.getResponsibleTeacher();
        Long responsibleId = responsible == null ? null : responsible.getId();
        Long teacherId = appUser.getTeacherId();
        boolean responsibleAccess = session.isAdmin() || (teacherId != null && Objects.equals(teacherId, responsibleId));
        boolean canManage = session.isAdmin() || session.canEditTab(AppTab.OVZ);
        return new Access(teacherId, responsibleAccess, canManage, responsibleId,
                responsible == null ? null : responsible.getFioTeacher());
    }

    private CompletionStatus status(OvzSpecialistSupportEntry entry) {
        if (entry == null) return CompletionStatus.NOT_STARTED;
        int completed = 0;
        if (hasText(entry.getChildDeficits())) completed++;
        if (hasText(entry.getChildResources())) completed++;
        if (hasText(entry.getAnnualTasks())) completed++;
        if (hasText(entry.getPlannedResults())) completed++;
        if (completed == 0) return CompletionStatus.NOT_STARTED;
        return completed == 4 ? CompletionStatus.COMPLETED : CompletionStatus.IN_PROGRESS;
    }

    private CompletionStatus aggregate(List<CompletionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return CompletionStatus.NOT_STARTED;
        if (statuses.stream().allMatch(status -> status == CompletionStatus.COMPLETED)) return CompletionStatus.COMPLETED;
        if (statuses.stream().allMatch(status -> status == CompletionStatus.NOT_STARTED)) return CompletionStatus.NOT_STARTED;
        return CompletionStatus.IN_PROGRESS;
    }

    private String currentClass(Long studentId, String academicYear) {
        return enrollmentRepository
                .findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(studentId, academicYear)
                .map(StudentClassEnrollment::getClassName)
                .orElseGet(() -> enrollmentRepository
                        .findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear).stream()
                        .findFirst().map(StudentClassEnrollment::getClassName).orElse("—"));
    }

    private Comparator<CorrectionStudentAssignment> assignmentComparator() {
        return Comparator.comparing((CorrectionStudentAssignment assignment) -> assignment.getSpecialist().getName(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(assignment -> assignment.getStaff().getTeacher().getFioTeacher(),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private OvzSpecialistWorkspaceDtos.EmployeeOption employeeOption(TeacherDirectoryEntry teacher) {
        OvzSpecialistWorkspaceDtos.EmployeeOption result = new OvzSpecialistWorkspaceDtos.EmployeeOption();
        result.setId(teacher.getId());
        result.setFullName(teacher.getFioTeacher());
        result.setPosition(teacher.getPrimaryPosition());
        result.setPersonnelNumber(teacher.getPersonnelNumber());
        return result;
    }

    private String text(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + ": допускается не более " + MAX_TEXT_LENGTH + " символов");
        }
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private EntryKey key(OvzSpecialistSupportEntry entry) {
        return new EntryKey(entry.getStudent().getId(), entry.getSpecialist().getId());
    }

    private record EntryKey(Long studentId, Long specialistId) {}

    private record Access(Long teacherId,
                          boolean responsible,
                          boolean canManageSettings,
                          Long responsibleTeacherId,
                          String responsibleTeacherName) {}
}
