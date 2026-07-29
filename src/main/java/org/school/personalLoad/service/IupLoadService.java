package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.IupLoadDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.impl.ClassNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IupLoadService {

    private static final BigDecimal K2_COEFFICIENT =
            BigDecimal.valueOf(25).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
    private static final BigDecimal K3_COEFFICIENT =
            BigDecimal.valueOf(25).divide(BigDecimal.valueOf(3), 10, RoundingMode.HALF_UP);

    private final ManualLoadEntryRepository manualLoadRepository;
    private final IupPlanRepository iupPlanRepository;
    private final IupSubjectLineRepository iupSubjectLineRepository;
    private final IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final SubjectCatalogRepository subjectRepository;
    private final ClassroomLeadershipRepository classroomRepository;
    private final LoadSalaryCalculationService salaryCalculationService;

    @Transactional
    public void synchronize(Long planId) {
        manualLoadRepository.deleteAllBySourceIupPlanId(planId);
        IupPlan plan = iupPlanRepository.findById(planId).orElse(null);
        if (plan == null || !isIssued(plan.getStatus())) {
            return;
        }

        Map<Long, IupSubjectLine> lines = iupSubjectLineRepository
                .findAllByIupPlan_IdOrderBySubjectNameAsc(planId).stream()
                .collect(Collectors.toMap(IupSubjectLine::getId, Function.identity()));
        List<IupTeacherAssignment> assignments = iupTeacherAssignmentRepository
                .findAllBySubjectLine_IupPlan_Id(planId).stream()
                .filter(assignment -> assignment.getDeliveryForm() == IupDeliveryForm.FACE_TO_FACE)
                .filter(assignment -> assignment.getTeacher() != null)
                .filter(assignment -> assignment.getHoursPerWeek() != null
                        && assignment.getHoursPerWeek().signum() > 0)
                .toList();
        if (assignments.isEmpty()) {
            return;
        }

        StudentClassEnrollment enrollment = enrollmentAt(
                plan.getStudent().getId(),
                plan.getAcademicYear(),
                plan.getValidFrom()
        );
        StudentCategory category = categoryAt(
                plan.getStudent().getId(),
                plan.getAcademicYear(),
                plan.getValidFrom()
        );

        List<ManualLoadEntry> generated = new ArrayList<>();
        for (IupTeacherAssignment assignment : assignments) {
            IupSubjectLine line = lines.get(assignment.getSubjectLine().getId());
            if (line == null) {
                continue;
            }
            CurriculumPlanEntry curriculum = line.getCurriculumEntryId() == null
                    ? null
                    : curriculumRepository.findById(line.getCurriculumEntryId()).orElse(null);
            SubjectCatalogEntry subject = curriculum == null
                    ? resolveSubject(line.getSubjectName())
                    : curriculum.getSubject();
            if (subject == null) {
                throw new IllegalStateException(
                        "Предмет ИУП «" + line.getSubjectName()
                                + "» отсутствует в справочнике предметов"
                );
            }

            String baseClass = curriculum != null
                    ? curriculum.getClassName()
                    : enrollment == null ? "" : enrollment.getClassName();
            if (baseClass == null || baseClass.isBlank()) {
                throw new IllegalStateException(
                        "Нельзя сформировать нагрузку ИУП: у ребёнка не определён класс"
                );
            }

            ManualLoadEntry row = new ManualLoadEntry();
            row.setAcademicYear(plan.getAcademicYear());
            row.setLoadSource(ManualLoadSource.IUP);
            row.setSourceIupPlanId(plan.getId());
            row.setSourceIupAssignmentId(assignment.getId());
            row.setSourceStudentId(plan.getStudent().getId());
            row.setIupStudentCategory(category);
            row.setTeacherId(assignment.getTeacherId());
            row.setFioTeacher(assignment.getTeacherFioSnapshot());
            row.setSubject(subject);
            row.setSubjectName(subject.getSubjectName());
            row.setClassName(iupClassName(baseClass, plan.getStudent().getCurrentFullName()));
            row.setClassId(null);
            row.setMetaGroupId(null);
            row.setGroupNameEducationalPlan(null);
            row.setPreciseLoadHours(assignment.getHoursPerWeek());
            row.setLoad(legacyWholeHours(assignment.getHoursPerWeek()));
            row.setGroupLoad(null);
            row.setLoadFromDate(assignment.getValidFrom());
            row.setLoadToDate(assignment.getValidTo());
            row.setIncludedInRateHours(BigDecimal.ZERO);
            row.setInRateAllocationConfirmed(true);
            row.setInRateReason("ИУП: отдельная оплата");
            row.setContinuityStatus(ContinuityStatus.UNKNOWN);

            if (curriculum != null) {
                row.setNumberSchoolBuilding(curriculum.getNumberSchoolBuilding());
                row.setSchoolBuildingId(curriculum.getSchoolBuildingId());
                row.setEducationLevel(Objects.requireNonNullElse(curriculum.getEducationLevel(), EducationLevel.BASIC));
                row.setStudyPeriod(resolveStudyPeriod(
                        plan.getAcademicYear(),
                        assignment.getValidFrom(),
                        assignment.getValidTo(),
                        curriculum.getStudyPeriod()
                ));
                row.setCurriculumPart(Objects.requireNonNullElse(curriculum.getCurriculumPart(), CurriculumPart.CORE));
            } else {
                ClassroomLeadershipEntry classRef = enrollment == null ? null : enrollment.getClassRef();
                if (classRef == null) {
                    classRef = classroomRepository
                            .findByAcademicYearAndClassName(plan.getAcademicYear(), baseClass)
                            .orElse(null);
                }
                if (classRef == null) {
                    throw new IllegalStateException(
                            "Нельзя сформировать нагрузку ИУП: класс «" + baseClass
                                    + "» не связан с корпусом"
                    );
                }
                row.setNumberSchoolBuilding(classRef.getNumberSchoolBuilding());
                row.setSchoolBuildingId(classRef.getSchoolBuildingId());
                row.setEducationLevel(EducationLevel.BASIC);
                row.setStudyPeriod(resolveStudyPeriod(
                        plan.getAcademicYear(),
                        assignment.getValidFrom(),
                        assignment.getValidTo(),
                        StudyPeriod.YEAR
                ));
                row.setCurriculumPart(CurriculumPart.CORE);
            }
            generated.add(row);
        }
        manualLoadRepository.saveAll(generated);
    }

    @Transactional
    public void removeForPlan(Long planId) {
        if (planId != null) {
            manualLoadRepository.deleteAllBySourceIupPlanId(planId);
        }
    }

    @Transactional
    public void refreshStudentCategory(Long studentId, String academicYear) {
        List<ManualLoadEntry> rows = manualLoadRepository
                .findAllBySourceStudentIdAndAcademicYearAndLoadSource(
                        studentId,
                        academicYear,
                        ManualLoadSource.IUP
                );
        rows.forEach(row -> row.setIupStudentCategory(categoryAt(
                studentId,
                academicYear,
                Objects.requireNonNullElse(row.getLoadFromDate(), LocalDate.now())
        )));
        manualLoadRepository.saveAll(rows);
    }

    @Transactional
    public List<IupLoadDtos.Row> findAll(String academicYear) {
        Set<Long> synchronizedPlanIds = manualLoadRepository
                .findAllByAcademicYearAndLoadSource(academicYear, ManualLoadSource.IUP).stream()
                .map(ManualLoadEntry::getSourceIupPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        iupPlanRepository.findAllByAcademicYear(academicYear).stream()
                .filter(plan -> isIssued(plan.getStatus()))
                .filter(plan -> !synchronizedPlanIds.contains(plan.getId()))
                .map(IupPlan::getId)
                .toList()
                .forEach(this::synchronize);
        List<ManualLoadEntry> rows = manualLoadRepository
                .findAllByAcademicYearAndLoadSource(academicYear, ManualLoadSource.IUP);
        Map<Long, IupPlan> plans = iupPlanRepository.findAllById(rows.stream()
                        .map(ManualLoadEntry::getSourceIupPlanId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(IupPlan::getId, Function.identity()));
        Map<Long, IupTeacherAssignment> assignments = iupTeacherAssignmentRepository.findAllById(rows.stream()
                        .map(ManualLoadEntry::getSourceIupAssignmentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(IupTeacherAssignment::getId, Function.identity()));
        Map<Long, LoadSalaryCalculationService.SalaryLine> salary =
                salaryCalculationService.calculate(academicYear, rows);
        LocalDate today = LocalDate.now();

        return rows.stream()
                .sorted(Comparator.comparing(ManualLoadEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ManualLoadEntry::getClassName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ManualLoadEntry::getSubjectName, String.CASE_INSENSITIVE_ORDER))
                .map(row -> toView(row, plans.get(row.getSourceIupPlanId()),
                        assignments.get(row.getSourceIupAssignmentId()), salary.get(row.getId()), today))
                .toList();
    }

    @Transactional
    public byte[] exportWorkbook(String academicYear, boolean includeSalary) {
        List<IupLoadDtos.Row> rows = findAll(academicYear);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Нагрузка ИУП");
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setWrapText(true);

            List<String> columns = new ArrayList<>(List.of(
                    "Педагог", "Ребёнок", "Категория", "Предмет", "Класс ИУП",
                    "Базовый класс", "Корпус", "Часы в неделю", "Форма занятий",
                    "Дата с", "Дата по", "Номер приказа", "Дата приказа", "Статус ИУП"
            ));
            if (includeSalary) {
                columns.addAll(List.of(
                        "Коэффициент предмета", "Коэффициент категории",
                        "Предварительная сумма в месяц"
                ));
            }
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < columns.size(); index++) {
                headerRow.createCell(index).setCellValue(columns.get(index));
                headerRow.getCell(index).setCellStyle(header);
            }

            int rowIndex = 1;
            for (IupLoadDtos.Row source : rows) {
                Row row = sheet.createRow(rowIndex++);
                int column = 0;
                row.createCell(column++).setCellValue(Objects.toString(source.getTeacherFullName(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getStudentFullName(), ""));
                row.createCell(column++).setCellValue(categoryLabel(source.getStudentCategory()));
                row.createCell(column++).setCellValue(Objects.toString(source.getSubjectName(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getClassName(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getBaseClassName(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getNumberSchoolBuilding(), ""));
                row.createCell(column++).setCellValue(source.getHoursPerWeek().doubleValue());
                row.createCell(column++).setCellValue(deliveryFormLabel(source.getDeliveryForm()));
                row.createCell(column++).setCellValue(Objects.toString(source.getValidFrom(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getValidTo(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getOrderNumber(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getOrderDate(), ""));
                row.createCell(column++).setCellValue(Objects.toString(source.getIupStatus(), ""));
                if (includeSalary) {
                    row.createCell(column++).setCellValue(source.getSubjectCoefficient().doubleValue());
                    row.createCell(column++).setCellValue(source.getCategoryCoefficient().doubleValue());
                    row.createCell(column).setCellValue(source.getPreliminaryMonthlyAmount().doubleValue());
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, Math.max(0, rowIndex - 1), 0, columns.size() - 1
            ));
            for (int index = 0; index < columns.size(); index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 512, 60 * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать выгрузку нагрузки ИУП", exception);
        }
    }

    private IupLoadDtos.Row toView(ManualLoadEntry row,
                                   IupPlan plan,
                                   IupTeacherAssignment assignment,
                                   LoadSalaryCalculationService.SalaryLine salary,
                                   LocalDate today) {
        IupLoadDtos.Row view = new IupLoadDtos.Row();
        view.setManualLoadEntryId(row.getId());
        view.setIupPlanId(row.getSourceIupPlanId());
        view.setAssignmentId(row.getSourceIupAssignmentId());
        view.setStudentId(row.getSourceStudentId());
        view.setStudentFullName(plan == null ? "" : plan.getStudent().getCurrentFullName());
        view.setStudentCategory(Objects.requireNonNullElse(row.getIupStudentCategory(), StudentCategory.NORMAL));
        view.setTeacherId(row.getTeacherId());
        view.setTeacherFullName(row.getFioTeacher());
        view.setSubjectName(row.getSubjectName());
        view.setClassName(row.getClassName());
        view.setBaseClassName(baseClassName(row.getClassName()));
        view.setNumberSchoolBuilding(row.getNumberSchoolBuilding());
        view.setHoursPerWeek(row.getEffectiveLoadHours());
        view.setStudyPeriod(row.getStudyPeriod());
        view.setDeliveryForm(assignment == null ? IupDeliveryForm.FACE_TO_FACE : assignment.getDeliveryForm());
        view.setValidFrom(row.getLoadFromDate());
        view.setValidTo(row.getLoadToDate());
        view.setIupStatus(plan == null ? null : plan.getStatus());
        view.setOrderNumber(plan == null ? null : plan.getOrderNumber());
        view.setOrderDate(plan == null ? null : plan.getOrderDate());
        view.setSubjectCoefficient(salary == null ? BigDecimal.ONE : salary.subjectCoefficient());
        view.setCategoryCoefficient(categoryCoefficient(view.getStudentCategory()));
        view.setPreliminaryMonthlyAmount(salary == null ? BigDecimal.ZERO : salary.amount());
        view.setActiveNow(contains(row.getLoadFromDate(), row.getLoadToDate(), today)
                && plan != null && isIssued(plan.getStatus()));
        return view;
    }

    private StudentClassEnrollment enrollmentAt(Long studentId, String academicYear, LocalDate date) {
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                .stream()
                .filter(enrollment -> enrollment.getStatus() == StudentEnrollmentStatus.ACTIVE)
                .filter(enrollment -> contains(enrollment.getValidFrom(), enrollment.getValidTo(), date))
                .findFirst()
                .orElseGet(() -> enrollmentRepository
                        .findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(
                                studentId,
                                academicYear
                        )
                        .orElse(null));
    }

    private StudentCategory categoryAt(Long studentId, String academicYear, LocalDate date) {
        return supportStatusRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                .stream()
                .filter(status -> contains(status.getValidFrom(), status.getValidTo(), date))
                .map(StudentSupportStatus::getCategory)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(StudentCategory.NORMAL);
    }

    private SubjectCatalogEntry resolveSubject(String subjectName) {
        String normalized = normalize(subjectName);
        return subjectRepository.findAll().stream()
                .filter(subject -> normalize(subject.getSubjectName()).equals(normalized))
                .sorted(Comparator.comparing(subject -> subject.getSubjectType() == SubjectType.CORE ? 0 : 1))
                .findFirst()
                .orElse(null);
    }

    private boolean isIssued(IupStatus status) {
        return status == IupStatus.APPROVED
                || status == IupStatus.ACTIVE
                || status == IupStatus.CHANGED
                || status == IupStatus.COMPLETED;
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return date != null
                && (from == null || !date.isBefore(from))
                && (to == null || !date.isAfter(to));
    }

    private int legacyWholeHours(BigDecimal hours) {
        return hours == null ? 0 : hours.max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private StudyPeriod resolveStudyPeriod(String academicYear,
                                           LocalDate from,
                                           LocalDate to,
                                           StudyPeriod curriculumPeriod) {
        if (curriculumPeriod == StudyPeriod.H1 || curriculumPeriod == StudyPeriod.H2) {
            return curriculumPeriod;
        }
        try {
            int startYear = Integer.parseInt(academicYear.substring(0, 4));
            LocalDate firstHalfEnd = LocalDate.of(startYear, 12, 31);
            LocalDate secondHalfStart = LocalDate.of(startYear + 1, 1, 1);
            if (to != null && !to.isAfter(firstHalfEnd)) {
                return StudyPeriod.H1;
            }
            if (from != null && !from.isBefore(secondHalfStart)) {
                return StudyPeriod.H2;
            }
        } catch (RuntimeException ignored) {
            // Keep annual scope for non-standard academic-year codes.
        }
        return StudyPeriod.YEAR;
    }

    private String iupClassName(String baseClassName, String fullName) {
        return "ИУП-" + normalizeClassPart(baseClassName) + "-" + shortName(fullName);
    }

    private String baseClassName(String iupClassName) {
        String value = Objects.toString(iupClassName, "");
        if (!value.startsWith("ИУП-")) {
            return value;
        }
        int surnameSeparator = value.lastIndexOf('-');
        return surnameSeparator <= 4 ? value.substring(4) : value.substring(4, surnameSeparator);
    }

    private String shortName(String fullName) {
        String[] parts = Objects.toString(fullName, "").trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return "Ребёнок";
        }
        StringBuilder result = new StringBuilder(parts[0]);
        for (int index = 1; index < Math.min(parts.length, 3); index++) {
            if (!parts[index].isBlank()) {
                if (index == 1) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(parts[index].charAt(0))).append('.');
            }
        }
        return result.toString();
    }

    private String normalizeClassPart(String value) {
        return ClassNameNormalizer.normalize(Objects.toString(value, "")).trim()
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s*-\\s*", "-")
                .replaceAll("\\s+", "-");
    }

    private String normalize(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static BigDecimal categoryCoefficient(StudentCategory category) {
        if (category == StudentCategory.K2) {
            return K2_COEFFICIENT;
        }
        if (category == StudentCategory.K3) {
            return K3_COEFFICIENT;
        }
        return BigDecimal.ONE;
    }

    private String categoryLabel(StudentCategory category) {
        if (category == StudentCategory.K2) return "К2";
        if (category == StudentCategory.K3) return "К3";
        return "Норма";
    }

    private String deliveryFormLabel(IupDeliveryForm deliveryForm) {
        return deliveryForm == IupDeliveryForm.FACE_TO_FACE ? "Очно" : "Дистанционно";
    }
}
