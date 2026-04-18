package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.AcademicYearRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;

    @Override
    public List<AcademicYearConfig> findAll() {
        List<AcademicYearConfig> years = academicYearRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicYearConfig::getCode))
                .toList();
        if (years.isEmpty()) {
            create(currentByDate());
            years = academicYearRepository.findAll().stream()
                    .sorted(Comparator.comparing(AcademicYearConfig::getCode))
                    .toList();
        }
        return years;
    }

    @Override
    public AcademicYearConfig create(String code) {
        String normalized = normalizeCode(code);
        if (academicYearRepository.existsByCode(normalized)) {
            throw new IllegalArgumentException("Учебный год уже существует: " + normalized);
        }
        AcademicYearConfig entity = new AcademicYearConfig();
        entity.setCode(normalized);
        return academicYearRepository.save(entity);
    }

    @Override
    public void delete(Long id) {
        academicYearRepository.deleteById(id);
    }

    @Override
    public String resolveRequestedOrDefault(String requestedCode) {
        List<AcademicYearConfig> years = findAll();
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            boolean exists = years.stream().anyMatch(y -> y.getCode().equals(normalized));
            if (exists) {
                return normalized;
            }
        }

        String current = currentByDate();
        boolean currentExists = years.stream().anyMatch(y -> y.getCode().equals(current));
        if (currentExists) {
            return current;
        }

        return years.stream()
                .map(AcademicYearConfig::getCode)
                .filter(code -> code.compareTo(current) > 0)
                .sorted()
                .findFirst()
                .orElseGet(() -> years.stream().map(AcademicYearConfig::getCode).max(String::compareTo).orElse(current));
    }

    @Override
    public String currentByDate() {
        LocalDate now = LocalDate.now();
        int startYear = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    @Override
    @Transactional
    public AcademicYearConfig markContinuityApplied(String code) {
        String normalized = normalizeCode(code);
        AcademicYearConfig year = academicYearRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Учебный год не найден: " + normalized));
        applyTeacherContinuity(normalized);
        year.setContinuityApplied(true);
        return academicYearRepository.save(year);
    }

    @Override
    @Transactional
    public AcademicYearConfig applyBuildingContinuity(String code) {
        String normalized = normalizeCode(code);
        AcademicYearConfig year = academicYearRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Учебный год не найден: " + normalized));
        applyClassBuildingContinuity(normalized);
        return academicYearRepository.save(year);
    }

    private void applyTeacherContinuity(String targetYear) {
        String sourceYear = previousAcademicYear(targetYear);
        if (!academicYearRepository.existsByCode(sourceYear)) {
            throw new IllegalArgumentException("Для преемственности не найден предыдущий учебный год: " + sourceYear);
        }

        List<CurriculumPlanEntry> targetCurriculum = curriculumPlanEntryRepository.findAll().stream()
                .filter(entry -> targetYear.equals(entry.getAcademicYear()))
                .filter(entry -> !entry.isDeprecated())
                .toList();
        if (targetCurriculum.isEmpty()) {
            throw new IllegalArgumentException("Невозможно выполнить преемственность: в " + targetYear + " не загружен учебный план");
        }

        List<ManualLoadEntry> sourceManual = manualLoadEntryRepository.findAllByAcademicYear(sourceYear).stream()
                .filter(entry -> entry.getFioTeacher() != null && !entry.getFioTeacher().isBlank())
                .toList();

        Map<String, ManualLoadEntry> sourceAssignments = sourceManual.stream()
                .filter(entry -> entry.getFioTeacher() != null && !entry.getFioTeacher().isBlank())
                .collect(Collectors.toMap(
                        this::continuityKey,
                        Function.identity(),
                        (left, right) -> left.getId() != null && right.getId() != null && left.getId() > right.getId() ? left : right
                ));
        Map<String, List<ManualLoadEntry>> sourceAssignmentsByClassSubject = sourceManual.stream()
                .collect(Collectors.groupingBy(this::classSubjectKey));

        if (sourceAssignments.isEmpty()) {
            throw new IllegalArgumentException("Невозможно выполнить преемственность: в " + sourceYear + " нет распределённых педагогов");
        }

        Map<String, ManualLoadEntry> targetExisting = manualLoadEntryRepository.findAllByAcademicYear(targetYear).stream()
                .collect(Collectors.toMap(this::continuityKey, Function.identity(), (left, right) -> left));

        List<ManualLoadEntry> toCreate = targetCurriculum.stream()
                .map(curriculum -> {
                    String key = continuityKey(curriculum);
                    if (targetExisting.containsKey(key)) {
                        return null;
                    }
                    ManualLoadEntry source = sourceAssignments.get(key);
                    if (source == null) {
                        List<ManualLoadEntry> fallback = sourceAssignmentsByClassSubject.get(classSubjectKey(curriculum));
                        if (fallback != null && !fallback.isEmpty()) {
                            source = fallback.get(0);
                        }
                    }
                    if (source == null) {
                        return null;
                    }
                    return createContinuityEntry(targetYear, curriculum, source);
                })
                .filter(Objects::nonNull)
                .toList();

        if (!toCreate.isEmpty()) {
            manualLoadEntryRepository.saveAll(toCreate);
        }
    }

    private void applyClassBuildingContinuity(String targetYear) {
        String sourceYear = previousAcademicYear(targetYear);
        if (!academicYearRepository.existsByCode(sourceYear)) {
            throw new IllegalArgumentException("Для преемственности корпусов не найден предыдущий учебный год: " + sourceYear);
        }

        List<CurriculumPlanEntry> targetCurriculum = curriculumPlanEntryRepository.findAll().stream()
                .filter(entry -> targetYear.equals(entry.getAcademicYear()))
                .filter(entry -> !entry.isDeprecated())
                .toList();
        if (targetCurriculum.isEmpty()) {
            throw new IllegalArgumentException("Невозможно выполнить преемственность корпусов: в " + targetYear + " не загружен учебный план");
        }

        Map<String, ClassroomLeadershipEntry> sourceByClass = classroomLeadershipRepository.findAllByAcademicYear(sourceYear).stream()
                .collect(Collectors.toMap(
                        entry -> normalizeKey(entry.getClassName()),
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<String, ClassroomLeadershipEntry> targetByClass = classroomLeadershipRepository.findAllByAcademicYear(targetYear).stream()
                .collect(Collectors.toMap(
                        entry -> normalizeKey(entry.getClassName()),
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<String, CurriculumPlanEntry> targetCurriculumByClass = targetCurriculum.stream()
                .collect(Collectors.toMap(
                        entry -> normalizeKey(entry.getClassName()),
                        Function.identity(),
                        (left, right) -> left
                ));

        List<ClassroomLeadershipEntry> toSave = new java.util.ArrayList<>();
        for (Map.Entry<String, CurriculumPlanEntry> entry : targetCurriculumByClass.entrySet()) {
            CurriculumPlanEntry curriculum = entry.getValue();
            String className = curriculum.getClassName();
            ClassroomLeadershipEntry existingTarget = targetByClass.get(entry.getKey());

            if (existingTarget != null && !isAutoFilledBuilding(existingTarget)) {
                continue;
            }

            String sourceClassName = previousClassForContinuity(className);
            String targetBuilding = "СП0";
            String targetAddress = "Не указан";
            if (sourceClassName != null) {
                ClassroomLeadershipEntry source = sourceByClass.get(normalizeKey(sourceClassName));
                if (source != null && source.getNumberSchoolBuilding() != null && !source.getNumberSchoolBuilding().isBlank()) {
                    targetBuilding = source.getNumberSchoolBuilding();
                    targetAddress = source.getCampusAddress();
                }
            }

            ClassroomLeadershipEntry targetEntry = existingTarget == null ? new ClassroomLeadershipEntry() : existingTarget;
            targetEntry.setAcademicYear(targetYear);
            targetEntry.setClassName(className);
            targetEntry.setNumberSchoolBuilding(targetBuilding);
            targetEntry.setCampusAddress(targetAddress == null || targetAddress.isBlank() ? "Не указан" : targetAddress);
            if (targetEntry.getClassDirection() == null || targetEntry.getClassDirection().isBlank()) {
                targetEntry.setClassDirection("Не указана");
            }
            if (targetEntry.getFioTeacher() == null || targetEntry.getFioTeacher().isBlank()) {
                targetEntry.setFioTeacher("Класс не назначен");
            }
            toSave.add(targetEntry);
        }

        if (!toSave.isEmpty()) {
            classroomLeadershipRepository.saveAll(toSave);
        }
    }

    private String previousClassForContinuity(String targetClassName) {
        String normalized = ClassNameNormalizer.normalize(targetClassName);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{1,2})(?:\\s*[- ]?\\s*([А-ЯA-Z]))?$").matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int parallel = Integer.parseInt(matcher.group(1));
        if (parallel <= 1) return null;
        if (parallel == 5 || parallel == 10) return null;
        int previousParallel = parallel - 1;
        String suffix = matcher.group(2);
        return suffix == null || suffix.isBlank()
                ? String.valueOf(previousParallel)
                : previousParallel + "-" + suffix.toUpperCase(Locale.ROOT);
    }

    private boolean isAutoFilledBuilding(ClassroomLeadershipEntry entry) {
        String building = String.valueOf(entry.getNumberSchoolBuilding() == null ? "" : entry.getNumberSchoolBuilding()).trim().toUpperCase(Locale.ROOT);
        return building.isBlank() || "СП0".equals(building);
    }

    private String previousAcademicYear(String academicYearCode) {
        int slash = academicYearCode.indexOf('/');
        int from = Integer.parseInt(academicYearCode.substring(0, slash));
        return (from - 1) + "/" + from;
    }

    private String continuityKey(ManualLoadEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getGroupNameEducationalPlan()
        );
    }

    private String continuityKey(CurriculumPlanEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                ""
        );
    }

    private String classSubjectKey(ManualLoadEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                ""
        );
    }

    private String classSubjectKey(CurriculumPlanEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                ""
        );
    }

    private String joinKey(String className, String subjectName, String groupNameEducationalPlan) {
        return String.join("|",
                normalizeKey(className),
                normalizeKey(subjectName),
                normalizeKey(groupNameEducationalPlan));
    }

    private String normalizeKey(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private ManualLoadEntry createContinuityEntry(String targetYear, CurriculumPlanEntry curriculum, ManualLoadEntry source) {
        ManualLoadEntry entry = new ManualLoadEntry();
        entry.setAcademicYear(targetYear);
        entry.setFioTeacher(source.getFioTeacher().trim());
        entry.setNumberSchoolBuilding(curriculum.getNumberSchoolBuilding());
        entry.setSubjectName(curriculum.getSubjectName());
        entry.setClassName(curriculum.getClassName());
        entry.setLoad(curriculum.getPlannedHours() == null ? 0 : curriculum.getPlannedHours().intValue());
        entry.setGroupNameEducationalPlan(source.getGroupNameEducationalPlan());
        entry.setGroupLoad(source.getGroupLoad() == null ? entry.getLoad() : source.getGroupLoad());
        entry.setEducationLevel(curriculum.getEducationLevel());
        StudyPeriod studyPeriod = curriculum.getStudyPeriod() == null ? StudyPeriod.YEAR : curriculum.getStudyPeriod();
        entry.setStudyPeriod(studyPeriod);
        entry.setLoadFromDate(defaultFromDate(targetYear, studyPeriod));
        entry.setLoadToDate(defaultToDate(targetYear, studyPeriod));
        entry.setOrphaned(false);
        entry.setDismissalAdjusted(false);
        return entry;
    }

    private LocalDate defaultFromDate(String academicYearCode, StudyPeriod studyPeriod) {
        int fromYear = Integer.parseInt(academicYearCode.substring(0, 4));
        if (studyPeriod == StudyPeriod.H2) {
            return LocalDate.of(fromYear + 1, 1, 1);
        }
        return LocalDate.of(fromYear, 9, 1);
    }

    private LocalDate defaultToDate(String academicYearCode, StudyPeriod studyPeriod) {
        int fromYear = Integer.parseInt(academicYearCode.substring(0, 4));
        if (studyPeriod == StudyPeriod.H1) {
            return LocalDate.of(fromYear, 12, 31);
        }
        return LocalDate.of(fromYear + 1, 5, 31);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        String normalized = code.trim().replace('\\', '/');
        if (normalized.matches("\\d{4}")) {
            int from = Integer.parseInt(normalized);
            return from + "/" + (from + 1);
        }
        if (!normalized.matches("\\d{4}/\\d{4}")) {
            throw new IllegalArgumentException("Формат учебного года должен быть YYYY или YYYY/YYYY");
        }
        int from = Integer.parseInt(normalized.substring(0, 4));
        int to = Integer.parseInt(normalized.substring(5));
        if (to != from + 1) {
            throw new IllegalArgumentException("Учебный год должен быть последовательным, например 2026/2027");
        }
        return normalized;
    }
}
