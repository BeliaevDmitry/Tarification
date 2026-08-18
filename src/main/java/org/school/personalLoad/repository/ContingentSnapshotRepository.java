package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContingentSnapshotRepository extends JpaRepository<ContingentSnapshot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select snapshot from ContingentSnapshot snapshot where snapshot.id = :id")
    Optional<ContingentSnapshot> findByIdForUpdate(@Param("id") Long id);

    List<ContingentSnapshot> findAllByAcademicYearOrderBySnapshotDateDescImportedAtDesc(String academicYear);

    Optional<ContingentSnapshot> findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(String academicYear);

    Optional<ContingentSnapshot> findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(String academicYear, LocalDate snapshotDate);

    Optional<ContingentSnapshot> findFirstByOrderByImportedAtDesc();
}
