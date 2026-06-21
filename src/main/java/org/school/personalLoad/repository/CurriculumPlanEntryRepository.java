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
    List<CurriculumPlanEntry> findAllByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(String academicYear, String className, String subjectName, EducationLevel educationLevel, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(String academicYear, CurriculumStage stage, String className, String subjectName, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear,
            Long classId,
            String subjectName,
            EducationLevel educationLevel,
            StudyPeriod studyPeriod
    );

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear,
            Long classId,
            Long subjectId,
            EducationLevel educationLevel,
            StudyPeriod studyPeriod
    );
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassIdAndSubject_IdAndCurriculumPartAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear, Long classId, Long subjectId, CurriculumPart curriculumPart,
            EducationLevel educationLevel, StudyPeriod studyPeriod
    );
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndClassIdAndSubject_IdAndCurriculumPartAndStudyPeriodAndDeprecatedFalse(
            String academicYear, Long classId, Long subjectId, CurriculumPart curriculumPart, StudyPeriod studyPeriod);

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear,
            Long metaGroupId,
            String subjectName,
            EducationLevel educationLevel,
            StudyPeriod studyPeriod
    );

    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear,
            Long metaGroupId,
            Long subjectId,
            EducationLevel educationLevel,
            StudyPeriod studyPeriod
    );
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndCurriculumPartAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
            String academicYear, Long metaGroupId, Long subjectId, CurriculumPart curriculumPart,
            EducationLevel educationLevel, StudyPeriod studyPeriod
    );
    Optional<CurriculumPlanEntry> findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndCurriculumPartAndStudyPeriodAndDeprecatedFalse(
            String academicYear, Long metaGroupId, Long subjectId, CurriculumPart curriculumPart, StudyPeriod studyPeriod);

    List<CurriculumPlanEntry> findAllByAcademicYearAndStage(String academicYear, CurriculumStage stage);
    List<CurriculumPlanEntry> findAllByAcademicYear(String academicYear);
    List<CurriculumPlanEntry> findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(String academicYear, String numberSchoolBuilding);
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);
    boolean existsByBuildingGroup_Id(Long buildingGroupId);

    @Query(value = "select * from curriculum_plan_entry where meta_group_id = :metaGroupId", nativeQuery = true)
    List<CurriculumPlanEntry> findAllByMetaGroupId(@Param("metaGroupId") Long metaGroupId);

    @Modifying
    @Query(value = "delete from curriculum_plan_entry where meta_group_id = :metaGroupId", nativeQuery = true)
    void deleteByMetaGroupId(@Param("metaGroupId") Long metaGroupId);

    @Modifying
    @Query(value = "delete from curriculum_plan_entry where academic_year = :academicYear and class_id = :classId", nativeQuery = true)
    void deleteByAcademicYearAndClassId(@Param("academicYear") String academicYear, @Param("classId") Long classId);

    @Query(value = "select count(*) from curriculum_plan_entry where academic_year = :academicYear and class_id = :classId", nativeQuery = true)
    long countClassTails(@Param("academicYear") String academicYear,
                         @Param("classId") Long classId);

    void deleteAllByAcademicYear(String academicYear);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update curriculum_plan_entry
               set number_school_building = :numberSchoolBuilding,
                   building_group_id = :buildingGroupId,
                   class_name = :className
             where academic_year = :academicYear
               and class_id = :classId
            """, nativeQuery = true)
    int updateClassBuildingScope(@Param("academicYear") String academicYear,
                                 @Param("classId") Long classId,
                                 @Param("numberSchoolBuilding") String numberSchoolBuilding,
                                 @Param("buildingGroupId") Long buildingGroupId,
                                 @Param("className") String className);

}
