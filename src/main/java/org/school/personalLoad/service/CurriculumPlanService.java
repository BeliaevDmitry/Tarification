package org.school.personalLoad.service;

import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;

import java.util.List;
import java.util.Optional;

public interface CurriculumPlanService {
    CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request);

    List<CurriculumPlanEntry> upsertBulk(List<CurriculumPlanEntryRequest> requests);

    List<CurriculumPlanEntry> findAll(String academicYear);

    default List<CurriculumPlanEntry> findAll(String academicYear, String numberSchoolBuilding) {
        return findAll(academicYear);
    }

    void clearAll(String academicYear);

    CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request);

    void deleteById(Long id);

    Optional<CurriculumPlanEntry> findRule(String academicYear,
                                           String numberSchoolBuilding,
                                           String className,
                                           String subjectName,
                                           EducationLevel educationLevel,
                                           StudyPeriod studyPeriod);
}
