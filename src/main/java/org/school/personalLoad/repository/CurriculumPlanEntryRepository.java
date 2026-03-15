package org.school.personalLoad.repository;

import org.school.personalLoad.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanEntryRepository extends JpaRepository<CurriculumPlanEntry, Long> {
    Optional<CurriculumPlanEntry> findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPart(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart);

    Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndDeprecatedFalse(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel);

    Optional<CurriculumPlanEntry> findFirstByClassNameAndSubjectNameAndEducationLevelAndDeprecatedFalse(String className, String subjectName, EducationLevel educationLevel);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, CurriculumStage stage, String className, String subjectName, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearAndStage(String academicYear, CurriculumStage stage);
}
