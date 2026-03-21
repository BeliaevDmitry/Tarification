package org.school.personalLoad.repository;

import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanEntryRepository extends JpaRepository<CurriculumPlanEntry, Long> {
    Optional<CurriculumPlanEntry> findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPart(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart);

    Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevel(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel);

    List<CurriculumPlanEntry> findAllByNumberSchoolBuilding(String numberSchoolBuilding);

    List<CurriculumPlanEntry> findAllByDeprecatedFalse();

    List<CurriculumPlanEntry> findAllByNumberSchoolBuildingAndDeprecatedFalse(String numberSchoolBuilding);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, String stage, String className, String subjectName, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearInAndStageInAndDeprecatedFalse(List<String> academicYears, List<String> stages);
}
