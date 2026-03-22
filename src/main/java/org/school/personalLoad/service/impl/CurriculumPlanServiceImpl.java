package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurriculumPlanServiceImpl implements CurriculumPlanService {

    private final CurriculumPlanEntryRepository repository;

    @Override
    public CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        StudyPeriod studyPeriod = normalizedStudyPeriod(request);
        String normalizedClassName = ClassNameNormalizer.normalize(request.getClassName());

        CurriculumPlanEntry entity = repository
                .findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
                        request.getNumberSchoolBuilding().trim(),
                        normalizedClassName,
                        request.getSubjectName().trim(),
                        request.getEducationLevel(),
                        curriculumPart,
                        studyPeriod
                )
                .orElseGet(CurriculumPlanEntry::new);

        applyValues(entity, request, curriculumPart, studyPeriod);
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
    public List<CurriculumPlanEntry> findAll() {
        return repository.findAll();
    }

    @Override
    public void clearAll() {
        repository.deleteAll();
    }

    @Override
    public CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPlanEntry entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum entry not found: " + id));

        applyValues(entity, request, request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart(), normalizedStudyPeriod(request));
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
    public Optional<CurriculumPlanEntry> findRule(String numberSchoolBuilding,
                                                  String className,
                                                  String subjectName,
                                                  EducationLevel educationLevel,
                                                  StudyPeriod studyPeriod) {
        String normalizedClass = ClassNameNormalizer.normalize(className);
        String normalizedSubject = subjectName == null ? "" : subjectName.trim();
        StudyPeriod effectiveStudyPeriod = studyPeriod == null ? StudyPeriod.YEAR : studyPeriod;

        Optional<CurriculumPlanEntry> exactRule = repository.findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                numberSchoolBuilding,
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );

        if (exactRule.isPresent()) {
            return exactRule;
        }

        return repository.findFirstByClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );
    }

    private void applyValues(CurriculumPlanEntry entity,
                             CurriculumPlanEntryRequest request,
                             CurriculumPart curriculumPart,
                             StudyPeriod studyPeriod) {
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
        entity.setStudyPeriod(studyPeriod);
    }

    private StudyPeriod normalizedStudyPeriod(CurriculumPlanEntryRequest request) {
        Integer parallel = ClassNameNormalizer.extractParallel(request.getClassName());
        if (parallel != null && parallel >= 10) {
            return request.getStudyPeriod() == null ? StudyPeriod.H1 : request.getStudyPeriod();
        }
        return StudyPeriod.YEAR;
    }

    private void validate(CurriculumPlanEntryRequest request) {
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
