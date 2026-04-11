package org.school.personalLoad.repository;

import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualLoadEntryRepository extends JpaRepository<ManualLoadEntry, Long> {
    boolean existsByAcademicYearAndFioTeacherIgnoreCase(String academicYear, String fioTeacher);

    java.util.List<ManualLoadEntry> findByAcademicYearAndFioTeacherIgnoreCase(String academicYear, String fioTeacher);
    java.util.List<ManualLoadEntry> findAllByAcademicYear(String academicYear);

    @Modifying
    @Query("delete from ManualLoadEntry m where m.academicYear = :academicYear and lower(m.numberSchoolBuilding) in :codes")
    void deleteByAcademicYearAndBuildingCodes(@Param("academicYear") String academicYear, @Param("codes") java.util.Collection<String> codes);

    void deleteAllByAcademicYear(String academicYear);

    @Deprecated
    default boolean existsByFioTeacherIgnoreCase(String fioTeacher) {
        return existsByAcademicYearAndFioTeacherIgnoreCase("", fioTeacher);
    }

    @Deprecated
    default java.util.List<ManualLoadEntry> findByFioTeacherIgnoreCase(String fioTeacher) {
        return findByAcademicYearAndFioTeacherIgnoreCase("", fioTeacher);
    }

    @Deprecated
    default void deleteByBuildingCodes(@Param("codes") java.util.Collection<String> codes) {
        deleteByAcademicYearAndBuildingCodes("", codes);
    }
}
