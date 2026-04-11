package org.school.personalLoad.repository;

import org.school.personalLoad.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanEntryRepository extends JpaRepository<CurriculumPlanEntry, Long> {
    Optional<CurriculumPlanEntry> findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(String academicYear, String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod, Long studyPeriodSettingId);
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
            String academicYear,
            String numberSchoolBuilding,
            String className,
            String subjectName,
            EducationLevel educationLevel,
            CurriculumPart curriculumPart,
            StudyPeriod studyPeriod
    );

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, CurriculumStage stage, String className, String subjectName, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearAndStage(String academicYear, CurriculumStage stage);
    List<CurriculumPlanEntry> findAllByAcademicYear(String academicYear);

    List<CurriculumPlanEntry> findAllByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);
    void deleteByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);

    @Deprecated
    default Optional<CurriculumPlanEntry> findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod, Long studyPeriodSettingId) {
        return findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId("", numberSchoolBuilding, className, subjectName, educationLevel, curriculumPart, studyPeriod, studyPeriodSettingId);
    }

    @Deprecated
    default Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod) {
        return findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod("", numberSchoolBuilding, className, subjectName, educationLevel, curriculumPart, studyPeriod);
    }

    @Deprecated
    default Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod) {
        return findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse("", numberSchoolBuilding, className, subjectName, educationLevel, studyPeriod);
    }

    @Deprecated
    default Optional<CurriculumPlanEntry> findFirstByClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod) {
        return findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse("", className, subjectName, educationLevel, studyPeriod);
    }
}
