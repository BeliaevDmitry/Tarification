package org.school.personalLoad.repository;

import org.school.personalLoad.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanEntryRepository extends JpaRepository<CurriculumPlanEntry, Long> {
    Optional<CurriculumPlanEntry> findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod, Long studyPeriodSettingId);

    Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, CurriculumStage stage, String className, String subjectName, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearAndStage(String academicYear, CurriculumStage stage);
}
