package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
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
        String normalizedClassName = ClassNameNormalizer.normalize(request.getClassName());

        CurriculumPlanEntry entity = repository
                .findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPart(
                        request.getNumberSchoolBuilding().trim(),
                        normalizedClassName,
                        request.getSubjectName().trim(),
                        request.getEducationLevel(),
                        curriculumPart
                )
                .orElseGet(CurriculumPlanEntry::new);

        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(normalizedClassName);
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? request.getSubgroupCount() : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setCurriculumPart(curriculumPart);
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

        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? request.getSubgroupCount() : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setCurriculumPart(request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart());
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
    public Optional<CurriculumPlanEntry> findRule(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel) {
        return repository.findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevel(
                numberSchoolBuilding,
                ClassNameNormalizer.normalize(className),
                subjectName,
                educationLevel
        );
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
        if (request.getPlannedHours() == null || request.getPlannedHours() <= 0) {
            throw new IllegalArgumentException("plannedHours must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required");
        }
        if (request.isSubgroupRequired() && (request.getSubgroupCount() == null || request.getSubgroupCount() < 2)) {
            throw new IllegalArgumentException("subgroupCount must be >= 2 when subgroupRequired=true");
        }
    }
}
