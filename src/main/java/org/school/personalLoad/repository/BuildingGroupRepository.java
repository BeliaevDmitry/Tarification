package org.school.personalLoad.repository;

import org.school.personalLoad.model.BuildingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BuildingGroupRepository extends JpaRepository<BuildingGroup, Long> {
    Optional<BuildingGroup> findByCodeIgnoreCase(String code);
}
