package org.school.personalLoad.repository;

import org.school.personalLoad.model.SchoolBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SchoolBuildingRepository extends JpaRepository<SchoolBuilding, Long> {
    default Optional<SchoolBuilding> findByCode(String code) {
        return findFirstByCodeIgnoreCaseOrderByIdAsc(code);
    }

    Optional<SchoolBuilding> findFirstByCodeIgnoreCaseOrderByIdAsc(String code);
    List<SchoolBuilding> findAllByCodeIgnoreCase(String code);

    void deleteById(Long id);
}
