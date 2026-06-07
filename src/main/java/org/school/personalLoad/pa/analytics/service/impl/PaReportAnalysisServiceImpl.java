package org.school.personalLoad.pa.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.school.personalLoad.pa.analytics.model.PaSpecificationMatchSource;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisService;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaReportAnalysisServiceImpl implements PaReportAnalysisService {

    private static final String PA_REPORT_STORAGE_DIR = "pa-reports";
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final PaReportVersionRepository reportVersionRepository;
    private final PaReportAnalysisSummaryRepository summaryRepository;
    private final PaReportStudentResultRepository studentResultRepository;
    private final PaReportTaskResultRepository taskResultRepository;

    @Override
    @Transactional
    public void analyzeReport(Long reportVersionId) {
        PaReportVersion version = findReportVersion(reportVersionId);
        LocalDateTime now = LocalDateTime.now();
        PaReportAnalysisSummary summary = findOrCreateSummary(version);
        summary.setAnalysisStartedAt(now);
        summary.setAnalysisFinishedAt(now);
        summary.setAnalysisStatus(PaAnalysisStatus.NOT_ANALYZED);
        summary.setAnalysisMessage("Каркас аналитики создан. Парсинг отчёта пока не выполняется.");
        summary.setNeedsReview(false);
        summaryRepository.save(summary);
    }

    @Override
    @Transactional
    public void saveAnalysisError(Long reportVersionId, Exception exception) {
        PaReportVersion version = findReportVersion(reportVersionId);
        LocalDateTime now = LocalDateTime.now();
        PaReportAnalysisSummary summary = findOrCreateSummary(version);
        summary.setAnalysisStartedAt(summary.getAnalysisStartedAt() == null ? now : summary.getAnalysisStartedAt());
        summary.setAnalysisFinishedAt(now);
        summary.setAnalysisStatus(PaAnalysisStatus.ERROR);
        summary.setNeedsReview(true);
        summary.setAnalysisMessage(buildErrorMessage(exception));

        try {
            Path directory = Path.of(PA_REPORT_STORAGE_DIR, safeYear(version.getAcademicYear()), "analysis-logs");
            Files.createDirectories(directory);
            String fileName = "analysis_error_report_" + reportVersionId + "_" + now.format(LOG_TIMESTAMP_FORMAT) + ".txt";
            Path logPath = directory.resolve(fileName);
            Files.writeString(logPath, buildErrorLog(version, exception), StandardCharsets.UTF_8);
            summary.setAnalysisErrorLogPath(logPath.toString());
            summary.setAnalysisErrorLogFileName(fileName);
        } catch (Exception logException) {
            summary.setAnalysisMessage(summary.getAnalysisMessage()
                    + "; не удалось записать txt-лог: " + buildErrorMessage(logException));
        }

        summaryRepository.save(summary);
    }

    @Override
    @Transactional
    public int rebuildAll(String academicYear) {
        List<PaReportVersion> versions = reportVersionRepository.findAll().stream()
                .filter(version -> Objects.equals(version.getAcademicYear(), academicYear))
                .toList();
        versions.forEach(version -> analyzeReport(version.getId()));
        return versions.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PaAnalyticsDtos.ReportAnalysisDetails getDetails(Long reportVersionId) {
        PaAnalyticsDtos.ReportAnalysisListItem summary = summaryRepository.findByReportVersionId(reportVersionId)
                .map(this::toListItem)
                .orElse(null);
        List<PaAnalyticsDtos.StudentResultRow> students = studentResultRepository
                .findAllByReportVersionIdOrderByStudentFioAsc(reportVersionId)
                .stream()
                .map(this::toStudentRow)
                .toList();
        List<PaAnalyticsDtos.TaskResultRow> tasks = toTaskRows(
                taskResultRepository.findAllByReportVersionIdOrderByTaskNoAsc(reportVersionId)
        );
        return new PaAnalyticsDtos.ReportAnalysisDetails(summary, students, tasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaAnalyticsDtos.ReportAnalysisListItem> getReports(String academicYear,
                                                                   String subjectName,
                                                                   String teacherFio,
                                                                   String className,
                                                                   String workType,
                                                                   Boolean onlyProblems,
                                                                   Boolean onlyNeedsReview) {
        return summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc(academicYear)
                .stream()
                .filter(summary -> matches(summary.getSubjectName(), subjectName))
                .filter(summary -> matches(summary.getTeacherFio(), teacherFio))
                .filter(summary -> matches(summary.getClassName(), className))
                .filter(summary -> matches(summary.getWorkType(), workType))
                .filter(summary -> !Boolean.TRUE.equals(onlyNeedsReview) || summary.isNeedsReview())
                .filter(summary -> !Boolean.TRUE.equals(onlyProblems) || positive(summary.getProblemTasksCount()) || positive(summary.getProblemTopicsCount()))
                .map(this::toListItem)
                .toList();
    }

    private PaReportVersion findReportVersion(Long reportVersionId) {
        return reportVersionRepository.findById(reportVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Версия отчёта ПА не найдена: " + reportVersionId));
    }

    private PaReportAnalysisSummary findOrCreateSummary(PaReportVersion version) {
        PaReportAnalysisSummary summary = summaryRepository.findByReportVersionId(version.getId())
                .orElseGet(PaReportAnalysisSummary::new);
        fillSummaryFromVersion(summary, version);
        return summary;
    }

    private void fillSummaryFromVersion(PaReportAnalysisSummary summary, PaReportVersion version) {
        summary.setReportVersionId(version.getId());
        summary.setAcademicYear(version.getAcademicYear());
        summary.setSubjectName(version.getSubjectName());
        summary.setClassName(version.getScopeValue());
        summary.setTeacherFio(version.getTeacherFio());
        summary.setWorkType(version.getWorkType() == null ? null : version.getWorkType().name());
        summary.setWorkDate(version.getWorkDate());
        summary.setLevel(version.getLevel() == null ? null : version.getLevel().name());
        summary.setStudentsTotal(version.getReportedStudentsCount());
        summary.setStudentsWithResult(version.getAcceptedResultsCount());
        summary.setStudentsAbsent(0);
        summary.setStudentsEmpty(0);
        summary.setPossibleOtherSubgroupCount(0);
        summary.setProblemTasksCount(0);
        summary.setProblemTopicsCount(0);
        summary.setSpecificationFound(false);
        summary.setSpecificationSource(PaSpecificationMatchSource.NOT_FOUND);
    }

    private PaAnalyticsDtos.ReportAnalysisListItem toListItem(PaReportAnalysisSummary summary) {
        return new PaAnalyticsDtos.ReportAnalysisListItem(
                summary.getReportVersionId(),
                summary.getAcademicYear(),
                summary.getSubjectName(),
                summary.getClassName(),
                summary.getTeacherFio(),
                summary.getWorkType(),
                summary.getWorkDate(),
                summary.getLevel(),
                summary.getStudentsTotal(),
                summary.getStudentsWithResult(),
                summary.getAvgPercent(),
                summary.getAvgMark(),
                summary.getSuccessPercent(),
                summary.getQualityPercent(),
                summary.getProblemTasksCount(),
                summary.getProblemTopicsCount(),
                summary.isNeedsReview(),
                summary.getAnalysisStatus(),
                summary.getAnalysisMessage()
        );
    }

    private PaAnalyticsDtos.StudentResultRow toStudentRow(PaReportStudentResult row) {
        return new PaAnalyticsDtos.StudentResultRow(
                row.getStudentFio(),
                row.getPresenceStatus(),
                row.getVariantName(),
                row.getTotalScore(),
                row.getMaxScore(),
                row.getPercent(),
                row.getMark(),
                row.getRowStatus(),
                row.isPossibleOtherSubgroup()
        );
    }

    private List<PaAnalyticsDtos.TaskResultRow> toTaskRows(List<PaReportTaskResult> rows) {
        Map<TaskKey, List<PaReportTaskResult>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> new TaskKey(row.getTaskNo(), row.getTopic(), row.getSkill(), row.getTaskKind(), row.getMaxScore())));
        return grouped.entrySet().stream()
                .map(entry -> toTaskRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PaAnalyticsDtos.TaskResultRow::taskNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private PaAnalyticsDtos.TaskResultRow toTaskRow(TaskKey key, List<PaReportTaskResult> rows) {
        double avgScore = rows.stream()
                .filter(row -> row.getScore() != null)
                .mapToDouble(PaReportTaskResult::getScore)
                .average()
                .orElse(0D);
        double avgPercent = rows.stream()
                .filter(row -> row.getPercent() != null)
                .mapToDouble(PaReportTaskResult::getPercent)
                .average()
                .orElse(0D);
        long below50Count = rows.stream()
                .filter(row -> row.getPercent() != null && row.getPercent() < 50D)
                .count();
        long emptyCount = rows.stream()
                .filter(PaReportTaskResult::isEmpty)
                .count();
        String status = below50Count > 0 ? "PROBLEM" : "OK";
        return new PaAnalyticsDtos.TaskResultRow(
                key.taskNo(), key.topic(), key.skill(), key.taskKind(), key.maxScore(),
                avgScore, avgPercent, below50Count, emptyCount, status
        );
    }

    private boolean matches(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private String safeYear(String academicYear) {
        return academicYear == null || academicYear.isBlank() ? "unknown" : academicYear.replace("/", "-");
    }

    private String buildErrorMessage(Exception exception) {
        if (exception == null) {
            return "Неизвестная ошибка анализа";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getName();
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }

    private String buildErrorLog(PaReportVersion version, Exception exception) {
        return "reportVersionId: " + version.getId() + System.lineSeparator()
                + "academicYear: " + nvl(version.getAcademicYear()) + System.lineSeparator()
                + "subjectName: " + nvl(version.getSubjectName()) + System.lineSeparator()
                + "className: " + nvl(version.getScopeValue()) + System.lineSeparator()
                + "teacherFio: " + nvl(version.getTeacherFio()) + System.lineSeparator()
                + "workType: " + (version.getWorkType() == null ? "" : version.getWorkType().name()) + System.lineSeparator()
                + "level: " + (version.getLevel() == null ? "" : version.getLevel().name()) + System.lineSeparator()
                + "sourceFileName: " + nvl(version.getSourceFileName()) + System.lineSeparator()
                + "sourceFilePath: " + nvl(version.getSourceFilePath()) + System.lineSeparator()
                + "error: " + buildErrorMessage(exception) + System.lineSeparator()
                + "stacktrace:" + System.lineSeparator()
                + stackTrace(exception);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String stackTrace(Exception exception) {
        if (exception == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private record TaskKey(Integer taskNo, String topic, String skill, String taskKind, Double maxScore) {
    }
}
