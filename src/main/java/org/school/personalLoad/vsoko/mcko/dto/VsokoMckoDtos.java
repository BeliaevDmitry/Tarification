package org.school.personalLoad.vsoko.mcko.dto;

import org.school.personalLoad.vsoko.mcko.model.MckoFileStatus;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentLinkStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class VsokoMckoDtos {
    private VsokoMckoDtos() {}

    public record FileStatusRow(Long id, Long batchId, String fileName, String fileKind, long fileSize,
                                String detectedAcademicYear, String detectedWorkDate, String detectedSubject,
                                MckoFileStatus status, String reason, int totalRows, int importedRows,
                                int skippedRows, LocalDateTime processedAt) {}

    public record ImportResponse(Long batchId, int filesTotal, int filesProcessed, int filesFailed,
                                 int rowsImported, List<FileStatusRow> files) {}

    public record ResultRow(Long id, Long studentId, String studentFio, String studentCode,
                            MckoStudentLinkStatus linkStatus, String linkMessage, String className,
                            String subjectName, LocalDate diagnosticDate, String academicYear,
                            String schoolName, String classLevel, String cityLevel, String resultType,
                            String variantName, Double score, Double percent, Integer mark,
                            String masteryLevel, Double section1Percent, Double section2Percent,
                            Double section3Percent, Long teacherId, String teacherFio,
                            String sourceFileName) {}

    public record FilterOptions(List<String> academicYears, List<String> classes, List<String> subjects,
                                List<String> teachers, List<String> linkStatuses) {}

    public record TeacherAssignmentRow(Long id, String academicYear, String className, String subjectName,
                                       Long teacherId, String teacherFio) {}

    public record TeacherAssignmentRequest(Long id, String academicYear, String className,
                                           String subjectName, Long teacherId) {}

    public record StudentSearchRow(Long id, String currentFullName, List<String> knownNames,
                                   LocalDate birthDate, String currentClass, int resultsCount) {}

    public record TimelineRow(String source, Long sourceId, String academicYear, String className,
                              String subjectName, LocalDate date, String workType, Double score,
                              Double maxScore, Double percent, Integer mark, String teacherFio,
                              String status) {}

    public record StudentSummary(Long studentId, String currentFullName, List<String> knownNames,
                                 List<TimelineRow> results) {}

    public record ClassSubjectComparison(String subjectName, int mckoCount, Double mckoAveragePercent,
                                         Double mckoAverageMark, int paCount, Double paAveragePercent,
                                         Double paAverageMark) {}

    public record ClassSummary(String academicYear, String className, List<ClassSubjectComparison> subjects) {}

    public record ReconcileResponse(int linked, int ambiguous, int notFound) {}

    public record InterviewRequest(String academicYear, List<Long> teacherIds) {}
}
