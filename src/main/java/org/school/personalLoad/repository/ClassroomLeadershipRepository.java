package org.school.personalLoad.repository;

import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassroomLeadershipRepository extends JpaRepository<ClassroomLeadershipEntry, Long> {
    Optional<ClassroomLeadershipEntry> findByClassName(String className);
    Optional<ClassroomLeadershipEntry> findByAcademicYearAndClassName(String academicYear, String className);

    boolean existsByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);
    boolean existsByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);
    Optional<ClassroomLeadershipEntry> findByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);
    java.util.List<ClassroomLeadershipEntry> findAllByAcademicYearAndNumberSchoolBuildingAndClassName(String academicYear, String numberSchoolBuilding, String className);
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);


    java.util.List<ClassroomLeadershipEntry> findAllByAcademicYear(String academicYear);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update classroom_leadership_entry
           set building_group_id = (
               select bg.id
                 from building_group bg
                where bg.code = :numberSchoolBuilding
           )
         where id = :id
        """, nativeQuery = true)
    int updateBuildingGroupById(@Param("id") Long id,
                                @Param("numberSchoolBuilding") String numberSchoolBuilding);
}
