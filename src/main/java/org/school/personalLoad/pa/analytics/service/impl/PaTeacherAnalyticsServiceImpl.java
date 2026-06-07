package org.school.personalLoad.pa.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
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
        return byTeacher.values().stream()
                .map(teacherSummaries -> toTeacherSummary(teacherSummaries, studentsByReport, null))
                .sorted(Comparator.comparing(PaAnalyticsDtos.TeacherSummaryRow::teacherFio, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaAnalyticsDtos.TeacherDetailsResponse getTeacherDetails(String academicYear, String teacherFio) {
        List<PaReportAnalysisSummary> summaries = loadEligibleSummaries(academicYear, null, null, teacherFio);
        Map<Long, List<PaReportStudentResult>> studentsByReport = loadStudentsByReport(summaries);
        PaAnalyticsDtos.TeacherSummaryRow teacherSummary = toTeacherSummary(summaries, studentsByReport, teacherFio);
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
        return summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc(academicYear)
                .stream()
                .filter(this::hasSuccessfulAnalysis)
                .filter(summary -> positive(summary.getStudentsWithResult()))
                .filter(summary -> matches(summary.getSubjectName(), subjectName))
                .filter(summary -> matches(summary.getTeacherFio(), teacherFio))
                .filter(summary -> !Boolean.TRUE.equals(onlyNeedsReview) || summary.isNeedsReview())
                .filter(summary -> isActiveAcceptedReport(summary, versionsById))
                .toList();
    }

    private boolean hasSuccessfulAnalysis(PaReportAnalysisSummary summary) {
        return summary.getAnalysisStatus() == PaAnalysisStatus.SUCCESS
                || summary.getAnalysisStatus() == PaAnalysisStatus.WARNING;
    }

    private boolean isActiveAcceptedReport(PaReportAnalysisSummary summary, Map<Long, PaReportVersion> versionsById) {
        PaReportVersion version = versionsById.get(summary.getReportVersionId());
        return version != null && version.isActiveVersion() && "ACCEPTED".equalsIgnoreCase(nvl(version.getStatus()));
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

    private PaAnalyticsDtos.TeacherSummaryRow toTeacherSummary(List<PaReportAnalysisSummary> summaries,
                                                               Map<Long, List<PaReportStudentResult>> studentsByReport,
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
                avgMark,
                performanceMark(avgMark),
                null,
                null,
                DYNAMIC_NOT_AVAILABLE
        );
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
