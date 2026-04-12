package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadPlanFactSummary;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.ManualLoadService;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualLoadServiceImpl implements ManualLoadService {

    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final DatabaseService databaseService;
    private final CurriculumPlanService curriculumPlanService;
    private final StudyPeriodSettingService studyPeriodSettingService;

    @Override
    public ManualLoadEntry create(ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(request);
        return manualLoadEntryRepository.save(entity);
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) {
        java.util.Set<String> explicitAcademicYears = requests.stream()
                .filter(java.util.Objects::nonNull)
                .map(ManualLoadEntryRequest::getAcademicYear)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(year -> !year.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        List<ManualLoadEntry> entries = requests.stream().map(this::toEntity).toList();
        java.util.Set<String> buildingCodes = entries.stream()
                .map(ManualLoadEntry::getNumberSchoolBuilding)
                .filter(java.util.Objects::nonNull)
                .map(code -> code.trim().toLowerCase())
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (!buildingCodes.isEmpty()) {
            if (explicitAcademicYears.size() == 1) {
                String academicYear = explicitAcademicYears.iterator().next();
                manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(academicYear, buildingCodes);
            } else {
                manualLoadEntryRepository.deleteByBuildingCodes(buildingCodes);
            }
        }
        return manualLoadEntryRepository.saveAll(entries);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear) {
        return manualLoadEntryRepository.findAllByAcademicYear(academicYear);
    }

    @Override
    public void clearAll(String academicYear) {
        manualLoadEntryRepository.deleteAllByAcademicYear(academicYear);
    }

    @Override
    public ManualLoadProcessResult processCurrentManualLoad(String academicYear) {
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAllByAcademicYear(academicYear);
        List<TarifficationPerson> tarifficationList = new ArrayList<>();
        List<SubjectWithGroup> groupList = new ArrayList<>();
        Map<RuleKey, SummaryAccumulator> summaryByRule = new HashMap<>();

        for (ManualLoadEntry entry : entries) {
            CurriculumPlanEntry rule = validateAgainstCurriculum(entry);
            int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();

            RuleKey key = new RuleKey(rule.getClassName(), rule.getSubjectName(), rule.getEducationLevel(), rule.getStudyPeriod());
            summaryByRule.computeIfAbsent(key, k -> new SummaryAccumulator(rule.getPlannedHours()))
                    .addActualHours(effectiveLoad);

            TarifficationPerson person = new TarifficationPerson(
                    entry.getFioTeacher(),
                    entry.getNumberSchoolBuilding(),
                    entry.getSubjectName(),
                    entry.getClassName(),
                    entry.getLoad()
            );
            person.setGroupNameEducationalPlan(entry.getGroupNameEducationalPlan() != null
                    ? entry.getGroupNameEducationalPlan() : "");
            person.setGroupLoad(effectiveLoad);
            tarifficationList.add(person);
        }

        tarifficationList = tarifficationProcessingService.addingGroup(tarifficationList, groupList);
        tarifficationProcessingService.sortByFIO(tarifficationList);
        databaseService.compareAndSave(tarifficationList);

        List<ManualLoadPlanFactSummary> summaries = summaryByRule.entrySet().stream()
                .map(entry -> {
                    RuleKey key = entry.getKey();
                    SummaryAccumulator summary = entry.getValue();
                    return new ManualLoadPlanFactSummary(
                            key.className,
                            key.subjectName,
                            key.educationLevel,
                            summary.plannedHours,
                            summary.actualHours,
                            summary.plannedHours.subtract(summary.actualHours)
                    );
                })
                .sorted((a, b) -> {
                    int classCompare = a.getClassName().compareToIgnoreCase(b.getClassName());
                    if (classCompare != 0) {
                        return classCompare;
                    }
                    int subjectCompare = a.getSubjectName().compareToIgnoreCase(b.getSubjectName());
                    if (subjectCompare != 0) {
                        return subjectCompare;
                    }
                    return a.getEducationLevel().name().compareToIgnoreCase(b.getEducationLevel().name());
                })
                .toList();

        log.info("Ручная нагрузка обработана. Записей: {}, сводок: {}", tarifficationList.size(), summaries.size());
        return new ManualLoadProcessResult("ok", tarifficationList.size(), summaries);
    }

    private ManualLoadEntry toEntity(ManualLoadEntryRequest request) {
        validate(request);
        String effectiveAcademicYear = resolveAcademicYearOrDefault(request.getAcademicYear());
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setAcademicYear(effectiveAcademicYear);
        entity.setFioTeacher(request.getFioTeacher().trim());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setLoad(request.getLoad());
        entity.setGroupNameEducationalPlan(request.getGroupNameEducationalPlan());
        entity.setGroupLoad(request.getGroupLoad());
        entity.setEducationLevel(request.getEducationLevel());
        entity.setStudyPeriod(resolveStudyPeriod(effectiveAcademicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate()));
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        return entity;
    }

    private CurriculumPlanEntry validateAgainstCurriculum(ManualLoadEntry entry) {
        StudyPeriod effectiveStudyPeriod = resolveStudyPeriod(entry.getAcademicYear(), entry.getClassName(), entry.getStudyPeriod(), entry.getLoadFromDate(), entry.getLoadToDate());
        CurriculumPlanEntry rule = findRuleWithFallback(
                entry.getAcademicYear(),
                entry.getNumberSchoolBuilding().trim(),
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getEducationLevel(),
                effectiveStudyPeriod
        ).orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel() + ", period=" + effectiveStudyPeriod));

        int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();
        if (BigDecimal.valueOf(effectiveLoad).compareTo(rule.getPlannedHours()) > 0) {
            throw new IllegalArgumentException("Load exceeds planned hours for curriculum rule");
        }

        if (rule.isSubgroupRequired()) {
            if (entry.getGroupNameEducationalPlan() == null || entry.getGroupNameEducationalPlan().isBlank()) {
                throw new IllegalArgumentException("groupNameEducationalPlan is required because subgroupRequired=true in curriculum");
            }
        }

        return rule;
    }


    private java.util.Optional<CurriculumPlanEntry> findRuleWithFallback(String academicYear,
                                                                         String numberSchoolBuilding,
                                                                         String className,
                                                                         String subjectName,
                                                                         org.school.personalLoad.model.EducationLevel educationLevel,
                                                                         StudyPeriod effectiveStudyPeriod) {
        java.util.List<StudyPeriod> candidates = new java.util.ArrayList<>();
        candidates.add(effectiveStudyPeriod == null ? StudyPeriod.YEAR : effectiveStudyPeriod);
        candidates.add(StudyPeriod.YEAR);
        candidates.add(StudyPeriod.H1);
        candidates.add(StudyPeriod.H2);
        return candidates.stream()
                .distinct()
                .map(period -> curriculumPlanService.findRule(academicYear, numberSchoolBuilding,
                        ClassNameNormalizer.normalize(className),
                        subjectName.trim(),
                        educationLevel,
                        period))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }

    private StudyPeriod resolveStudyPeriod(String academicYear,
                                           String className,
                                           StudyPeriod explicitStudyPeriod,
                                           java.time.LocalDate loadFromDate,
                                           java.time.LocalDate loadToDate) {
        if (explicitStudyPeriod != null) {
            return explicitStudyPeriod;
        }
        return studyPeriodSettingService.inferStudyPeriod(academicYear, className, loadFromDate, loadToDate);
    }

    private static class SummaryAccumulator {
        private final BigDecimal plannedHours;
        private BigDecimal actualHours;

        private SummaryAccumulator(BigDecimal plannedHours) {
            this.plannedHours = plannedHours == null ? BigDecimal.ZERO : plannedHours;
            this.actualHours = BigDecimal.ZERO;
        }

        private void addActualHours(int hours) {
            this.actualHours = this.actualHours.add(BigDecimal.valueOf(hours));
        }
    }

    private static class RuleKey {
        private final String className;
        private final String subjectName;
        private final org.school.personalLoad.model.EducationLevel educationLevel;
        private final StudyPeriod studyPeriod;

        private RuleKey(String className, String subjectName, org.school.personalLoad.model.EducationLevel educationLevel, StudyPeriod studyPeriod) {
            this.className = className;
            this.subjectName = subjectName;
            this.educationLevel = educationLevel;
            this.studyPeriod = studyPeriod;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RuleKey ruleKey = (RuleKey) o;
            return className.equals(ruleKey.className)
                    && subjectName.equals(ruleKey.subjectName)
                    && educationLevel == ruleKey.educationLevel
                    && studyPeriod == ruleKey.studyPeriod;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(className, subjectName, educationLevel, studyPeriod);
        }
    }

    private void validate(ManualLoadEntryRequest request) {
        if (request.getFioTeacher() == null || request.getFioTeacher().isBlank()) {
            throw new IllegalArgumentException("fioTeacher is required");
        }
        if (request.getNumberSchoolBuilding() == null || request.getNumberSchoolBuilding().isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        if (request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (request.getLoad() == null || request.getLoad() <= 0) {
            throw new IllegalArgumentException("load must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required (BASIC or ADVANCED)");
        }
        if (request.getLoadFromDate() == null || request.getLoadToDate() == null) {
            throw new IllegalArgumentException("load period is required: loadFromDate and loadToDate");
        }
        if (request.getLoadFromDate().isAfter(request.getLoadToDate())) {
            throw new IllegalArgumentException("loadFromDate must be before or equal to loadToDate");
        }
    }

    private String resolveAcademicYearOrDefault(String value) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        java.time.LocalDate now = java.time.LocalDate.now();
        int start = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return start + "/" + (start + 1);
    }
}
