package org.school.personalLoad.repository;

import org.school.personalLoad.model.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    Optional<AcademicYear> findByName(String name);
    Optional<AcademicYear> findByStartYear(Integer startYear);
    List<AcademicYear> findAllByOrderByStartYearAsc();
}

