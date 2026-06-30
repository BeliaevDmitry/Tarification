package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentClassSizeOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContingentClassSizeOverrideRepository extends JpaRepository<ContingentClassSizeOverride, Long> {
    List<ContingentClassSizeOverride> findAllByAcademicYear(String academicYear);

    Optional<ContingentClassSizeOverride> findByAcademicYearAndClassName(String academicYear, String className);
}
