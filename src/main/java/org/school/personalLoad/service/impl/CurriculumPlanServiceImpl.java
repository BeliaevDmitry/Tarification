package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
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
        CurriculumPlanEntry entity = repository
                .findByClassNameAndSubjectNameAndEducationLevel(request.getClassName().trim(), request.getSubjectName().trim(), request.getEducationLevel())
                .orElseGet(CurriculumPlanEntry::new);

        entity.setClassName(request.getClassName().trim());
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? request.getSubgroupCount() : 0);
        entity.setEducationLevel(request.getEducationLevel());
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
    public Optional<CurriculumPlanEntry> findRule(String className, String subjectName, EducationLevel educationLevel) {
        return repository.findByClassNameAndSubjectNameAndEducationLevel(className, subjectName, educationLevel);
    }

    private void validate(CurriculumPlanEntryRequest request) {
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
