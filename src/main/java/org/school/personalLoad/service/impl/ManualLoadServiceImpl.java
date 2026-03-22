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
import org.springframework.stereotype.Service;

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

    @Override
    public ManualLoadEntry create(ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(request);
        return manualLoadEntryRepository.save(entity);
    }

    @Override
    public List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) {
        List<ManualLoadEntry> entries = requests.stream().map(this::toEntity).toList();
        return manualLoadEntryRepository.saveAll(entries);
    }

    @Override
    public List<ManualLoadEntry> findAll() {
        return manualLoadEntryRepository.findAll();
    }

    @Override
    public void clearAll() {
        manualLoadEntryRepository.deleteAll();
    }

    @Override
    public ManualLoadProcessResult processCurrentManualLoad() {
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAll();
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
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setFioTeacher(request.getFioTeacher().trim());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setLoad(request.getLoad());
        entity.setGroupNameEducationalPlan(request.getGroupNameEducationalPlan());
        entity.setGroupLoad(request.getGroupLoad());
        entity.setEducationLevel(request.getEducationLevel());
        entity.setStudyPeriod(request.getStudyPeriod() == null ? StudyPeriod.YEAR : request.getStudyPeriod());
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        return entity;
    }

    private CurriculumPlanEntry validateAgainstCurriculum(ManualLoadEntry entry) {
        CurriculumPlanEntry rule = curriculumPlanService
                .findRule(entry.getNumberSchoolBuilding().trim(),
                        ClassNameNormalizer.normalize(entry.getClassName()),
                        entry.getSubjectName().trim(),
                        entry.getEducationLevel(),
                        entry.getStudyPeriod() == null ? StudyPeriod.YEAR : entry.getStudyPeriod())
                .orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                        ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel() + ", period=" + entry.getStudyPeriod()));

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
}
