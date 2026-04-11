package org.school.personalLoad.repository;

import org.school.personalLoad.model.SchoolBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolBuildingRepository extends JpaRepository<SchoolBuilding, Long> {
    Optional<SchoolBuilding> findByCode(String code);

    void deleteByCode(String code);
}
