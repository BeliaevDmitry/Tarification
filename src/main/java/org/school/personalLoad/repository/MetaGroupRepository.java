package org.school.personalLoad.repository;

import org.school.personalLoad.model.MetaGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetaGroupRepository extends JpaRepository<MetaGroup, Long> {
    List<MetaGroup> findAllByAcademicYearOrderByNumberSchoolBuildingAscParallelAscNameAsc(String academicYear);
    boolean existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(String academicYear, String numberSchoolBuilding, Integer parallel, String name, String classType);
    boolean existsByNumberSchoolBuildingIgnoreCase(String numberSchoolBuilding);
    boolean existsBySchoolBuilding_Id(Long schoolBuildingId);
    boolean existsByBuildingGroup_Id(Long buildingGroupId);
}
