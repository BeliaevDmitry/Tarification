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

    @Query("""
            select m from ManualLoadEntry m
             where m.academicYear = :academicYear
               and lower(m.numberSchoolBuilding) = lower(:numberSchoolBuilding)
               and m.classId in (
                   select cl.id from ClassroomLeadershipEntry cl
                    where cl.academicYear = :academicYear
                      and lower(cl.numberSchoolBuilding) = lower(:numberSchoolBuilding)
                      and lower(trim(cl.campusAddress)) = lower(trim(:campusAddress))
               )
            """)
    java.util.List<ManualLoadEntry> findAllByAcademicYearAndBuildingAddress(@Param("academicYear") String academicYear,
                                                                            @Param("numberSchoolBuilding") String numberSchoolBuilding,
                                                                            @Param("campusAddress") String campusAddress);
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
    @Query("""
            delete from ManualLoadEntry m
             where m.academicYear = :academicYear
               and lower(m.numberSchoolBuilding) = lower(:numberSchoolBuilding)
               and m.classId in (
                   select cl.id from ClassroomLeadershipEntry cl
                    where cl.academicYear = :academicYear
                      and lower(cl.numberSchoolBuilding) = lower(:numberSchoolBuilding)
                      and lower(trim(cl.campusAddress)) = lower(trim(:campusAddress))
               )
            """)
    void deleteByAcademicYearAndBuildingAddress(@Param("academicYear") String academicYear,
                                                @Param("numberSchoolBuilding") String numberSchoolBuilding,
                                                @Param("campusAddress") String campusAddress);

    @Modifying
    @Query("delete from ManualLoadEntry m where lower(m.numberSchoolBuilding) in :codes")
    void deleteByBuildingCodes(@Param("codes") java.util.Collection<String> codes);

    @Modifying
    @Query("update ManualLoadEntry m set m.subjectName = :newName where lower(m.subjectName) = lower(:oldName)")
    int renameSubjectEverywhere(@Param("oldName") String oldName, @Param("newName") String newName);

}
