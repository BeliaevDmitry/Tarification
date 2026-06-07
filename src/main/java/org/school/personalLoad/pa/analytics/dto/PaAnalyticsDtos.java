package org.school.personalLoad.pa.analytics.dto;

import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;

import java.time.LocalDate;
import java.util.List;

public final class PaAnalyticsDtos {
    private PaAnalyticsDtos() {
    }

    public record ReportAnalysisListItem(Long reportVersionId,
                                         String academicYear,
                                         String subjectName,
                                         String className,
                                         String teacherFio,
                                         String workType,
                                         LocalDate workDate,
                                         String level,
                                         Integer studentsTotal,
                                         Integer studentsWithResult,
                                         Double avgPercent,
                                         Double avgMark,
                                         Double successPercent,
                                         Double qualityPercent,
                                         Integer problemTasksCount,
                                         Integer problemTopicsCount,
                                         Boolean needsReview,
                                         PaAnalysisStatus analysisStatus,
                                         String analysisMessage) {
    }

    public record ReportAnalysisDetails(ReportAnalysisListItem summary,
                                        List<StudentResultRow> students,
                                        List<TaskResultRow> tasks) {
    }


    public record RebuildAllResult(String status,
                                   String academicYear,
                                   Integer processed,
                                   Integer failed) {
    }


    public record TeacherSummaryRow(String teacherFio,
                                    List<String> subjects,
                                    List<String> classes,
                                    Integer reportsCount,
                                    Integer studentsWithResult,
                                    Double avgPercent,
                                    Double avgMark,
                                    Double successPercent,
                                    Double qualityPercent,
                                    Integer problemTasksCount,
                                    Integer problemTopicsCount,
                                    Integer needsReviewCount,
                                    Double paPerformanceScore,
                                    Integer paPerformanceMark,
                                    Double vsokoDynamicScore,
                                    Integer vsokoDynamicMark,
                                    String vsokoDynamicStatus) {
    }

    public record TeacherReportDetailRow(Long reportVersionId,
                                         String subjectName,
                                         String className,
                                         String workType,
                                         LocalDate workDate,
                                         String level,
                                         Integer studentsTotal,
                                         Integer studentsWithResult,
                                         Double avgPercent,
                                         Double avgMark,
                                         Double successPercent,
                                         Double qualityPercent,
                                         Integer problemTasksCount,
                                         Integer problemTopicsCount,
                                         Boolean needsReview,
                                         PaAnalysisStatus analysisStatus,
                                         String analysisMessage) {
    }

    public record TeacherDetailsResponse(TeacherSummaryRow teacherSummary,
                                         List<TeacherReportDetailRow> reports) {
    }

    public record StudentResultRow(String studentFio,
                                   String presenceStatus,
                                   String variantName,
                                   Double totalScore,
                                   Double maxScore,
                                   Double percent,
                                   Integer mark,
                                   PaStudentResultStatus rowStatus,
                                   Boolean possibleOtherSubgroup) {
    }

    public record TaskResultRow(Integer taskNo,
                                String topic,
                                String skill,
                                String taskKind,
                                Double maxScore,
                                Double avgScore,
                                Double avgPercent,
                                Long below50Count,
                                Long emptyCount,
                                String status) {
    }
}
