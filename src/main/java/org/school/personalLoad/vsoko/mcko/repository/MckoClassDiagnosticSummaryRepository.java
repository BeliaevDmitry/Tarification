package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.MckoClassDiagnosticSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MckoClassDiagnosticSummaryRepository extends JpaRepository<MckoClassDiagnosticSummary, Long> {
    Optional<MckoClassDiagnosticSummary> findByFingerprint(String fingerprint);
    List<MckoClassDiagnosticSummary> findAllByAcademicYear(String academicYear);
}
