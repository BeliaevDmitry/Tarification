package org.school.personalLoad.repository;

import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualLoadEntryRepository extends JpaRepository<ManualLoadEntry, Long> {
    boolean existsByFioTeacherIgnoreCase(String fioTeacher);

    java.util.List<ManualLoadEntry> findByFioTeacherIgnoreCase(String fioTeacher);

    @Modifying
    @Query("delete from ManualLoadEntry m where m.academicYear = :academicYear and lower(m.numberSchoolBuilding) in :codes")
    void deleteByAcademicYearAndBuildingCodes(@Param("academicYear") String academicYear,
                                              @Param("codes") java.util.Collection<String> codes);

    java.util.List<ManualLoadEntry> findAllByAcademicYear(String academicYear);
    java.util.List<ManualLoadEntry> findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(String academicYear, String numberSchoolBuilding);


    @Query(value = """
            select distinct m.*
              from manual_load_entry m
              left join classroom_leadership_entry c on c.id = m.class_id
              left join meta_group mg on mg.id = m.meta_group_id
             where m.academic_year = :academicYear
               and (
                    (m.class_id is not null and c.school_building_id = :schoolBuildingId)
                 or (m.meta_group_id is not null and mg.school_building_id = :schoolBuildingId)
               )
            """, nativeQuery = true)
    java.util.List<ManualLoadEntry> findAllByAcademicYearAndSchoolBuildingId(@Param("academicYear") String academicYear,
                                                                             @Param("schoolBuildingId") Long schoolBuildingId);
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);
    @Query(value = "select count(*) from manual_load_entry where academic_year = :academicYear and class_id = :classId", nativeQuery = true)
    long countClassTails(@Param("academicYear") String academicYear,
                         @Param("classId") Long classId);

    void deleteAllByAcademicYear(String academicYear);

    @Modifying
    @Query(value = "delete from manual_load_entry where meta_group_id = :metaGroupId", nativeQuery = true)
    void deleteByMetaGroupId(@Param("metaGroupId") Long metaGroupId);

    @Modifying
    @Query("delete from ManualLoadEntry m where m.academicYear = :academicYear and m.classId in :classIds")
    void deleteByAcademicYearAndClassIds(@Param("academicYear") String academicYear,
                                         @Param("classIds") java.util.Collection<Long> classIds);

    @Modifying
    @Query("delete from ManualLoadEntry m where m.academicYear = :academicYear and m.metaGroupId in :metaGroupIds")
    void deleteByAcademicYearAndMetaGroupIds(@Param("academicYear") String academicYear,
                                             @Param("metaGroupIds") java.util.Collection<Long> metaGroupIds);


    @Modifying
    @Query(value = """
            delete from manual_load_entry m
             where m.academic_year = :academicYear
               and (
                    m.class_id in (
                        select c.id
                          from classroom_leadership_entry c
                         where c.academic_year = :academicYear
                           and c.school_building_id = :schoolBuildingId
                    )
                 or m.meta_group_id in (
                        select mg.id
                          from meta_group mg
                         where mg.school_building_id = :schoolBuildingId
                    )
               )
            """, nativeQuery = true)
    void deleteByAcademicYearAndSchoolBuildingId(@Param("academicYear") String academicYear,
                                                 @Param("schoolBuildingId") Long schoolBuildingId);

    @Modifying
    @Query("delete from ManualLoadEntry m where lower(m.numberSchoolBuilding) in :codes")
    void deleteByBuildingCodes(@Param("codes") java.util.Collection<String> codes);

    @Modifying
    @Query("update ManualLoadEntry m set m.subjectName = :newName where lower(m.subjectName) = lower(:oldName)")
    int renameSubjectEverywhere(@Param("oldName") String oldName, @Param("newName") String newName);

}
