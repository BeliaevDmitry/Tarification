package org.school.personalLoad.repository;

import org.school.personalLoad.model.MckoCertificate;
import org.school.personalLoad.model.MckoCertificateSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MckoCertificateRepository extends JpaRepository<MckoCertificate, Long> {
    List<MckoCertificate> findAllByTeacherId(Long teacherId);

    Optional<MckoCertificate> findFirstByTeacherIdAndMckoSubjectIgnoreCaseAndDiagnosticDateAndExamTypeIgnoreCaseAndSource(
            Long teacherId, String mckoSubject, LocalDate diagnosticDate, String examType, MckoCertificateSource source);
}
