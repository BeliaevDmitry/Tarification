package org.school.personalLoad.repository;

import org.school.personalLoad.model.SubjectArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectAreaRepository extends JpaRepository<SubjectArea, Long> {
    Optional<SubjectArea> findByNameIgnoreCase(String name);
}
