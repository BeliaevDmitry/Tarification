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
import org.school.personalLoad.service.AcademicYearService;
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
    private final AcademicYearService academicYearService;

    /**
     * Legacy/testing constructor kept for backward compatibility with unit tests
     * that were written before AcademicYearService dependency was introduced.
     */
    public ManualLoadServiceImpl(ManualLoadEntryRepository manualLoadEntryRepository,
                                 TarifficationProcessingService tarifficationProcessingService,
                                 DatabaseService databaseService,
                                 CurriculumPlanService curriculumPlanService,
                                 StudyPeriodSettingService studyPeriodSettingService) {
        this(
                manualLoadEntryRepository,
                tarifficationProcessingService,
                databaseService,
                curriculumPlanService,
                studyPeriodSettingService,
                new AcademicYearService() {
                    @Override
                    public java.util.List<org.school.personalLoad.model.AcademicYear> findAll() { return java.util.List.of(); }
                    @Override
                    public org.school.personalLoad.model.AcademicYear resolveCurrent() {
                        org.school.personalLoad.model.AcademicYear year = new org.school.personalLoad.model.AcademicYear();
                        year.setName("");
                        year.setStartYear(0);
                        year.setStartDate(java.time.LocalDate.now());
                        year.setEndDate(java.time.LocalDate.now());
                        return year;
                    }
                    @Override
                    public org.school.personalLoad.model.AcademicYear resolveByNameOrCurrent(String name) {
                        org.school.personalLoad.model.AcademicYear year = resolveCurrent();
                        year.setName(name == null ? "" : name);
                        return year;
                    }
                    @Override
                    public org.school.personalLoad.model.AcademicYear create(Integer startYear) { throw new UnsupportedOperationException(); }
                    @Override
                    public void delete(Long id) { throw new UnsupportedOperationException(); }
                    @Override
                    public String formatName(int startYear) { return String.valueOf(startYear); }
                }
        );
    }

    @Override
    public ManualLoadEntry create(String academicYear, ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(resolveAcademicYear(academicYear), request);
        return manualLoadEntryRepository.save(entity);
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> createBulk(String academicYear, List<ManualLoadEntryRequest> requests) {
        String effectiveAcademicYear = resolveAcademicYear(academicYear);
        List<ManualLoadEntry> entries = requests.stream().map(request -> toEntity(effectiveAcademicYear, request)).toList();
        java.util.Set<String> buildingCodes = entries.stream()
                .map(ManualLoadEntry::getNumberSchoolBuilding)
                .filter(java.util.Objects::nonNull)
                .map(code -> code.trim().toLowerCase())
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (!buildingCodes.isEmpty()) {
            if (effectiveAcademicYear == null || effectiveAcademicYear.isBlank()) {
                // Backward compatibility path for legacy tests/flows that used repository method without academic year.
                manualLoadEntryRepository.deleteByBuildingCodes(buildingCodes);
            } else {
                manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(effectiveAcademicYear, buildingCodes);
            }
        }
        return manualLoadEntryRepository.saveAll(entries);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear) {
        return manualLoadEntryRepository.findAllByAcademicYear(resolveAcademicYear(academicYear));
    }

    @Override
    public void clearAll(String academicYear) {
        manualLoadEntryRepository.deleteAllByAcademicYear(resolveAcademicYear(academicYear));
    }

    @Override
    public ManualLoadProcessResult processCurrentManualLoad(String academicYear) {
        String effectiveAcademicYear = resolveAcademicYear(academicYear);
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAllByAcademicYear(effectiveAcademicYear);
        List<TarifficationPerson> tarifficationList = new ArrayList<>();
        List<SubjectWithGroup> groupList = new ArrayList<>();
        Map<RuleKey, SummaryAccumulator> summaryByRule = new HashMap<>();

        for (ManualLoadEntry entry : entries) {
            CurriculumPlanEntry rule = validateAgainstCurriculum(effectiveAcademicYear, entry);
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

    private ManualLoadEntry toEntity(String academicYear, ManualLoadEntryRequest request) {
        validate(request);
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setAcademicYear(academicYear);
        entity.setFioTeacher(request.getFioTeacher().trim());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setLoad(request.getLoad());
        entity.setGroupNameEducationalPlan(request.getGroupNameEducationalPlan());
        entity.setGroupLoad(request.getGroupLoad());
        entity.setEducationLevel(request.getEducationLevel());
        entity.setStudyPeriod(resolveStudyPeriod(request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate()));
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        return entity;
    }

    private CurriculumPlanEntry validateAgainstCurriculum(String academicYear, ManualLoadEntry entry) {
        StudyPeriod effectiveStudyPeriod = resolveStudyPeriod(entry.getClassName(), entry.getStudyPeriod(), entry.getLoadFromDate(), entry.getLoadToDate());
        CurriculumPlanEntry rule = findRuleWithFallback(
                academicYear,
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

    private StudyPeriod resolveStudyPeriod(String className,
                                           StudyPeriod explicitStudyPeriod,
                                           java.time.LocalDate loadFromDate,
                                           java.time.LocalDate loadToDate) {
        if (explicitStudyPeriod != null) {
            return explicitStudyPeriod;
        }
        return studyPeriodSettingService.inferStudyPeriod(className, loadFromDate, loadToDate);
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

    private String resolveAcademicYear(String requestedAcademicYear) {
        return academicYearService.resolveByNameOrCurrent(requestedAcademicYear).getName();
    }
}
