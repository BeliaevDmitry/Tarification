package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.SalaryGroupCoefficientSubjectRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;
import org.school.personalLoad.service.impl.ClassNameNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoadSalaryCalculationService {
    private static final BigDecimal STUDENT_HOUR_MULTIPLIER =
            BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
    private static final BigDecimal GROUP_BASE_SIZE = BigDecimal.valueOf(25);

    private final ClassSizeService classSizeService;
    private final SalarySettingsRepository salarySettingsRepository;
    private final SubjectLevelCoefficientRepository coefficientRepository;
    private final SalaryGroupCoefficientSubjectRepository groupSubjectRepository;
    private final IupCompensationCalculator iupCompensationCalculator;

    @Autowired(required = false)
    @Lazy
    private StudentDataExchangeService studentDataExchangeService;

    public Map<Long, SalaryLine> calculate(String academicYear, Collection<ManualLoadEntry> rows) {
        Collection<ManualLoadEntry> safeRows = Optional.ofNullable(rows).orElse(List.of());
        CalculationContext context = context(academicYear, safeRows);
        return safeRows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(
                        ManualLoadEntry::getId,
                        row -> calculate(row, context),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    public SalaryLine calculate(String academicYear, ManualLoadEntry row) {
        return calculate(row, context(academicYear, row == null ? List.of() : List.of(row)));
    }

    public BigDecimal totalHours(ManualLoadEntry row) {
        return row == null ? BigDecimal.ZERO : row.getEffectiveLoadHours();
    }

    public BigDecimal includedHours(ManualLoadEntry row) {
        BigDecimal total = totalHours(row);
        BigDecimal included = Optional.ofNullable(row.getIncludedInRateHours()).orElse(BigDecimal.ZERO);
        if (included.signum() < 0) return BigDecimal.ZERO;
        return included.min(total);
    }

    public BigDecimal paidHours(ManualLoadEntry row) {
        return totalHours(row).subtract(includedHours(row)).max(BigDecimal.ZERO);
    }

    private CalculationContext context(String academicYear, Collection<ManualLoadEntry> rows) {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        classSizeService.effectiveClassSizes(academicYear).forEach((name, count) ->
                sizes.put(normalizeClass(name), count == null ? 0 : Math.max(count, 0)));
        Map<Long, Integer> contingentCounts = new LinkedHashMap<>();
        if (studentDataExchangeService != null) {
            try {
                StudentDataExchangeService.StudentCountResolution resolution =
                        studentDataExchangeService.resolveStudentCounts(academicYear, rows);
                if (resolution != null && resolution.contingentMode()
                        && resolution.childrenByLoadEntry() != null) {
                    contingentCounts.putAll(resolution.childrenByLoadEntry());
                }
            } catch (RuntimeException exception) {
                log.warn("Не удалось применить фактическую численность для {}: {}. Используется резервный расчёт.",
                        academicYear, exception.getMessage());
            }
        }
        Map<String, BigDecimal> coefficients = coefficientRepository.findAll().stream()
                .collect(Collectors.toMap(
                        row -> coefficientKey(row.getSubjectName(), row.getEducationStage()),
                        row -> positive(row.getCoefficient()),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        Set<Long> groupSubjectIds = groupSubjectRepository.findAll().stream()
                .map(SalaryGroupCoefficientSubject::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> groupSubjectNames = groupSubjectRepository.findAll().stream()
                .map(SalaryGroupCoefficientSubject::getSubjectName)
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        BigDecimal rate = salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)
                .map(SalarySettings::getStudentHourRate)
                .filter(value -> value != null && value.signum() > 0)
                .orElse(SalarySettings.DEFAULT_STUDENT_HOUR_RATE);
        return new CalculationContext(sizes, contingentCounts, coefficients, groupSubjectIds, groupSubjectNames, rate);
    }

    private SalaryLine calculate(ManualLoadEntry row, CalculationContext context) {
        BigDecimal totalHours = totalHours(row);
        BigDecimal includedHours = includedHours(row);
        BigDecimal paidHours = totalHours.subtract(includedHours).max(BigDecimal.ZERO);
        int classSize = context.classSizes().getOrDefault(normalizeClass(row.getClassName()), 30);
        String group = normalizeText(row.getGroupNameEducationalPlan());
        int first = (classSize + 1) / 2;
        int second = classSize - first;
        int legacyChildren = group.contains("2") ? second : group.contains("1") ? first : classSize;
        int children = context.contingentCounts().getOrDefault(row.getId(), legacyChildren);
        children = Math.max(children, 0);
        EducationStage stage = stage(row.getClassName());
        BigDecimal subjectCoefficient = context.subjectCoefficients()
                .getOrDefault(coefficientKey(row.getSubjectName(), stage), BigDecimal.ONE);
        if (row.isIupLoad()) {
            StudentCategory category = Objects.requireNonNullElse(
                    row.getIupStudentCategory(),
                    StudentCategory.NORMAL
            );
            IupCompensationCalculator.Calculation iup =
                    iupCompensationCalculator.calculate(paidHours, subjectCoefficient, category);
            return new SalaryLine(
                    row.getId(),
                    row.getTeacherId(),
                    row.getEmploymentContractId(),
                    totalHours,
                    BigDecimal.ZERO,
                    paidHours,
                    1,
                    BigDecimal.ONE,
                    iup.subjectCoefficient(),
                    iup.categoryCoefficient(),
                    iup.monthlyAmount(),
                    true,
                    "ИУП: отдельная оплата"
            );
        }
        boolean groupEnabled = row.getSubjectId() != null
                ? context.groupSubjectIds().contains(row.getSubjectId())
                : context.groupSubjectNames().contains(normalizeText(row.getSubjectName()));
        BigDecimal groupCoefficient = !group.isBlank() && groupEnabled && children > 0
                ? GROUP_BASE_SIZE.divide(BigDecimal.valueOf(children), 10, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        BigDecimal base = context.studentHourRate()
                .multiply(BigDecimal.valueOf(children))
                .multiply(paidHours)
                .multiply(STUDENT_HOUR_MULTIPLIER);
        BigDecimal amount = base
                .add(base.multiply(subjectCoefficient.subtract(BigDecimal.ONE)))
                .add(base.multiply(groupCoefficient.subtract(BigDecimal.ONE)));
        return new SalaryLine(
                row.getId(),
                row.getTeacherId(),
                row.getEmploymentContractId(),
                totalHours,
                includedHours,
                paidHours,
                children,
                context.studentHourRate(),
                subjectCoefficient,
                groupCoefficient,
                amount,
                row.isInRateAllocationConfirmed(),
                Objects.toString(row.getInRateReason(), "")
        );
    }

    private BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? BigDecimal.ONE : value;
    }

    private String coefficientKey(String subjectName, EducationStage stage) {
        return normalizeText(subjectName) + "|" + Objects.toString(stage, "");
    }

    private String normalizeClass(String value) {
        return normalizeText(ClassNameNormalizer.normalize(value))
                .replace(" ", "")
                .replace('ё', 'е')
                .replace('–', '-')
                .replace('—', '-');
    }

    private String normalizeText(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private EducationStage stage(String className) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+")
                .matcher(Objects.toString(className, ""));
        if (!matcher.find()) return EducationStage.OOO;
        int grade = Integer.parseInt(matcher.group());
        return grade <= 4 ? EducationStage.NOO : grade <= 9 ? EducationStage.OOO : EducationStage.SOO;
    }

    private record CalculationContext(
            Map<String, Integer> classSizes,
            Map<Long, Integer> contingentCounts,
            Map<String, BigDecimal> subjectCoefficients,
            Set<Long> groupSubjectIds,
            Set<String> groupSubjectNames,
            BigDecimal studentHourRate
    ) {
    }

    public record SalaryLine(
            Long manualLoadEntryId,
            Long teacherId,
            Long contractId,
            BigDecimal totalHours,
            BigDecimal includedHours,
            BigDecimal paidHours,
            int children,
            BigDecimal studentHourRate,
            BigDecimal subjectCoefficient,
            BigDecimal groupCoefficient,
            BigDecimal amount,
            boolean allocationConfirmed,
            String reason
    ) {
    }
}
