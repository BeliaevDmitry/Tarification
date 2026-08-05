package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MckoStudentResultRepository extends JpaRepository<MckoStudentResult, Long> {
    Optional<MckoStudentResult> findByFingerprint(String fingerprint);
    List<MckoStudentResult> findAllByStudentIdOrderByAcademicYearAscDiagnosticDateAsc(Long studentId);
    List<MckoStudentResult> findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(String academicYear);
    List<MckoStudentResult> findAllByAcademicYearAndClassNameOrderBySubjectNameAscStudentFioSnapshotAsc(String academicYear, String className);
    List<MckoStudentResult> findAllBySourceFileId(Long sourceFileId);
}
