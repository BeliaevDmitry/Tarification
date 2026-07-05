package org.school.personalLoad.dto;

import java.time.LocalDate;
import java.util.List;

public final class MckoDtos {
    private MckoDtos() {}

    public record CertificateRow(
            Long id,
            Long teacherId,
            String teacherFio,
            String mckoSubject,
            String examType,
            LocalDate diagnosticDate,
            LocalDate expiresAt,
            String level,
            boolean published,
            String source,
            String comment,
            boolean hasScan,
            String status,
            String warning
    ) {}

    public record SubjectMappingRow(Long id, String mckoSubject, Long subjectId, String subjectName) {}

    public record ImportResult(int totalRows, int importedRows, int skippedRows, List<String> warnings) {}

    public record EligibilityRow(
            Long teacherId,
            String teacherFio,
            Long subjectId,
            String subjectName,
            String status,
            String message,
            String level,
            LocalDate diagnosticDate,
            LocalDate expiresAt
    ) {}
}
