package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContingentSnapshotRepository extends JpaRepository<ContingentSnapshot, Long> {
    List<ContingentSnapshot> findAllByAcademicYearOrderBySnapshotDateDescImportedAtDesc(String academicYear);

    Optional<ContingentSnapshot> findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(String academicYear);

    Optional<ContingentSnapshot> findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(String academicYear, LocalDate snapshotDate);
}
