package org.school.personalLoad.repository;

import org.school.personalLoad.model.AcademicYearConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYearConfig, Long> {
    Optional<AcademicYearConfig> findByCode(String code);
    boolean existsByCode(String code);
}
