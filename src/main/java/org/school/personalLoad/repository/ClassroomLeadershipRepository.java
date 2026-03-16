package org.school.personalLoad.repository;

import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassroomLeadershipRepository extends JpaRepository<ClassroomLeadershipEntry, Long> {
    Optional<ClassroomLeadershipEntry> findByClassName(String className);

    boolean existsByNumberSchoolBuildingAndClassName(String numberSchoolBuilding, String className);
}
