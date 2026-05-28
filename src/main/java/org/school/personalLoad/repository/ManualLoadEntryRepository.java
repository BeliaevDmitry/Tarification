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
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);
    void deleteByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);

    void deleteAllByAcademicYear(String academicYear);

    @Modifying
    @Query("delete from ManualLoadEntry m where lower(m.numberSchoolBuilding) in :codes")
    void deleteByBuildingCodes(@Param("codes") java.util.Collection<String> codes);

    @Modifying
    @Query("update ManualLoadEntry m set m.subjectName = :newName where lower(m.subjectName) = lower(:oldName)")
    int renameSubjectEverywhere(@Param("oldName") String oldName, @Param("newName") String newName);
}
