package org.school.personalLoad.pa.dto;

import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaTaskKind;
import org.school.personalLoad.pa.model.PaWorkType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PaDtos {

    private PaDtos() {
    }

    public record ImportResult(String fileName, int importedSpecs, int importedTasks, List<String> subjects, List<String> parallels, List<String> warnings) {
    }

    public record SpecificationRow(Long id,
                                   String academicYear,
                                   String subjectName,
                                   PaScopeType scopeType,
                                   String scopeValue,
                                   PaLevel level,
                                   PaWorkType workType,
                                   LocalDate workDate,
                                   Integer grade5Percent,
                                   Integer grade4Percent,
                                   Integer grade3Percent,
                                   String teacherFio,
                                   String sourceFileName,
                                   Integer versionNo,
                                   boolean activeVersion,
                                   String pairKey) {
    }

    public record SpecificationTaskRow(Integer taskNo,
                                       String topic,
                                       String skill,
                                       PaTaskKind taskKind,
                                       Integer repeatFromTaskNo,
                                       Integer maxScore) {
    }

    public record SummaryCell(String subjectName,
                              String scopeValue,
                              PaLevel level,
                              boolean participates,
                              boolean hasEntrySpecification,
                              boolean hasExitSpecification) {
    }

    public record SummaryResponse(List<SummaryCell> primary, List<SummaryCell> secondary) {
    }

    public record ReportVersionRow(Long id,
                                   String academicYear,
                                   String subjectName,
                                   PaScopeType scopeType,
                                   String scopeValue,
                                   PaLevel level,
                                   PaWorkType workType,
                                   LocalDate workDate,
                                   Integer versionNo,
                                   boolean activeVersion,
                                   String status,
                                   String validationMessage,
                                   String sourceFileName,
                                   LocalDateTime createdAt,
                                   boolean downloadedAtLeastOnce,
                                   boolean uploadedBackSuccess) {
    }

    public record ReportUploadResult(String fileName,
                                     String status,
                                     String message,
                                     Integer versionNo,
                                     String subjectName,
                                     String scopeValue,
                                     PaWorkType workType) {
    }

    public record ReportFolderItem(Long reportVersionId,
                                   String subjectName,
                                   String parallel,
                                   String className,
                                   PaLevel level,
                                   String fileName,
                                   LocalDateTime createdAt) {
    }

    public record ReportWorkflowSummaryItem(String subjectName,
                                            String scopeValue,
                                            boolean hasGenerated,
                                            boolean hasDownloaded,
                                            boolean hasUploaded,
                                            Long latestGeneratedId,
                                            Long latestUploadedId) {
    }
}
