package org.school.personalLoad.oge.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class OgeDtos {
    public record ImportFileResult(String fileName, boolean success, String message, int records) {}
    public record ImportLogRow(String fileName, boolean success, String message, int records, LocalDateTime createdAt) {}

    public record GiaVersionView(Long id, String sourceFileName, LocalDateTime uploadedAt, int participants) {}

    public record GiaParticipantView(String className, String fullName, String snils, int examCount, List<String> selectedSubjects) {}

    public record GiaChangesResponse(List<GiaChangeItem> changes) {}

    public record GiaMismatchRow(String type,
                                 String className,
                                 String fioGia,
                                 String fioContingent,
                                 String documentGia,
                                 String documentContingent,
                                 String reason) {}

    public record GiaMismatchResponse(List<GiaMismatchRow> rows, String infoMessage) {}

    public record GiaChangeItem(String type, String key, String wasValue, String becameValue) {}

    public record GiaStatsResponse(List<String> subjects,
                                   List<GiaClassStatsRow> classes,
                                   Map<String, Integer> totalsBySubject,
                                   Map<Integer, Integer> examCountDistribution) {}

    public record GiaClassStatsRow(String className, Map<String, Integer> counts) {}

    public record ScoreScaleRow(int score, Map<String, Integer> gradesBySubject) {}
    public record EvaluationRow(String subject, List<Integer> maxScores) {}

    public record WorkResultRow(String className,
                                String fullName,
                                String subject,
                                Integer score,
                                Integer grade,
                                boolean expectedByGia,
                                String status,
                                String teacherFio,
                                boolean needsTeacherBinding,
                                boolean needsManualStudentMatch,
                                String workSource,
                                String workType,
                                String workDate) {}

    public record WorkStatsRow(String className, String subject, int count2, int count3, int count4, int count5) {}

    public record WorkDatasetResponse(List<WorkResultRow> results,
                                      List<WorkResultRow> missing,
                                      List<WorkStatsRow> statistics,
                                      List<String> errors) {}

    public record TeacherBindingRow(String className,
                                    String fullName,
                                    String subject,
                                    String teacherFio) {}

    public record TeacherBindingUpdate(String className,
                                       String fullName,
                                       String subject,
                                       String teacherFio) {}
}
