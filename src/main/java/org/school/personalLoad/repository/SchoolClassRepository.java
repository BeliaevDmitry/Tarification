package org.school.personalLoad.repository;

import org.school.personalLoad.model.SchoolClassEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClassEntry, Long> {
    Optional<SchoolClassEntry> findByAcademicYearAndClassName(String academicYear, String className);
    List<SchoolClassEntry> findAllByAcademicYearAndParallel(String academicYear, Integer parallel);
}
