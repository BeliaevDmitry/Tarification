package org.school.personalLoad.pa.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.analytics.service.PaTeacherAnalyticsService;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaTeacherAnalyticsServiceImpl implements PaTeacherAnalyticsService {

    private static final String DYNAMIC_NOT_AVAILABLE = "NOT_AVAILABLE_NO_ENTRY_EXIT_PAIR";

    private final PaReportAnalysisSummaryRepository summaryRepository;
    private final PaReportStudentResultRepository studentResultRepository;
    private final PaReportTaskResultRepository taskResultRepository;
    private final PaReportVersionRepository reportVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PaAnalyticsDtos.TeacherSummaryRow> getTeacherSummaries(String academicYear,
                                                                       String subjectName,
                                                                       Boolean onlyNeedsReview) {
        List<PaReportAnalysisSummary> summaries = loadEligibleSummaries(academicYear, subjectName, onlyNeedsReview, null);
        Map<String, List<PaReportAnalysisSummary>> byTeacher = summaries.stream()
                .filter(summary -> summary.getTeacherFio() != null && !summary.getTeacherFio().isBlank())
                .collect(Collectors.groupingBy(summary -> normalizeTeacherKey(summary.getTeacherFio())));
        Map<Long, List<PaReportStudentResult>> studentsByReport = loadStudentsByReport(summaries);
        Map<Long, List<PaReportTaskResult>> tasksByReport = loadTasksByReport(summaries);
        return byTeacher.values().stream()
                .map(teacherSummaries -> toTeacherSummary(teacherSummaries, studentsByReport, tasksByReport, null))
                .sorted(Comparator.comparing(PaAnalyticsDtos.TeacherSummaryRow::teacherFio, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaAnalyticsDtos.TeacherDetailsResponse getTeacherDetails(String academicYear, String teacherFio) {
        List<PaReportAnalysisSummary> summaries = loadEligibleSummaries(academicYear, null, null, teacherFio);
        Map<Long, List<PaReportStudentResult>> studentsByReport = loadStudentsByReport(summaries);
        Map<Long, List<PaReportTaskResult>> tasksByReport = loadTasksByReport(summaries);
        PaAnalyticsDtos.TeacherSummaryRow teacherSummary = toTeacherSummary(summaries, studentsByReport, tasksByReport, teacherFio);
        List<PaAnalyticsDtos.TeacherReportDetailRow> reports = summaries.stream()
                .sorted(Comparator.comparing(PaReportAnalysisSummary::getWorkDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaReportAnalysisSummary::getSubjectName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(PaReportAnalysisSummary::getClassName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toReportDetail)
                .toList();
        return new PaAnalyticsDtos.TeacherDetailsResponse(teacherSummary, reports);
    }

    private List<PaReportAnalysisSummary> loadEligibleSummaries(String academicYear,
                                                                String subjectName,
                                                                Boolean onlyNeedsReview,
                                                                String teacherFio) {
        Map<Long, PaReportVersion> versionsById = reportVersionRepository.findAll().stream()
                .collect(Collectors.toMap(PaReportVersion::getId, Function.identity()));
        Set<Long> effectivelyActiveIds = effectivelyActiveAcceptedReportIds(versionsById.values().stream().toList());
        return summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc(academicYear)
                .stream()
                .filter(this::hasSuccessfulAnalysis)
                .filter(summary -> positive(summary.getStudentsWithResult()))
                .filter(summary -> matches(summary.getSubjectName(), subjectName))
                .filter(summary -> matches(summary.getTeacherFio(), teacherFio))
                .filter(summary -> !Boolean.TRUE.equals(onlyNeedsReview) || summary.isNeedsReview())
                .filter(summary -> isActiveAcceptedReport(summary, versionsById, effectivelyActiveIds))
                .toList();
    }

    private boolean hasSuccessfulAnalysis(PaReportAnalysisSummary summary) {
        return summary.getAnalysisStatus() == PaAnalysisStatus.SUCCESS
                || summary.getAnalysisStatus() == PaAnalysisStatus.WARNING;
    }

    private boolean hasReportFileLocator(PaReportVersion version) {
        return !isBlank(version.getSourceFilePath())
                || (!isBlank(version.getAcademicYear()) && !isBlank(version.getSourceFileName()));
    }

    private boolean isActiveAcceptedReport(PaReportAnalysisSummary summary, Map<Long, PaReportVersion> versionsById, Set<Long> effectivelyActiveIds) {
        PaReportVersion version = versionsById.get(summary.getReportVersionId());
        return version != null
                && effectivelyActiveIds.contains(version.getId())
                && "ACCEPTED".equalsIgnoreCase(nvl(version.getStatus()))
                && hasReportFileLocator(version)
                && !isBlank(version.getSubjectName())
                && !isBlank(version.getScopeValue())
                && !isBlank(summary.getSubjectName())
                && !isBlank(summary.getClassName())
                && !isBlank(summary.getTeacherFio());
    }

    private Set<Long> effectivelyActiveAcceptedReportIds(List<PaReportVersion> versions) {
        Map<String, Boolean> hasActiveByKey = versions.stream()
                .filter(version -> "ACCEPTED".equalsIgnoreCase(nvl(version.getStatus())))
                .filter(PaReportVersion::isActiveVersion)
                .collect(Collectors.toMap(this::reportReplacementKey, version -> true, (first, second) -> true));
        return versions.stream()
                .filter(version -> "ACCEPTED".equalsIgnoreCase(nvl(version.getStatus())))
                .filter(version -> version.getId() != null)
                .filter(version -> version.isActiveVersion() || !hasActiveByKey.containsKey(reportReplacementKey(version)))
                .map(PaReportVersion::getId)
                .collect(Collectors.toSet());
    }

    private String reportReplacementKey(PaReportVersion version) {
        return String.join("|",
                normalizeTeacherKey(version.getAcademicYear()),
                normalizeTeacherKey(version.getSubjectName()),
                version.getScopeType() == null ? "" : version.getScopeType().name(),
                normalizeTeacherKey(version.getScopeValue()),
                version.getLevel() == null ? "" : version.getLevel().name(),
                version.getWorkType() == null ? "" : version.getWorkType().name(),
                version.getWorkDate() == null ? "" : version.getWorkDate().toString(),
                normalizeTeacherKey(firstNonBlank(version.getTeacherFioNormalized(), version.getTeacherFio()))
        );
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private Map<Long, List<PaReportStudentResult>> loadStudentsByReport(List<PaReportAnalysisSummary> summaries) {
        List<Long> ids = summaries.stream()
                .map(PaReportAnalysisSummary::getReportVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentResultRepository.findAllByReportVersionIdIn(ids).stream()
                .collect(Collectors.groupingBy(PaReportStudentResult::getReportVersionId));
    }

    private Map<Long, List<PaReportTaskResult>> loadTasksByReport(List<PaReportAnalysisSummary> summaries) {
        List<Long> ids = summaries.stream()
                .map(PaReportAnalysisSummary::getReportVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return taskResultRepository.findAllByReportVersionIdIn(ids).stream()
                .collect(Collectors.groupingBy(PaReportTaskResult::getReportVersionId));
    }

    private PaAnalyticsDtos.TeacherSummaryRow toTeacherSummary(List<PaReportAnalysisSummary> summaries,
                                                               Map<Long, List<PaReportStudentResult>> studentsByReport,
                                                               Map<Long, List<PaReportTaskResult>> tasksByReport,
                                                               String fallbackTeacherFio) {
        String teacherFio = summaries.stream()
                .map(PaReportAnalysisSummary::getTeacherFio)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallbackTeacherFio);
        Set<String> subjects = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();
        int studentsWithResult = 0;
        double percentSum = 0D;
        int percentCount = 0;
        double markSum = 0D;
        int markCount = 0;
        int successCount = 0;
        int qualityCount = 0;
        int problemTasksCount = 0;
        int problemTopicsCount = 0;
        int needsReviewCount = 0;

        for (PaReportAnalysisSummary summary : summaries) {
            addIfPresent(subjects, summary.getSubjectName());
            addIfPresent(classes, summary.getClassName());
            problemTasksCount += safeInt(summary.getProblemTasksCount());
            problemTopicsCount += safeInt(summary.getProblemTopicsCount());
            if (summary.isNeedsReview()) {
                needsReviewCount++;
            }
            for (PaReportStudentResult student : studentsByReport.getOrDefault(summary.getReportVersionId(), List.of())) {
                if (student.getRowStatus() != PaStudentResultStatus.PRESENT_WITH_RESULT) {
                    continue;
                }
                studentsWithResult++;
                if (student.getPercent() != null) {
                    percentSum += student.getPercent();
                    percentCount++;
                }
                if (student.getMark() != null) {
                    markSum += student.getMark();
                    markCount++;
                    if (student.getMark() >= 3) {
                        successCount++;
                    }
                    if (student.getMark() >= 4) {
                        qualityCount++;
                    }
                }
            }
        }

        Double avgPercent = percentCount == 0 ? null : percentSum / percentCount;
        Double avgMark = markCount == 0 ? null : markSum / markCount;
        Double successPercent = studentsWithResult == 0 ? null : successCount * 100D / studentsWithResult;
        Double qualityPercent = studentsWithResult == 0 ? null : qualityCount * 100D / studentsWithResult;
        Double paPerformanceScore = calculateTeacherPerformanceScore(summaries, studentsByReport, tasksByReport);
        Double dynamicScore = calculateVsokoDynamic(summaries, tasksByReport);
        Integer dynamicMark = dynamicScore == null ? null : dynamicMark(dynamicScore);
        return new PaAnalyticsDtos.TeacherSummaryRow(
                teacherFio,
                new ArrayList<>(subjects),
                new ArrayList<>(classes),
                summaries.size(),
                studentsWithResult,
                avgPercent,
                avgMark,
                successPercent,
                qualityPercent,
                problemTasksCount,
                problemTopicsCount,
                needsReviewCount,
                paPerformanceScore,
                performanceMark(paPerformanceScore),
                dynamicScore,
                dynamicMark,
                dynamicScore == null ? DYNAMIC_NOT_AVAILABLE : "CALCULATED"
        );
    }

    private Double calculateTeacherPerformanceScore(List<PaReportAnalysisSummary> summaries,
                                                    Map<Long, List<PaReportStudentResult>> studentsByReport,
                                                    Map<Long, List<PaReportTaskResult>> tasksByReport) {
        List<PaReportAnalysisSummary> entries = summaries.stream()
                .filter(summary -> "ENTRY".equalsIgnoreCase(nvl(summary.getWorkType())))
                .toList();
        List<PaReportAnalysisSummary> exits = summaries.stream()
                .filter(summary -> "EXIT".equalsIgnoreCase(nvl(summary.getWorkType())))
                .toList();
        List<Double> studentScores = new ArrayList<>();
        for (PaReportAnalysisSummary exit : exits) {
            PaReportAnalysisSummary entry = latestMatchingEntry(entries, exit);
            if (entry == null) {
                studentScores.addAll(calculateExitOnlyStudentPerformanceScores(exit, studentsByReport, tasksByReport));
                continue;
            }
            studentScores.addAll(calculateStudentPerformanceScores(entry, exit, studentsByReport, tasksByReport));
        }
        return studentScores.isEmpty() ? null : studentScores.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private List<Double> calculateExitOnlyStudentPerformanceScores(PaReportAnalysisSummary exit,
                                                                   Map<Long, List<PaReportStudentResult>> studentsByReport,
                                                                   Map<Long, List<PaReportTaskResult>> tasksByReport) {
        Map<Long, List<PaReportTaskResult>> exitTasksByStudent = tasksByReport
                .getOrDefault(exit.getReportVersionId(), List.of())
                .stream()
                .filter(task -> task.getStudentResultId() != null)
                .collect(Collectors.groupingBy(PaReportTaskResult::getStudentResultId));
        List<Double> scores = new ArrayList<>();
        for (PaReportStudentResult exitStudent : studentsByReport.getOrDefault(exit.getReportVersionId(), List.of())) {
            if (!hasPresentResult(exitStudent)) {
                continue;
            }
            Double exitNewPercent = taskGroupPercent(
                    exitTasksByStudent.getOrDefault(exitStudent.getId(), List.of()),
                    "NEW"
            );
            Double availableExitPercent = exitNewPercent != null ? exitNewPercent : exitStudent.getPercent();
            if (availableExitPercent != null) {
                scores.add((double) currentPeriodMasteryMark(availableExitPercent));
            }
        }
        return scores;
    }

    private List<Double> calculateStudentPerformanceScores(PaReportAnalysisSummary entry,
                                                           PaReportAnalysisSummary exit,
                                                           Map<Long, List<PaReportStudentResult>> studentsByReport,
                                                           Map<Long, List<PaReportTaskResult>> tasksByReport) {
        Map<String, PaReportStudentResult> entryStudentsByFio = studentsByReport
                .getOrDefault(entry.getReportVersionId(), List.of())
                .stream()
                .filter(this::hasPresentResult)
                .filter(student -> !isBlank(student.getStudentFioNormalized()) || !isBlank(student.getStudentFio()))
                .collect(Collectors.toMap(
                        this::normalizedStudentKey,
                        Function.identity(),
                        (first, second) -> first
                ));
        Map<Long, List<PaReportTaskResult>> exitTasksByStudent = tasksByReport
                .getOrDefault(exit.getReportVersionId(), List.of())
                .stream()
                .filter(task -> task.getStudentResultId() != null)
                .collect(Collectors.groupingBy(PaReportTaskResult::getStudentResultId));
        List<Double> scores = new ArrayList<>();
        for (PaReportStudentResult exitStudent : studentsByReport.getOrDefault(exit.getReportVersionId(), List.of())) {
            if (!hasPresentResult(exitStudent)) {
                continue;
            }
            PaReportStudentResult entryStudent = entryStudentsByFio.get(normalizedStudentKey(exitStudent));
            if (entryStudent == null) {
                continue;
            }
            Double score = calculateStudentPerformanceScore(entryStudent, exitStudent, exitTasksByStudent.getOrDefault(exitStudent.getId(), List.of()));
            if (score != null) {
                scores.add(score);
            }
        }
        return scores;
    }

    private Double calculateStudentPerformanceScore(PaReportStudentResult entryStudent,
                                                    PaReportStudentResult exitStudent,
                                                    List<PaReportTaskResult> exitTasks) {
        Double entryPercent = entryStudent.getPercent();
        Integer entryMark = entryStudent.getMark();
        Double exitRepeatPercent = taskGroupPercent(exitTasks, "REPEAT");
        Double exitNewPercent = taskGroupPercent(exitTasks, "NEW");
        Integer exitMark = exitStudent.getMark();
        if (entryPercent == null || entryMark == null || exitRepeatPercent == null || exitNewPercent == null || exitMark == null) {
            return null;
        }
        return (previousPeriodProgressMark(entryPercent, exitRepeatPercent)
                + gapClosureMark(entryPercent, exitRepeatPercent)
                + currentPeriodMasteryMark(exitNewPercent)
                + markDynamicsMark(entryMark, exitMark)) / 4D;
    }

    private Double taskGroupPercent(List<PaReportTaskResult> tasks, String taskKind) {
        List<PaReportTaskResult> matchingTasks = tasks.stream()
                .filter(task -> taskKind.equalsIgnoreCase(nvl(task.getTaskKind())))
                .toList();
        double maxScore = matchingTasks.stream()
                .map(PaReportTaskResult::getMaxScore)
                .filter(Objects::nonNull)
                .filter(value -> value > 0D)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (maxScore > 0D) {
            double score = matchingTasks.stream()
                    .filter(task -> task.getMaxScore() != null && task.getMaxScore() > 0D)
                    .map(PaReportTaskResult::getScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            return score / maxScore * 100D;
        }
        List<Double> percents = matchingTasks.stream()
                .map(PaReportTaskResult::getPercent)
                .filter(Objects::nonNull)
                .toList();
        return percents.isEmpty() ? null : percents.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private int previousPeriodProgressMark(Double entryPercent, Double exitRepeatPercent) {
        if (entryPercent >= 90D && exitRepeatPercent >= 90D) return 5;
        if (entryPercent >= 75D && exitRepeatPercent >= 90D) return 5;
        if (entryPercent >= 75D && exitRepeatPercent >= 75D) return 4;
        double delta = exitRepeatPercent - entryPercent;
        if (delta <= -10D) return 2;
        if (delta <= 4D) return 3;
        if (delta <= 14D) return 4;
        return 5;
    }

    private int gapClosureMark(Double entryPercent, Double exitRepeatPercent) {
        double efficiency;
        if (entryPercent >= 90D) {
            if (exitRepeatPercent >= 90D) return 5;
            if (exitRepeatPercent >= 75D) return 4;
            efficiency = exitRepeatPercent - entryPercent;
        } else {
            efficiency = (exitRepeatPercent - entryPercent) / (100D - entryPercent) * 100D;
        }
        if (efficiency <= -30D) return 1;
        if (efficiency <= -10D) return 2;
        if (efficiency <= 39D) return 3;
        if (efficiency <= 59D) return 4;
        return 5;
    }

    private int currentPeriodMasteryMark(Double exitNewPercent) {
        if (exitNewPercent < 40D) return 2;
        if (exitNewPercent < 60D) return 3;
        if (exitNewPercent < 80D) return 4;
        return 5;
    }

    private int markDynamicsMark(Integer entryMark, Integer exitMark) {
        if (Objects.equals(entryMark, exitMark)) {
            if (entryMark == 5) return 5;
            if (entryMark == 4) return 4;
            if (entryMark == 2) return 2;
        }
        int delta = exitMark - entryMark;
        if (delta <= -2) return 2;
        if (delta <= 0) return 3;
        if (delta == 1) return 4;
        return 5;
    }

    private PaReportAnalysisSummary latestMatchingEntry(List<PaReportAnalysisSummary> entries, PaReportAnalysisSummary exit) {
        return entries.stream()
                .filter(candidate -> sameDynamicPair(candidate, exit))
                .filter(candidate -> candidate.getWorkDate() == null || exit.getWorkDate() == null || !candidate.getWorkDate().isAfter(exit.getWorkDate()))
                .max(Comparator.comparing(PaReportAnalysisSummary::getWorkDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean hasPresentResult(PaReportStudentResult student) {
        return student != null && student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT;
    }

    private String normalizedStudentKey(PaReportStudentResult student) {
        return !isBlank(student.getStudentFioNormalized())
                ? normalizeTeacherKey(student.getStudentFioNormalized())
                : normalizeTeacherKey(student.getStudentFio());
    }

    private Double calculateVsokoDynamic(List<PaReportAnalysisSummary> summaries,
                                         Map<Long, List<PaReportTaskResult>> tasksByReport) {
        List<PaReportAnalysisSummary> entries = summaries.stream()
                .filter(summary -> "ENTRY".equalsIgnoreCase(nvl(summary.getWorkType())))
                .toList();
        List<PaReportAnalysisSummary> exits = summaries.stream()
                .filter(summary -> "EXIT".equalsIgnoreCase(nvl(summary.getWorkType())))
                .toList();
        List<Double> deltas = new ArrayList<>();
        for (PaReportAnalysisSummary exit : exits) {
            PaReportAnalysisSummary entry = latestMatchingEntry(entries, exit);
            if (entry == null) {
                continue;
            }
            Double taskDelta = repeatedTaskDelta(entry, exit, tasksByReport);
            if (taskDelta != null) {
                deltas.add(taskDelta);
            } else if (entry.getAvgPercent() != null && exit.getAvgPercent() != null) {
                deltas.add(exit.getAvgPercent() - entry.getAvgPercent());
            }
        }
        return deltas.isEmpty() ? null : deltas.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private boolean sameDynamicPair(PaReportAnalysisSummary entry, PaReportAnalysisSummary exit) {
        return sameText(entry.getTeacherFio(), exit.getTeacherFio())
                && sameText(entry.getSubjectName(), exit.getSubjectName())
                && sameText(entry.getClassName(), exit.getClassName())
                && sameText(entry.getLevel(), exit.getLevel());
    }

    private Double repeatedTaskDelta(PaReportAnalysisSummary entry,
                                     PaReportAnalysisSummary exit,
                                     Map<Long, List<PaReportTaskResult>> tasksByReport) {
        Map<Integer, Double> entryAvgByTask = avgPercentByTask(tasksByReport.getOrDefault(entry.getReportVersionId(), List.of()));
        List<Double> deltas = tasksByReport.getOrDefault(exit.getReportVersionId(), List.of()).stream()
                .filter(task -> "REPEAT".equalsIgnoreCase(nvl(task.getTaskKind())))
                .filter(task -> task.getRepeatFromTaskNo() != null)
                .filter(task -> task.getPercent() != null)
                .map(task -> {
                    Double entryPercent = entryAvgByTask.get(task.getRepeatFromTaskNo());
                    return entryPercent == null ? null : task.getPercent() - entryPercent;
                })
                .filter(Objects::nonNull)
                .toList();
        return deltas.isEmpty() ? null : deltas.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private Map<Integer, Double> avgPercentByTask(List<PaReportTaskResult> tasks) {
        return tasks.stream()
                .filter(task -> task.getTaskNo() != null && task.getPercent() != null)
                .collect(Collectors.groupingBy(
                        PaReportTaskResult::getTaskNo,
                        Collectors.averagingDouble(PaReportTaskResult::getPercent)
                ));
    }

    private Integer dynamicMark(Double dynamicScore) {
        if (dynamicScore == null) return null;
        if (dynamicScore >= 15D) return 5;
        if (dynamicScore >= 5D) return 4;
        if (dynamicScore >= 0D) return 3;
        if (dynamicScore >= -10D) return 2;
        return 1;
    }

    private boolean sameText(String a, String b) {
        return normalizeTeacherKey(a).equals(normalizeTeacherKey(b));
    }

    private PaAnalyticsDtos.TeacherReportDetailRow toReportDetail(PaReportAnalysisSummary summary) {
        return new PaAnalyticsDtos.TeacherReportDetailRow(
                summary.getReportVersionId(),
                summary.getSubjectName(),
                summary.getClassName(),
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

    private Integer performanceMark(Double avgMark) {
        if (avgMark == null) {
            return null;
        }
        if (avgMark >= 4.5D) {
            return 5;
        }
        if (avgMark >= 3.5D) {
            return 4;
        }
        if (avgMark >= 2.5D) {
            return 3;
        }
        if (avgMark >= 1.5D) {
            return 2;
        }
        return 1;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeTeacherKey(String value) {
        return nvl(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").trim();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
