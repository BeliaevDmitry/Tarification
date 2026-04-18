package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurriculumPlanServiceImpl implements CurriculumPlanService {

    private final CurriculumPlanEntryRepository repository;
    private final StudyPeriodSettingRepository studyPeriodSettingRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;

    @Override
    public CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        StudyPeriodSetting rule = resolveRule(request);
        rule = normalizeYearRuleIfNeeded(request, curriculumPart, rule);
        String normalizedClassName = ClassNameNormalizer.normalize(request.getClassName());

        CurriculumPlanEntry entity = repository
                .findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                        request.getAcademicYear(),
                        request.getNumberSchoolBuilding().trim(),
                        normalizedClassName,
                        request.getSubjectName().trim(),
                        request.getEducationLevel(),
                        curriculumPart,
                        rule.getStudyPeriod(),
                        rule.getId()
                )
                .orElseGet(CurriculumPlanEntry::new);

        applyValues(entity, request, curriculumPart, rule);
        return repository.save(entity);
    }

    @Override
    public List<CurriculumPlanEntry> upsertBulk(List<CurriculumPlanEntryRequest> requests) {
        List<CurriculumPlanEntry> result = new ArrayList<>();
        for (CurriculumPlanEntryRequest request : requests) {
            result.add(upsert(request));
        }
        return result;
    }

    @Override
    public List<CurriculumPlanEntry> findAll(String academicYear) {
        return repository.findAllByAcademicYear(academicYear);
    }

    @Override
    public List<CurriculumPlanEntry> findAll(String academicYear, String numberSchoolBuilding) {
        if (numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            return findAll(academicYear);
        }
        return repository.findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(academicYear, numberSchoolBuilding.trim());
    }

    @Override
    public void clearAll(String academicYear) {
        repository.deleteAllByAcademicYear(academicYear);
    }

    @Override
    public CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPlanEntry entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum entry not found: " + id));
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        StudyPeriodSetting rule = resolveRule(request);
        rule = normalizeYearRuleIfNeeded(request, curriculumPart, rule);
        applyValues(entity, request, curriculumPart, rule);
        return repository.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Curriculum entry not found: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    public Optional<CurriculumPlanEntry> findRule(String academicYear,
                                                  String numberSchoolBuilding,
                                                  String className,
                                                  String subjectName,
                                                  EducationLevel educationLevel,
                                                  StudyPeriod studyPeriod) {
        String normalizedClass = ClassNameNormalizer.normalize(className);
        String normalizedSubject = subjectName == null ? "" : subjectName.trim();
        StudyPeriod effectiveStudyPeriod = studyPeriod == null ? StudyPeriod.YEAR : studyPeriod;

        Optional<CurriculumPlanEntry> exactRule = repository.findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                academicYear,
                numberSchoolBuilding,
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );

        if (exactRule.isPresent()) {
            return exactRule;
        }

        return repository.findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                academicYear,
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );
    }

    private StudyPeriodSetting resolveRule(CurriculumPlanEntryRequest request) {
        if (request.getStudyPeriodSettingId() != null) {
            StudyPeriodSetting byId = studyPeriodSettingRepository.findById(request.getStudyPeriodSettingId())
                    .orElseThrow(() -> new IllegalArgumentException("Период обучения не найден: " + request.getStudyPeriodSettingId()));
            Integer parallel = ClassNameNormalizer.extractParallel(request.getClassName());
            if (parallel != null && (parallel < byId.getParallelFrom() || parallel > byId.getParallelTo())) {
                throw new IllegalArgumentException("Период не подходит для выбранного класса");
            }
            return byId;
        }
        return studyPeriodSettingService.resolveRuleForClassAndPeriod(request.getAcademicYear(), request.getClassName(), request.getStudyPeriod());
    }

    private StudyPeriodSetting normalizeYearRuleIfNeeded(CurriculumPlanEntryRequest request,
                                                         CurriculumPart curriculumPart,
                                                         StudyPeriodSetting resolvedRule) {
        if (resolvedRule.getStudyPeriod() == StudyPeriod.YEAR) {
            return resolvedRule;
        }
        StudyPeriod oppositePeriod = resolvedRule.getStudyPeriod() == StudyPeriod.H1 ? StudyPeriod.H2 : StudyPeriod.H1;
        Optional<CurriculumPlanEntry> opposite = repository.findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
                request.getAcademicYear(),
                request.getNumberSchoolBuilding().trim(),
                ClassNameNormalizer.normalize(request.getClassName()),
                request.getSubjectName().trim(),
                request.getEducationLevel(),
                curriculumPart,
                oppositePeriod
        );
        if (opposite.isEmpty()) {
            return resolvedRule;
        }
        if (request.getPlannedHours() == null || opposite.get().getPlannedHours() == null) {
            return resolvedRule;
        }
        if (request.getPlannedHours().compareTo(opposite.get().getPlannedHours()) != 0) {
            return resolvedRule;
        }
        repository.delete(opposite.get());
        try {
            return studyPeriodSettingService.resolveRuleForClassAndPeriod(request.getAcademicYear(), request.getClassName(), StudyPeriod.YEAR);
        } catch (Exception ignored) {
            return resolvedRule;
        }
    }

    private void applyValues(CurriculumPlanEntry entity,
                             CurriculumPlanEntryRequest request,
                             CurriculumPart curriculumPart,
                             StudyPeriodSetting rule) {
        entity.setAcademicYear(request.getAcademicYear());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? 2 : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setSubgroup1Hours(request.isSubgroupRequired() ? request.getSubgroup1Hours() : null);
        entity.setSubgroup1EducationLevel(request.isSubgroupRequired() ? request.getSubgroup1EducationLevel() : null);
        entity.setSubgroup2Hours(request.isSubgroupRequired() ? request.getSubgroup2Hours() : null);
        entity.setSubgroup2EducationLevel(request.isSubgroupRequired() ? request.getSubgroup2EducationLevel() : null);
        entity.setCurriculumPart(curriculumPart);
        entity.setStudyPeriod(rule.getStudyPeriod());
        entity.setStudyPeriodSettingId(rule.getId());
        entity.setMetaGroup(request.isMetaGroup());
    }

    private void validate(CurriculumPlanEntryRequest request) {
        if (request.getAcademicYear() == null || request.getAcademicYear().isBlank()) {
            throw new IllegalArgumentException("academicYear is required");
        }
        if (request.getNumberSchoolBuilding() == null || request.getNumberSchoolBuilding().isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getPlannedHours() == null || request.getPlannedHours().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("plannedHours must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required");
        }
        if (request.isSubgroupRequired()) {
            if (request.getSubgroup1Hours() == null || request.getSubgroup1Hours() <= 0) {
                throw new IllegalArgumentException("subgroup1Hours must be > 0 when subgroupRequired=true");
            }
            if (request.getSubgroup2Hours() == null || request.getSubgroup2Hours() <= 0) {
                throw new IllegalArgumentException("subgroup2Hours must be > 0 when subgroupRequired=true");
            }
            if (request.getSubgroup1EducationLevel() == null || request.getSubgroup2EducationLevel() == null) {
                throw new IllegalArgumentException("subgroup levels are required when subgroupRequired=true");
            }
        }
    }
}
