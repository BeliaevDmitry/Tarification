package org.school.personalLoad.repository.contingent;

import org.school.personalLoad.model.contingent.ContingentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContingentSnapshotRepository extends JpaRepository<ContingentSnapshot, Long> {
    List<ContingentSnapshot> findAllByAcademicYearOrderBySnapshotDateDescImportedAtDesc(String academicYear);
    Optional<ContingentSnapshot> findByAcademicYearAndSnapshotDate(String academicYear, LocalDate snapshotDate);
}

