package org.school.personalLoad.repository;

import org.school.personalLoad.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanEntryRepository extends JpaRepository<CurriculumPlanEntry, Long> {
    Optional<CurriculumPlanEntry> findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod, Long studyPeriodSettingId);
    Optional<CurriculumPlanEntry> findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(String academicYear, String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, CurriculumPart curriculumPart, StudyPeriod studyPeriod, Long studyPeriodSettingId);
    Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
            String numberSchoolBuilding,
            String className,
            String subjectName,
            EducationLevel educationLevel,
            CurriculumPart curriculumPart,
            StudyPeriod studyPeriod
    );
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
            String academicYear,
            String numberSchoolBuilding,
            String className,
            String subjectName,
            EducationLevel educationLevel,
            CurriculumPart curriculumPart,
            StudyPeriod studyPeriod
    );

    Optional<CurriculumPlanEntry> findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, CurriculumStage stage, String className, String subjectName, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearAndStage(String academicYear, CurriculumStage stage);
    List<CurriculumPlanEntry> findAllByAcademicYear(String academicYear);
    List<CurriculumPlanEntry> findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(String academicYear, String numberSchoolBuilding);
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);

    List<CurriculumPlanEntry> findAllByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);
    List<CurriculumPlanEntry> findAllByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);

    void deleteByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);
    void deleteByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);


    @Modifying
    @Query(value = "delete from curriculum_plan_entry where academic_year = :academicYear and class_id = :classId", nativeQuery = true)
    void deleteByAcademicYearAndClassId(@Param("academicYear") String academicYear, @Param("classId") Long classId);

    @Query(value = "select count(*) from curriculum_plan_entry where academic_year = :academicYear and (class_id = :classId or (lower(number_school_building) = lower(:numberSchoolBuilding) and lower(class_name) = lower(:className)))", nativeQuery = true)
    long countClassTails(@Param("academicYear") String academicYear,
                         @Param("classId") Long classId,
                         @Param("numberSchoolBuilding") String numberSchoolBuilding,
                         @Param("className") String className);

    void deleteAllByAcademicYear(String academicYear);

    @Modifying
    @Query("update CurriculumPlanEntry c set c.subjectName = :newName where lower(c.subjectName) = lower(:oldName)")
    int renameSubjectEverywhere(@Param("oldName") String oldName, @Param("newName") String newName);

    @Modifying
    @Query("""
            update CurriculumPlanEntry c
               set c.className = :newClassName,
                   c.numberSchoolBuilding = :newBuilding
             where c.academicYear = :academicYear
               and lower(trim(c.className)) = lower(trim(:oldClassName))
            """)
    int renameClassEverywhere(@Param("academicYear") String academicYear,
                              @Param("oldClassName") String oldClassName,
                              @Param("newClassName") String newClassName,
                              @Param("newBuilding") String newBuilding);

}
