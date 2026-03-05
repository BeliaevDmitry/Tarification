package org.school.personalLoad.service;

import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;

import java.util.List;
import java.util.Optional;

public interface CurriculumPlanService {
    CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request);

    List<CurriculumPlanEntry> upsertBulk(List<CurriculumPlanEntryRequest> requests);

    List<CurriculumPlanEntry> findAll();

    void clearAll();

    CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request);

    void deleteById(Long id);

    Optional<CurriculumPlanEntry> findRule(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel);
}
