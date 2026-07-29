package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.contingent.StudentSupportDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.IupDeliveryForm;
import org.school.personalLoad.model.IupParticipationMode;
import org.school.personalLoad.model.IupPlan;
import org.school.personalLoad.model.IupStatus;
import org.school.personalLoad.model.IupSubjectLine;
import org.school.personalLoad.model.IupTeacherAssignment;
import org.school.personalLoad.model.NosologyCatalogEntry;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentGroupMembership;
import org.school.personalLoad.model.StudentGroupMembershipSource;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.IupPlanRepository;
import org.school.personalLoad.repository.IupSubjectLineRepository;
import org.school.personalLoad.repository.IupTeacherAssignmentRepository;
import org.school.personalLoad.repository.NosologyCatalogEntryRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentGroupMembershipRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.IupLoadService;
import org.school.personalLoad.service.StudentSupportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentSupportServiceImpl implements StudentSupportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final IupPlanRepository iupPlanRepository;
    private final IupSubjectLineRepository iupSubjectLineRepository;
    private final IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    private final StudentGroupMembershipRepository groupMembershipRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final NosologyCatalogEntryRepository nosologyCatalogEntryRepository;
    private final IupLoadService iupLoadService;

    @Override
    @Transactional(readOnly = true)
    public StudentSupportDtos.SummaryResponse getSummary(String academicYear,
                                                         LocalDate snapshotDate,
                                                         LocalDate asOfDate) {
        ContingentSnapshot snapshot = resolveSnapshot(academicYear, snapshotDate);
        LocalDate effectiveDate = asOfDate == null ? snapshot.getSnapshotDate() : asOfDate;
        List<ContingentStudent> sourceRows = contingentStudentRepository.findAllBySnapshotId(snapshot.getId());
        Map<Long, StudentProfile> profiles = studentProfileRepository.findAllById(
                        sourceRows.stream()
                                .map(ContingentStudent::getStudentId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));

        Map<Long, StudentSupportStatus> activeStatusByStudent = activeStatuses(academicYear, effectiveDate);
        Map<Long, IupPlan> activeIupByStudent = activeIups(academicYear, effectiveDate);
        Map<String, MutableClassSummary> classTotals = new HashMap<>();
        Map<Long, ContingentStudent> currentRowByStudent = new LinkedHashMap<>();
        int unlinked = 0;

        for (ContingentStudent row : sourceRows) {
            String className = displayClassName(row.getClassName());
            MutableClassSummary totals = classTotals.computeIfAbsent(className, MutableClassSummary::new);
            Long studentId = row.getStudentId();
            if (studentId == null || !profiles.containsKey(studentId)) {
                totals.normal++;
                unlinked++;
                continue;
            }
            currentRowByStudent.putIfAbsent(studentId, row);
            if (activeIupByStudent.containsKey(studentId)) {
                totals.iup++;
                continue;
            }
            StudentCategory category = categoryOf(activeStatusByStudent.get(studentId));
            switch (category) {
                case K2 -> totals.k2++;
                case K3 -> totals.k3++;
                default -> totals.normal++;
            }
        }

        List<StudentSupportDtos.ClassSummary> classes = classTotals.values().stream()
                .sorted(Comparator.comparing(MutableClassSummary::className, this::compareClassNames))
                .map(this::toClassSummary)
                .toList();

        List<StudentSupportDtos.RegisterRow> register = currentRowByStudent.entrySet().stream()
                .map(entry -> toRegisterRow(
                        profiles.get(entry.getKey()),
                        entry.getValue().getClassName(),
                        activeStatusByStudent.get(entry.getKey()),
                        activeIupByStudent.get(entry.getKey())
                ))
                .filter(row -> row.getUnderlyingCategory() != StudentCategory.NORMAL || Boolean.TRUE.equals(row.getHasIup()))
                .sorted(Comparator.comparing(StudentSupportDtos.RegisterRow::getClassName, this::compareClassNames)
                        .thenComparing(StudentSupportDtos.RegisterRow::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        StudentSupportDtos.SummaryResponse response = new StudentSupportDtos.SummaryResponse();
        response.setSnapshotId(snapshot.getId());
        response.setSnapshotDate(snapshot.getSnapshotDate());
        response.setAsOfDate(effectiveDate);
        response.setTotalStudents(classes.stream().mapToInt(StudentSupportDtos.ClassSummary::getTotal).sum());
        response.setUnlinkedStudents(unlinked);
        response.setClasses(classes);
        response.setRegisterRows(register);
        List<String> warnings = new ArrayList<>();
        if (unlinked > 0) {
            warnings.add("Не удалось однозначно связать с постоянной карточкой: " + unlinked
                    + ". До сопоставления эти строки учтены как «Норма».");
        }
        response.setWarnings(warnings);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSupportDtos.ReferenceDataResponse getReferenceData(String academicYear) {
        ContingentSnapshot snapshot = resolveSnapshot(academicYear, null);
        List<ContingentStudent> rows = contingentStudentRepository.findAllBySnapshotId(snapshot.getId());
        Map<Long, ContingentStudent> rowByStudent = rows.stream()
                .filter(row -> row.getStudentId() != null)
                .collect(Collectors.toMap(
                        ContingentStudent::getStudentId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, StudentProfile> profiles = studentProfileRepository.findAllById(rowByStudent.keySet()).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));

        List<StudentSupportDtos.StudentOption> students = rowByStudent.entrySet().stream()
                .map(entry -> toStudentOption(profiles.get(entry.getKey()), entry.getValue()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StudentSupportDtos.StudentOption::getClassName, this::compareClassNames)
                        .thenComparing(StudentSupportDtos.StudentOption::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<StudentSupportDtos.CurriculumOption> curriculum = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear)
                .stream()
                .filter(entry -> !entry.isDeprecated())
                .map(this::toCurriculumOption)
                .sorted(Comparator.comparing(StudentSupportDtos.CurriculumOption::getClassName, this::compareClassNames)
                        .thenComparing(StudentSupportDtos.CurriculumOption::getSubjectName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<StudentSupportDtos.TeacherOption> teachers = teacherDirectoryRepository.findAll().stream()
                .map(this::toTeacherOption)
                .sorted(Comparator.comparing(StudentSupportDtos.TeacherOption::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        StudentSupportDtos.ReferenceDataResponse response = new StudentSupportDtos.ReferenceDataResponse();
        response.setStudents(students);
        response.setCurriculum(curriculum);
        response.setTeachers(teachers);
        return response;
    }

    @Override
    @Transactional
    public StudentSupportDtos.RegisterRow saveStatus(String academicYear,
                                                     StudentSupportDtos.StatusSaveRequest request) {
        requireRequest(request, "Статус ребёнка не передан");
        StudentProfile student = requireStudent(request.getStudentId());
        NosologyCatalogEntry nosology = resolveNosology(request.getNosologyId(), request.getNosologyCode());
        StudentCategory category = nosology == null
                ? Objects.requireNonNullElse(request.getCategory(), StudentCategory.NORMAL)
                : nosology.getStudentCategory();
        validateDates(request.getValidFrom(), request.getValidTo(), "статуса");

        List<StudentSupportStatus> existing = supportStatusRepository
                .findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(student.getId(), academicYear);
        boolean overlaps = existing.stream()
                .filter(status -> !Objects.equals(status.getId(), request.getId()))
                .anyMatch(status -> overlaps(
                        request.getValidFrom(),
                        request.getValidTo(),
                        status.getValidFrom(),
                        status.getValidTo()
                ));
        if (overlaps) {
            throw new IllegalStateException("У ребёнка уже есть статус, действующий в выбранный период");
        }

        StudentSupportStatus status;
        if (request.getId() == null) {
            status = new StudentSupportStatus();
            status.setStudent(student);
            status.setAcademicYear(academicYear);
        } else {
            status = supportStatusRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + request.getId()));
            validateOwner(status.getStudent().getId(), student.getId(), status.getAcademicYear(), academicYear);
        }
        status.setCategory(category);
        status.setNosology(nosology);
        status.setNosologyCodeSnapshot(nosology == null ? null : nosology.getCode());
        status.setAoopVariantSnapshot(nosology == null ? null : nosology.getAoopVariant());
        status.setValidFrom(request.getValidFrom());
        status.setValidTo(request.getValidTo());
        status.setComment(trimToNull(request.getComment()));
        status.setUpdatedAt(LocalDateTime.now());
        status = supportStatusRepository.save(status);
        iupLoadService.refreshStudentCategory(student.getId(), academicYear);

        String className = currentClassName(student.getId(), academicYear);
        return toRegisterRow(student, className, status, null);
    }

    @Override
    @Transactional
    public StudentSupportDtos.IupPlanView saveIup(String academicYear,
                                                  StudentSupportDtos.IupSaveRequest request) {
        requireRequest(request, "ИУП не передан");
        StudentProfile student = requireStudent(request.getStudentId());
        IupStatus requestedStatus = Objects.requireNonNullElse(request.getStatus(), IupStatus.DRAFT);
        validateDates(request.getValidFrom(), request.getValidTo(), "ИУП");
        validatePlanHeader(requestedStatus, request);

        List<IupPlan> plans = iupPlanRepository
                .findAllByStudent_IdAndAcademicYearOrderByVersionNumberDesc(student.getId(), academicYear);
        if (requestedStatus.affectsHeadcount()) {
            boolean overlaps = plans.stream()
                    .filter(plan -> !Objects.equals(plan.getId(), request.getId()))
                    .filter(plan -> plan.getStatus().affectsHeadcount())
                    .anyMatch(plan -> overlaps(
                            request.getValidFrom(),
                            request.getValidTo(),
                            plan.getValidFrom(),
                            plan.getValidTo()
                    ));
            if (overlaps) {
                throw new IllegalStateException("У ребёнка уже есть утверждённый или действующий ИУП в выбранный период");
            }
        }

        IupPlan plan;
        if (request.getId() == null) {
            plan = new IupPlan();
            plan.setStudent(student);
            plan.setAcademicYear(academicYear);
            plan.setVersionNumber(1);
        } else {
            plan = iupPlanRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("ИУП не найден: " + request.getId()));
            validateOwner(plan.getStudent().getId(), student.getId(), plan.getAcademicYear(), academicYear);
            iupLoadService.removeForPlan(plan.getId());
            removePlanLines(plan.getId());
        }
        plan.setStatus(requestedStatus);
        plan.setOrderNumber(trimToNull(request.getOrderNumber()));
        plan.setOrderDate(request.getOrderDate());
        plan.setValidFrom(request.getValidFrom());
        plan.setValidTo(request.getValidTo());
        plan.setComment(trimToNull(request.getComment()));
        plan.setUpdatedAt(LocalDateTime.now());
        plan = iupPlanRepository.save(plan);

        List<StudentSupportDtos.SubjectLineRequest> subjectRequests =
                request.getSubjects() == null ? List.of() : request.getSubjects();
        for (StudentSupportDtos.SubjectLineRequest subjectRequest : subjectRequests) {
            saveSubjectLine(academicYear, plan, student, subjectRequest, requestedStatus);
        }
        iupLoadService.synchronize(plan.getId());
        return getIup(academicYear, plan.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSupportDtos.IupPlanView getIup(String academicYear, Long iupPlanId) {
        IupPlan plan = iupPlanRepository.findById(iupPlanId)
                .orElseThrow(() -> new IllegalArgumentException("ИУП не найден: " + iupPlanId));
        if (!Objects.equals(plan.getAcademicYear(), academicYear)) {
            throw new IllegalArgumentException("ИУП относится к другому учебному году");
        }
        List<IupSubjectLine> lines = iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(plan.getId());
        Map<Long, List<IupTeacherAssignment>> assignmentsByLine =
                iupTeacherAssignmentRepository.findAllBySubjectLine_IupPlan_Id(plan.getId()).stream()
                        .collect(Collectors.groupingBy(assignment -> assignment.getSubjectLine().getId()));
        return toIupPlanView(plan, lines, assignmentsByLine);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportSummary(String academicYear, LocalDate snapshotDate, LocalDate asOfDate) {
        StudentSupportDtos.SummaryResponse summary = getSummary(academicYear, snapshotDate, asOfDate);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            writeClassSummarySheet(workbook, summary, header);
            writeRegisterSheet(workbook, summary, header);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать Excel-файл: " + exception.getMessage(), exception);
        }
    }

    private void saveSubjectLine(String academicYear,
                                 IupPlan plan,
                                 StudentProfile student,
                                 StudentSupportDtos.SubjectLineRequest request,
                                 IupStatus planStatus) {
        if (request == null) {
            throw new IllegalArgumentException("В ИУП обнаружена пустая строка предмета");
        }
        IupParticipationMode mode = Objects.requireNonNullElse(
                request.getParticipationMode(),
                IupParticipationMode.INDIVIDUAL
        );
        BigDecimal classHours = nonNegative(request.getClassHours(), "Часы с классом");
        BigDecimal individualHours = nonNegative(request.getIndividualHours(), "Индивидуальные часы");
        validateParticipationHours(mode, classHours, individualHours);

        CurriculumPlanEntry curriculumEntry = null;
        if (request.getCurriculumEntryId() != null) {
            curriculumEntry = curriculumPlanEntryRepository.findById(request.getCurriculumEntryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Строка учебного плана не найдена: " + request.getCurriculumEntryId()
                    ));
            if (!Objects.equals(curriculumEntry.getAcademicYear(), academicYear)) {
                throw new IllegalArgumentException("Предмет ИУП относится к другому учебному году");
            }
        }
        String subjectName = curriculumEntry == null
                ? trimToNull(request.getSubjectName())
                : curriculumEntry.getSubjectName();
        if (subjectName == null) {
            throw new IllegalArgumentException("Укажите предмет ИУП");
        }

        String groupName = validateAndNormalizeGroup(curriculumEntry, mode, request.getGroupNameEducationalPlan());
        List<StudentSupportDtos.TeacherAssignmentRequest> assignments =
                request.getTeachers() == null ? List.of() : request.getTeachers();
        validateTeacherHours(planStatus, individualHours, assignments);

        IupSubjectLine line = new IupSubjectLine();
        line.setIupPlan(plan);
        line.setSubjectName(subjectName);
        line.setCurriculumEntryId(curriculumEntry == null ? null : curriculumEntry.getId());
        line.setParticipationMode(mode);
        line.setClassHours(classHours);
        line.setIndividualHours(individualHours);
        line.setGroupNameEducationalPlan(groupName);
        line.setUpdatedAt(LocalDateTime.now());
        line = iupSubjectLineRepository.save(line);

        for (StudentSupportDtos.TeacherAssignmentRequest assignmentRequest : assignments) {
            saveTeacherAssignment(plan, line, assignmentRequest);
        }
        if (curriculumEntry != null && groupName != null) {
            StudentGroupMembership membership = new StudentGroupMembership();
            membership.setStudent(student);
            membership.setAcademicYear(academicYear);
            membership.setCurriculumEntryId(curriculumEntry.getId());
            membership.setMetaGroupId(curriculumEntry.getMetaGroupId());
            membership.setGroupNameEducationalPlan(groupName);
            membership.setValidFrom(plan.getValidFrom());
            membership.setValidTo(plan.getValidTo());
            membership.setSource(StudentGroupMembershipSource.IUP_ORDER);
            membership.setIupSubjectLineId(line.getId());
            groupMembershipRepository.save(membership);
        }
    }

    private void saveTeacherAssignment(IupPlan plan,
                                       IupSubjectLine line,
                                       StudentSupportDtos.TeacherAssignmentRequest request) {
        if (request == null || request.getTeacherId() == null) {
            throw new IllegalArgumentException("Для часов ИУП выберите учителя");
        }
        TeacherDirectoryEntry teacher = teacherDirectoryRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("Учитель не найден: " + request.getTeacherId()));
        BigDecimal hours = positive(request.getHoursPerWeek(), "Часы учителя по ИУП");
        LocalDate validFrom = request.getValidFrom() == null ? plan.getValidFrom() : request.getValidFrom();
        LocalDate validTo = request.getValidTo() == null ? plan.getValidTo() : request.getValidTo();
        validateDates(validFrom, validTo, "назначения учителя");
        if (validFrom.isBefore(plan.getValidFrom())
                || (plan.getValidTo() != null && (validTo == null || validTo.isAfter(plan.getValidTo())))) {
            throw new IllegalArgumentException("Период назначения учителя должен находиться внутри периода ИУП");
        }

        IupTeacherAssignment assignment = new IupTeacherAssignment();
        assignment.setSubjectLine(line);
        assignment.setTeacher(teacher);
        assignment.setTeacherFioSnapshot(teacher.getFioTeacher());
        assignment.setHoursPerWeek(hours);
        assignment.setDeliveryForm(Objects.requireNonNullElse(
                request.getDeliveryForm(),
                IupDeliveryForm.FACE_TO_FACE
        ));
        assignment.setValidFrom(validFrom);
        assignment.setValidTo(validTo);
        iupTeacherAssignmentRepository.save(assignment);
    }

    private void validatePlanHeader(IupStatus status, StudentSupportDtos.IupSaveRequest request) {
        List<StudentSupportDtos.SubjectLineRequest> subjects =
                request.getSubjects() == null ? List.of() : request.getSubjects();
        if (status.affectsHeadcount()) {
            if (trimToNull(request.getOrderNumber()) == null || request.getOrderDate() == null) {
                throw new IllegalArgumentException("Для утверждённого или действующего ИУП укажите номер и дату приказа");
            }
            if (subjects.isEmpty()) {
                throw new IllegalArgumentException("В утверждённом или действующем ИУП должен быть хотя бы один предмет");
            }
        }
    }

    private void validateParticipationHours(IupParticipationMode mode,
                                            BigDecimal classHours,
                                            BigDecimal individualHours) {
        switch (mode) {
            case WITH_CLASS -> {
                requirePositive(classHours, "Для занятий с классом укажите часы с классом");
                requireZero(individualHours, "Для режима «с классом» индивидуальные часы должны быть равны нулю");
            }
            case INDIVIDUAL -> {
                requirePositive(individualHours, "Для индивидуальных занятий укажите индивидуальные часы");
                requireZero(classHours, "Для индивидуального режима часы с классом должны быть равны нулю");
            }
            case PARTIAL -> {
                requirePositive(classHours, "Для частичного посещения укажите часы с классом");
                requirePositive(individualHours, "Для частичного посещения укажите индивидуальные часы");
            }
            case NOT_STUDIED -> {
                requireZero(classHours, "Для не изучаемого предмета часы с классом должны быть равны нулю");
                requireZero(individualHours, "Для не изучаемого предмета индивидуальные часы должны быть равны нулю");
            }
        }
    }

    private String validateAndNormalizeGroup(CurriculumPlanEntry entry,
                                             IupParticipationMode mode,
                                             String rawGroupName) {
        boolean attendsClass = mode == IupParticipationMode.WITH_CLASS || mode == IupParticipationMode.PARTIAL;
        if (entry == null || !entry.isSubgroupRequired() || !attendsClass) {
            return null;
        }
        String value = trimToNull(rawGroupName);
        if (value == null) {
            throw new IllegalArgumentException("Предмет делится на группы — выберите группу ребёнка в приказе ИУП");
        }
        int groupNumber;
        try {
            String digits = value.replaceAll("[^0-9]", "");
            groupNumber = Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректное название группы: " + value);
        }
        int subgroupCount = entry.getSubgroupCount() == null || entry.getSubgroupCount() < 1
                ? 2
                : entry.getSubgroupCount();
        if (groupNumber < 1 || groupNumber > subgroupCount) {
            throw new IllegalArgumentException("Для предмета доступны группы с 1 по " + subgroupCount);
        }
        return "Группа " + groupNumber;
    }

    private void validateTeacherHours(IupStatus planStatus,
                                      BigDecimal individualHours,
                                      List<StudentSupportDtos.TeacherAssignmentRequest> assignments) {
        if (individualHours.signum() == 0 && !assignments.isEmpty()) {
            throw new IllegalArgumentException("Назначения учителей относятся только к индивидуальным часам ИУП");
        }
        BigDecimal assigned = assignments.stream()
                .map(StudentSupportDtos.TeacherAssignmentRequest::getHoursPerWeek)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        if (planStatus.affectsHeadcount() && individualHours.signum() > 0) {
            if (assignments.isEmpty()) {
                throw new IllegalArgumentException("Для индивидуальных часов утверждённого ИУП назначьте учителя");
            }
            if (assigned.compareTo(individualHours) != 0) {
                throw new IllegalArgumentException("Сумма часов назначенных учителей должна совпадать с индивидуальными часами предмета");
            }
        }
    }

    private void removePlanLines(Long planId) {
        List<Long> lineIds = iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(planId).stream()
                .map(IupSubjectLine::getId)
                .toList();
        if (!lineIds.isEmpty()) {
            groupMembershipRepository.deleteAllByIupSubjectLineIdIn(lineIds);
        }
        iupTeacherAssignmentRepository.deleteAllBySubjectLine_IupPlan_Id(planId);
        iupSubjectLineRepository.deleteAllByIupPlan_Id(planId);
    }

    private Map<Long, StudentSupportStatus> activeStatuses(String academicYear, LocalDate date) {
        Map<Long, StudentSupportStatus> result = new HashMap<>();
        supportStatusRepository.findAllByAcademicYear(academicYear).stream()
                .filter(status -> contains(status.getValidFrom(), status.getValidTo(), date))
                .sorted(Comparator.comparing(
                        StudentSupportStatus::getValidFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .forEach(status -> result.put(status.getStudent().getId(), status));
        return result;
    }

    private Map<Long, IupPlan> activeIups(String academicYear, LocalDate date) {
        Map<Long, IupPlan> result = new HashMap<>();
        iupPlanRepository.findAllByAcademicYear(academicYear).stream()
                .filter(plan -> plan.getStatus().affectsHeadcount())
                .filter(plan -> contains(plan.getValidFrom(), plan.getValidTo(), date))
                .sorted(Comparator.comparing(IupPlan::getVersionNumber, Comparator.nullsFirst(Integer::compareTo)))
                .forEach(plan -> result.put(plan.getStudent().getId(), plan));
        return result;
    }

    private StudentSupportDtos.ClassSummary toClassSummary(MutableClassSummary source) {
        StudentSupportDtos.ClassSummary result = new StudentSupportDtos.ClassSummary();
        result.setClassName(source.className);
        result.setNormal(source.normal);
        result.setK2(source.k2);
        result.setK3(source.k3);
        result.setIup(source.iup);
        result.setTotal(source.normal + source.k2 + source.k3 + source.iup);
        return result;
    }

    private StudentSupportDtos.RegisterRow toRegisterRow(StudentProfile student,
                                                         String className,
                                                         StudentSupportStatus status,
                                                         IupPlan plan) {
        StudentSupportDtos.RegisterRow row = new StudentSupportDtos.RegisterRow();
        row.setStudentId(student.getId());
        row.setFullName(student.getCurrentFullName());
        row.setClassName(displayClassName(className));
        row.setBirthDate(student.getBirthDate());
        row.setUnderlyingCategory(categoryOf(status));
        if (status != null) {
            row.setSupportStatusId(status.getId());
            row.setNosologyId(status.getNosologyId());
            row.setNosologyCode(status.getNosologyCodeSnapshot());
            row.setNosologyName(status.getNosology() == null ? null : status.getNosology().getName());
            row.setAoopVariant(status.getAoopVariantSnapshot());
            row.setCategoryValidFrom(status.getValidFrom());
            row.setCategoryValidTo(status.getValidTo());
        }
        row.setHasIup(plan != null);
        if (plan != null) {
            row.setIupPlanId(plan.getId());
            row.setIupStatus(plan.getStatus());
            row.setOrderNumber(plan.getOrderNumber());
            row.setOrderDate(plan.getOrderDate());
            row.setIupValidFrom(plan.getValidFrom());
            row.setIupValidTo(plan.getValidTo());
        }
        return row;
    }

    private StudentSupportDtos.StudentOption toStudentOption(StudentProfile profile, ContingentStudent source) {
        if (profile == null) {
            return null;
        }
        StudentSupportDtos.StudentOption option = new StudentSupportDtos.StudentOption();
        option.setStudentId(profile.getId());
        option.setFullName(profile.getCurrentFullName());
        option.setClassName(displayClassName(source.getClassName()));
        option.setBirthDate(profile.getBirthDate());
        option.setRecordNumber(profile.getRecordNumber());
        return option;
    }

    private StudentSupportDtos.CurriculumOption toCurriculumOption(CurriculumPlanEntry entry) {
        StudentSupportDtos.CurriculumOption option = new StudentSupportDtos.CurriculumOption();
        option.setCurriculumEntryId(entry.getId());
        option.setClassName(entry.getClassName());
        option.setSubjectName(entry.getSubjectName());
        option.setSubgroupRequired(entry.isSubgroupRequired());
        option.setSubgroupCount(entry.getSubgroupCount());
        return option;
    }

    private StudentSupportDtos.TeacherOption toTeacherOption(TeacherDirectoryEntry teacher) {
        StudentSupportDtos.TeacherOption option = new StudentSupportDtos.TeacherOption();
        option.setTeacherId(teacher.getId());
        option.setFullName(teacher.getFioTeacher());
        option.setArchived(teacher.isArchived());
        return option;
    }

    private StudentSupportDtos.IupPlanView toIupPlanView(
            IupPlan plan,
            List<IupSubjectLine> lines,
            Map<Long, List<IupTeacherAssignment>> assignmentsByLine
    ) {
        StudentSupportDtos.IupPlanView view = new StudentSupportDtos.IupPlanView();
        view.setId(plan.getId());
        view.setStudentId(plan.getStudent().getId());
        view.setStatus(plan.getStatus());
        view.setOrderNumber(plan.getOrderNumber());
        view.setOrderDate(plan.getOrderDate());
        view.setValidFrom(plan.getValidFrom());
        view.setValidTo(plan.getValidTo());
        view.setVersionNumber(plan.getVersionNumber());
        view.setComment(plan.getComment());
        view.setSubjects(lines.stream().map(line -> {
            StudentSupportDtos.SubjectLineView lineView = new StudentSupportDtos.SubjectLineView();
            lineView.setId(line.getId());
            lineView.setSubjectName(line.getSubjectName());
            lineView.setCurriculumEntryId(line.getCurriculumEntryId());
            lineView.setParticipationMode(line.getParticipationMode());
            lineView.setClassHours(line.getClassHours());
            lineView.setIndividualHours(line.getIndividualHours());
            lineView.setGroupNameEducationalPlan(line.getGroupNameEducationalPlan());
            lineView.setTeachers(assignmentsByLine.getOrDefault(line.getId(), List.of()).stream()
                    .map(this::toTeacherAssignmentView)
                    .toList());
            return lineView;
        }).toList());
        return view;
    }

    private StudentSupportDtos.TeacherAssignmentView toTeacherAssignmentView(IupTeacherAssignment assignment) {
        StudentSupportDtos.TeacherAssignmentView view = new StudentSupportDtos.TeacherAssignmentView();
        view.setId(assignment.getId());
        view.setTeacherId(assignment.getTeacherId());
        view.setTeacherFullName(assignment.getTeacherFioSnapshot());
        view.setHoursPerWeek(assignment.getHoursPerWeek());
        view.setDeliveryForm(assignment.getDeliveryForm());
        view.setValidFrom(assignment.getValidFrom());
        view.setValidTo(assignment.getValidTo());
        return view;
    }

    private void writeClassSummarySheet(Workbook workbook,
                                        StudentSupportDtos.SummaryResponse summary,
                                        CellStyle header) {
        Sheet sheet = workbook.createSheet("Численность");
        String[] columns = {"Класс", "Всего", "Норма", "К2", "К3", "ИУП"};
        writeHeader(sheet, columns, header);
        int rowIndex = 1;
        for (StudentSupportDtos.ClassSummary item : summary.getClasses()) {
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, item.getClassName());
            write(row, 1, item.getTotal());
            write(row, 2, item.getNormal());
            write(row, 3, item.getK2());
            write(row, 4, item.getK3());
            write(row, 5, item.getIup());
        }
        Row total = sheet.createRow(rowIndex);
        write(total, 0, "ИТОГО");
        write(total, 1, summary.getTotalStudents());
        write(total, 2, summary.getClasses().stream().mapToInt(StudentSupportDtos.ClassSummary::getNormal).sum());
        write(total, 3, summary.getClasses().stream().mapToInt(StudentSupportDtos.ClassSummary::getK2).sum());
        write(total, 4, summary.getClasses().stream().mapToInt(StudentSupportDtos.ClassSummary::getK3).sum());
        write(total, 5, summary.getClasses().stream().mapToInt(StudentSupportDtos.ClassSummary::getIup).sum());
        autosize(sheet, columns.length);
        sheet.createFreezePane(1, 1);
    }

    private void writeRegisterSheet(Workbook workbook,
                                    StudentSupportDtos.SummaryResponse summary,
                                    CellStyle header) {
        Sheet sheet = workbook.createSheet("Реестр статусов и ИУП");
        String[] columns = {
                "ФИО", "Класс", "Дата рождения", "Категория",
                "Статус с", "Статус по", "ИУП", "Статус ИУП",
                "Приказ", "Дата приказа", "ИУП с", "ИУП по"
        };
        writeHeader(sheet, columns, header);
        int rowIndex = 1;
        for (StudentSupportDtos.RegisterRow item : summary.getRegisterRows()) {
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, item.getFullName());
            write(row, 1, item.getClassName());
            write(row, 2, item.getBirthDate());
            write(row, 3, categoryLabel(item.getUnderlyingCategory()));
            write(row, 4, item.getCategoryValidFrom());
            write(row, 5, item.getCategoryValidTo());
            write(row, 6, Boolean.TRUE.equals(item.getHasIup()) ? "Да" : "Нет");
            write(row, 7, iupStatusLabel(item.getIupStatus()));
            write(row, 8, item.getOrderNumber());
            write(row, 9, item.getOrderDate());
            write(row, 10, item.getIupValidFrom());
            write(row, 11, item.getIupValidTo());
        }
        autosize(sheet, columns.length);
        sheet.createFreezePane(2, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0,
                Math.max(0, rowIndex - 1),
                0,
                columns.length - 1
        ));
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, String[] values, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values[index]);
            cell.setCellStyle(style);
        }
    }

    private void write(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int column = 0; column < columns; column++) {
            sheet.autoSizeColumn(column);
            int width = Math.min(60 * 256, Math.max(sheet.getColumnWidth(column) + 512, 12 * 256));
            sheet.setColumnWidth(column, width);
        }
    }

    private ContingentSnapshot resolveSnapshot(String academicYear, LocalDate snapshotDate) {
        return (snapshotDate == null
                ? snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                : snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(academicYear, snapshotDate))
                .orElseThrow(() -> new IllegalStateException(
                        snapshotDate == null
                                ? "Для учебного года ещё не загружен контингент"
                                : "Не найден снимок контингента на " + snapshotDate
                ));
    }

    private StudentProfile requireStudent(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("Выберите ребёнка");
        }
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена: " + studentId));
    }

    private NosologyCatalogEntry resolveNosology(Long nosologyId, String nosologyCode) {
        if (nosologyId != null) {
            NosologyCatalogEntry entry = nosologyCatalogEntryRepository.findById(nosologyId)
                    .orElseThrow(() -> new IllegalArgumentException("Нозология не найдена: " + nosologyId));
            if (!entry.isActive()) {
                throw new IllegalArgumentException("Выбранная нозология отключена");
            }
            return entry;
        }
        String code = trimToNull(nosologyCode);
        if (code == null) {
            return null;
        }
        NosologyCatalogEntry entry = nosologyCatalogEntryRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Нозология с кодом «" + code + "» не найдена"));
        if (!entry.isActive()) {
            throw new IllegalArgumentException("Нозология с кодом «" + code + "» отключена");
        }
        return entry;
    }

    private String currentClassName(Long studentId, String academicYear) {
        return enrollmentRepository
                .findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(studentId, academicYear)
                .map(StudentClassEnrollment::getClassName)
                .orElse("");
    }

    private void validateDates(LocalDate from, LocalDate to, String entityName) {
        if (from == null) {
            throw new IllegalArgumentException("Укажите дату начала " + entityName);
        }
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания " + entityName + " не может быть раньше даты начала");
        }
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private boolean overlaps(LocalDate firstFrom, LocalDate firstTo, LocalDate secondFrom, LocalDate secondTo) {
        LocalDate firstEnd = firstTo == null ? LocalDate.MAX : firstTo;
        LocalDate secondEnd = secondTo == null ? LocalDate.MAX : secondTo;
        return !firstEnd.isBefore(secondFrom) && !secondEnd.isBefore(firstFrom);
    }

    private void validateOwner(Long actualStudentId,
                               Long requestedStudentId,
                               String actualYear,
                               String requestedYear) {
        if (!Objects.equals(actualStudentId, requestedStudentId) || !Objects.equals(actualYear, requestedYear)) {
            throw new IllegalArgumentException("Нельзя перенести запись другому ребёнку или в другой учебный год");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        BigDecimal result = value == null ? ZERO : value;
        if (result.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " не могут быть отрицательными");
        }
        return result;
    }

    private BigDecimal positive(BigDecimal value, String fieldName) {
        BigDecimal result = nonNegative(value, fieldName);
        requirePositive(result, fieldName + " должны быть больше нуля");
        return result;
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireZero(BigDecimal value, String message) {
        if (value.signum() != 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private StudentCategory categoryOf(StudentSupportStatus status) {
        return status == null || status.getCategory() == null ? StudentCategory.NORMAL : status.getCategory();
    }

    private String displayClassName(String value) {
        String normalized = ClassNameNormalizer.normalize(value);
        return normalized.isBlank() ? "Класс не указан" : normalized;
    }

    private int compareClassNames(String first, String second) {
        String left = Objects.toString(first, "");
        String right = Objects.toString(second, "");
        int leftParallel = classParallel(left);
        int rightParallel = classParallel(right);
        int byParallel = Integer.compare(leftParallel, rightParallel);
        return byParallel != 0
                ? byParallel
                : left.compareToIgnoreCase(right);
    }

    private int classParallel(String value) {
        String digits = Objects.toString(value, "").trim().replaceAll("^(\\d+).*$", "$1");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String categoryLabel(StudentCategory category) {
        return switch (category == null ? StudentCategory.NORMAL : category) {
            case K2 -> "К2";
            case K3 -> "К3";
            default -> "Норма";
        };
    }

    private String iupStatusLabel(IupStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case DRAFT -> "Черновик";
            case REVIEW -> "На согласовании";
            case APPROVED -> "Утверждён";
            case ACTIVE -> "Действует";
            case CHANGED -> "Изменён";
            case COMPLETED -> "Завершён";
            case CANCELLED -> "Отменён";
        };
    }

    private static final class MutableClassSummary {
        private final String className;
        private int normal;
        private int k2;
        private int k3;
        private int iup;

        private MutableClassSummary(String className) {
            this.className = className;
        }

        private String className() {
            return className;
        }
    }
}
