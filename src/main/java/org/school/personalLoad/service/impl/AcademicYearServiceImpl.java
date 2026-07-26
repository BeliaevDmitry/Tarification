package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.model.ContinuityStatus;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.CurriculumModule;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.AcademicYearRepository;
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
        return academicYearForDate(LocalDate.now());
    }

    static String academicYearForDate(LocalDate date) {
        int startYear = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
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

    private void applyTeacherContinuity(String targetYear) {
        String sourceYear = previousAcademicYear(targetYear);
        if (!academicYearRepository.existsByCode(sourceYear)) {
            throw new IllegalArgumentException("Для преемственности не найден предыдущий учебный год: " + sourceYear);
        }

        List<CurriculumPlanEntry> allCurriculum = curriculumPlanEntryRepository.findAll();
        List<CurriculumPlanEntry> targetCurriculum = allCurriculum.stream()
                .filter(entry -> targetYear.equals(entry.getAcademicYear()))
                .filter(entry -> !entry.isDeprecated())
                .toList();
        Map<Long, CurriculumModule> sourceModulesById = allCurriculum.stream()
                .filter(entry -> sourceYear.equals(entry.getAcademicYear()))
                .flatMap(entry -> entry.getModules().stream())
                .filter(module -> module.getId() != null)
                .collect(Collectors.toMap(CurriculumModule::getId, Function.identity(), (left, right) -> left));
        if (targetCurriculum.isEmpty()) {
            throw new IllegalArgumentException("Невозможно выполнить преемственность: в " + targetYear + " не загружен учебный план");
        }

        List<ManualLoadEntry> sourceManual = manualLoadEntryRepository.findAllByAcademicYear(sourceYear).stream()
                .filter(entry -> entry.getFioTeacher() != null && !entry.getFioTeacher().isBlank())
                .toList();

        if (sourceManual.isEmpty()) {
            throw new IllegalArgumentException("Невозможно выполнить преемственность: в " + sourceYear + " нет распределённых педагогов");
        }

        Map<String, CurriculumPlanEntry> curriculumByClassSubjectPeriod = targetCurriculum.stream()
                .collect(Collectors.toMap(
                        this::classSubjectPeriodKey,
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<String, ManualLoadEntry> targetExisting = manualLoadEntryRepository.findAllByAcademicYear(targetYear).stream()
                .collect(Collectors.toMap(this::continuityKey, Function.identity(), (left, right) -> left));

        List<ManualLoadEntry> toCreate = sourceManual.stream()
                .map(source -> {
                    StudyPeriod period = source.getStudyPeriod() == null ? StudyPeriod.YEAR : source.getStudyPeriod();
                    List<String> classCandidates = continuityClassCandidates(source.getClassName());
                    for (String targetClass : classCandidates) {
                        CurriculumPlanEntry curriculum = curriculumByClassSubjectPeriod.get(
                                classSubjectPeriodKey(targetClass, source.getSubjectName(), source.getCurriculumPart(), period)
                        );
                        if (curriculum == null && period != StudyPeriod.YEAR) {
                            curriculum = curriculumByClassSubjectPeriod.get(
                                    classSubjectPeriodKey(targetClass, source.getSubjectName(), source.getCurriculumPart(), StudyPeriod.YEAR)
                            );
                        }
                        if (curriculum == null) {
                            continue;
                        }
                        CurriculumModule targetModule = resolveContinuityModule(source, sourceModulesById, curriculum);
                        if ((source.getCurriculumModuleId() != null || curriculum.isModularSystem()) && targetModule == null) {
                            continue;
                        }
                        String targetKey = joinKey(targetClass, source.getSubjectName(), source.getCurriculumPart(), source.getGroupNameEducationalPlan())
                                + "|module:" + String.valueOf(targetModule == null ? "" : targetModule.getId());
                        if (targetExisting.containsKey(targetKey)) {
                            return null;
                        }
                        ManualLoadEntry created = createContinuityEntry(targetYear, curriculum, targetModule, source);
                        targetExisting.put(targetKey, created);
                        return created;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        if (!toCreate.isEmpty()) {
            manualLoadEntryRepository.saveAll(toCreate);
        }
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
                entry.getCurriculumPart(),
                entry.getGroupNameEducationalPlan()
        ) + "|module:" + String.valueOf(entry.getCurriculumModuleId() == null ? "" : entry.getCurriculumModuleId());
    }

    private String continuityKey(CurriculumPlanEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getCurriculumPart(),
                ""
        );
    }

    private String classSubjectKey(ManualLoadEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getCurriculumPart(),
                ""
        );
    }

    private String classSubjectKey(CurriculumPlanEntry entry) {
        return joinKey(
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getCurriculumPart(),
                ""
        );
    }

    private String classSubjectPeriodKey(CurriculumPlanEntry entry) {
        StudyPeriod period = entry.getStudyPeriod() == null ? StudyPeriod.YEAR : entry.getStudyPeriod();
        return classSubjectPeriodKey(entry.getClassName(), entry.getSubjectName(), entry.getCurriculumPart(), period);
    }

    private String classSubjectPeriodKey(String className, String subjectName, CurriculumPart curriculumPart, StudyPeriod studyPeriod) {
        return joinKey(className, subjectName, curriculumPart, studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name());
    }

    private String joinKey(String className, String subjectName, CurriculumPart curriculumPart, String groupNameEducationalPlan) {
        return String.join("|",
                normalizeKey(className),
                normalizeKey(subjectName),
                (curriculumPart == null ? CurriculumPart.CORE : curriculumPart).name(),
                normalizeKey(groupNameEducationalPlan));
    }

    private String normalizeKey(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String nextClassForContinuity(String sourceClassName) {
        String normalized = ClassNameNormalizer.normalize(sourceClassName);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{1,2})-([А-ЯA-Z])$").matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int parallel = Integer.parseInt(matcher.group(1));
        if (parallel >= 11) {
            return null;
        }
        if (parallel == 4 || parallel == 6 || parallel == 9 || parallel == 11) {
            return null;
        }
        return (parallel + 1) + "-" + matcher.group(2);
    }

    private boolean isExcludedGraduationParallel(String className) {
        String normalized = ClassNameNormalizer.normalize(className);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{1,2})-([А-ЯA-Z])$").matcher(normalized);
        if (!matcher.matches()) {
            return false;
        }
        int parallel = Integer.parseInt(matcher.group(1));
        return parallel == 4 || parallel == 6 || parallel == 9 || parallel == 11;
    }

    private List<String> continuityClassCandidates(String sourceClassName) {
        String normalizedSource = ClassNameNormalizer.normalize(sourceClassName);
        if (isExcludedGraduationParallel(normalizedSource)) {
            return List.of();
        }
        String nextClass = nextClassForContinuity(normalizedSource);
        if (nextClass == null || nextClass.equals(normalizedSource)) {
            return List.of(normalizedSource);
        }
        return List.of(nextClass, normalizedSource);
    }

    private ManualLoadEntry createContinuityEntry(String targetYear,
                                                  CurriculumPlanEntry curriculum,
                                                  CurriculumModule module,
                                                  ManualLoadEntry source) {
        ManualLoadEntry entry = new ManualLoadEntry();
        entry.setAcademicYear(targetYear);
        entry.setFioTeacher(source.getFioTeacher().trim());
        entry.setNumberSchoolBuilding(curriculum.getNumberSchoolBuilding());
        entry.setSubjectName(curriculum.getSubjectName());
        entry.setClassName(curriculum.getClassName());
        int plannedLoad = module == null
                ? (curriculum.getPlannedHours() == null ? 0 : curriculum.getPlannedHours().intValue())
                : module.getPlannedHours().intValue();
        if (module != null && source.getGroupNameEducationalPlan() != null) {
            if (source.getGroupNameEducationalPlan().contains("1") && module.getSubgroup1Hours() != null) plannedLoad = module.getSubgroup1Hours();
            if (source.getGroupNameEducationalPlan().contains("2") && module.getSubgroup2Hours() != null) plannedLoad = module.getSubgroup2Hours();
        }
        entry.setLoad(plannedLoad);
        entry.setGroupNameEducationalPlan(source.getGroupNameEducationalPlan());
        entry.setGroupLoad(source.getGroupLoad() == null ? entry.getLoad() : source.getGroupLoad());
        entry.setCurriculumModuleId(module == null ? null : module.getId());
        entry.setCurriculumPart(curriculum.getCurriculumPart());
        entry.setEducationLevel(module == null ? curriculum.getEducationLevel() : module.getEducationLevel());
        StudyPeriod studyPeriod = curriculum.getStudyPeriod() == null ? StudyPeriod.YEAR : curriculum.getStudyPeriod();
        entry.setStudyPeriod(studyPeriod);
        entry.setLoadFromDate(defaultFromDate(targetYear, studyPeriod));
        entry.setLoadToDate(defaultToDate(targetYear, studyPeriod));
        entry.setOrphaned(false);
        entry.setDismissalAdjusted(false);
        entry.setContinuityStatus(ContinuityStatus.OK);
        return entry;
    }

    private CurriculumModule resolveContinuityModule(ManualLoadEntry source,
                                                     Map<Long, CurriculumModule> sourceModulesById,
                                                     CurriculumPlanEntry targetCurriculum) {
        if (source.getCurriculumModuleId() == null) return null;
        CurriculumModule sourceModule = sourceModulesById.get(source.getCurriculumModuleId());
        if (sourceModule == null || !targetCurriculum.isModularSystem()) return null;
        return targetCurriculum.getModules().stream()
                .filter(module -> Objects.equals(module.getModuleOrder(), sourceModule.getModuleOrder())
                        || module.getModuleName().equalsIgnoreCase(sourceModule.getModuleName()))
                .findFirst()
                .orElse(null);
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
