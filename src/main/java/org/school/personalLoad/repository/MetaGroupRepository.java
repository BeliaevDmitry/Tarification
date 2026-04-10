package org.school.personalLoad.repository;

import org.school.personalLoad.model.MetaGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaGroupRepository extends JpaRepository<MetaGroup, Long> {
    boolean existsByNumberSchoolBuildingAndParallelAndNameIgnoreCase(String numberSchoolBuilding, Integer parallel, String name);
}
