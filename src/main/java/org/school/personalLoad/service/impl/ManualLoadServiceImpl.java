package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadPlanFactSummary;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.ManualLoadService;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ManualLoadServiceImpl implements ManualLoadService {

    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final DatabaseService databaseService;
    private final CurriculumPlanService curriculumPlanService;
    private final CurrentUserService currentUserService;
    private final SchoolBuildingRepository buildingRepository;
    private final AuditService auditService;

    @Override
    public ManualLoadEntry create(ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(request);
        ensureBuildingAccess(entity.getNumberSchoolBuilding());
        ManualLoadEntry saved = manualLoadEntryRepository.save(entity);
        auditService.log(ActionType.CREATE, "ManualLoad", saved.getId(), null, saved, "Manual load entry created");
        return saved;
    }

    @Override
    public List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) {
        List<ManualLoadEntry> entries = requests.stream().map(this::toEntity).peek(entry -> ensureBuildingAccess(entry.getNumberSchoolBuilding())).toList();
        List<ManualLoadEntry> saved = manualLoadEntryRepository.saveAll(entries);
        auditService.log(ActionType.CREATE, "ManualLoad", null, null, saved, "Manual load bulk import");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualLoadEntry> findAll() {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            return buildingRepository.findByHeadUserId(userId)
                    .map(building -> manualLoadEntryRepository.findAllByNumberSchoolBuilding(building.getCode()))
                    .orElse(List.of());
        }
        return manualLoadEntryRepository.findAll();
    }

    @Override
    public void clearAll() {
        List<ManualLoadEntry> oldValue = manualLoadEntryRepository.findAll();
        manualLoadEntryRepository.deleteAll();
        auditService.log(ActionType.DELETE, "ManualLoad", null, oldValue, null, "All manual load entries removed");
    }

    @Override
    public ManualLoadProcessResult processCurrentManualLoad() {
        List<ManualLoadEntry> entries = findAll();
        List<TarifficationPerson> tarifficationList = new ArrayList<>();
        List<SubjectWithGroup> groupList = new ArrayList<>();
        Map<RuleKey, SummaryAccumulator> summaryByRule = new HashMap<>();

        for (ManualLoadEntry entry : entries) {
            CurriculumPlanEntry rule = validateAgainstCurriculum(entry);
            int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();

            RuleKey key = new RuleKey(rule.getClassName(), rule.getSubjectName(), rule.getEducationLevel());
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
                            summary.plannedHours - summary.actualHours
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
        ManualLoadProcessResult result = new ManualLoadProcessResult("ok", tarifficationList.size(), summaries);
        auditService.log(ActionType.UPDATE, "ManualLoad", null, null, result, "Manual load processed");
        return result;
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
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        return entity;
    }

    private CurriculumPlanEntry validateAgainstCurriculum(ManualLoadEntry entry) {
        CurriculumPlanEntry rule = curriculumPlanService
                .findRule(entry.getNumberSchoolBuilding().trim(), ClassNameNormalizer.normalize(entry.getClassName()), entry.getSubjectName().trim(), entry.getEducationLevel())
                .orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                        ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel()));

        int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();
        if (effectiveLoad > rule.getPlannedHours()) {
            throw new IllegalArgumentException("Load exceeds planned hours for curriculum rule");
        }

        if (rule.isSubgroupRequired()) {
            if (entry.getGroupNameEducationalPlan() == null || entry.getGroupNameEducationalPlan().isBlank()) {
                throw new IllegalArgumentException("groupNameEducationalPlan is required because subgroupRequired=true in curriculum");
            }
        }

        return rule;
    }

    private void ensureBuildingAccess(String buildingCode) {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            boolean allowed = buildingRepository.findByHeadUserId(userId)
                    .map(building -> building.getCode().equalsIgnoreCase(buildingCode))
                    .orElse(false);
            if (!allowed) {
                throw new IllegalArgumentException("You can modify only your building load");
            }
        }
    }

    private static class SummaryAccumulator {
        private final int plannedHours;
        private int actualHours;

        private SummaryAccumulator(int plannedHours) {
            this.plannedHours = plannedHours;
            this.actualHours = 0;
        }

        private void addActualHours(int hours) {
            this.actualHours += hours;
        }
    }

    private static class RuleKey {
        private final String className;
        private final String subjectName;
        private final org.school.personalLoad.model.EducationLevel educationLevel;

        private RuleKey(String className, String subjectName, org.school.personalLoad.model.EducationLevel educationLevel) {
            this.className = className;
            this.subjectName = subjectName;
            this.educationLevel = educationLevel;
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
                    && educationLevel == ruleKey.educationLevel;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(className, subjectName, educationLevel);
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
