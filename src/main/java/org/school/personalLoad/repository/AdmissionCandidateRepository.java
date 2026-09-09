package org.school.personalLoad.repository;

import org.school.personalLoad.model.AdmissionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdmissionCandidateRepository extends JpaRepository<AdmissionCandidate, Long> {
    List<AdmissionCandidate> findAllByAcademicYearOrderByProcessedAscUpdatedAtDescIdDesc(String academicYear);

    Optional<AdmissionCandidate> findByIdAndAcademicYear(Long id, String academicYear);
}
