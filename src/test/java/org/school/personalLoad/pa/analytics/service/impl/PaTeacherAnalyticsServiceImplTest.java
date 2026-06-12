package org.school.personalLoad.pa.analytics.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaTeacherAnalyticsServiceImplTest {

    @Mock
    private PaReportAnalysisSummaryRepository summaryRepository;
    @Mock
    private PaReportStudentResultRepository studentResultRepository;
    @Mock
    private PaReportTaskResultRepository taskResultRepository;
    @Mock
    private PaReportVersionRepository reportVersionRepository;

    @Test
    void getTeacherSummariesCalculatesVsokoDynamicFromRepeatedExitTasks() {
        PaReportAnalysisSummary entry = summary(1L, "ENTRY", LocalDate.of(2025, 9, 10), 70D);
        PaReportAnalysisSummary exit = summary(2L, "EXIT", LocalDate.of(2026, 5, 10), 80D);
        when(summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc("2025/2026"))
                .thenReturn(List.of(entry, exit));
        when(reportVersionRepository.findAll()).thenReturn(List.of(version(1L), version(2L)));
        when(studentResultRepository.findAllByReportVersionIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(student(101L, 1L, "Иванов Иван", 70D, 4), student(201L, 2L, "Иванов Иван", 80D, 5)));
        when(taskResultRepository.findAllByReportVersionIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(
                        task(1L, 101L, 1, null, null, null, null, 60D),
                        task(2L, 201L, 3, "REPEAT", 1, null, null, 90D)
                ));
        PaTeacherAnalyticsServiceImpl service = new PaTeacherAnalyticsServiceImpl(
                summaryRepository,
                studentResultRepository,
                taskResultRepository,
                reportVersionRepository
        );

        List<PaAnalyticsDtos.TeacherSummaryRow> rows = service.getTeacherSummaries("2025/2026", null, null);

        assertEquals(1, rows.size());
        PaAnalyticsDtos.TeacherSummaryRow row = rows.get(0);
        assertNotNull(row.vsokoDynamicScore());
        assertEquals(30D, row.vsokoDynamicScore(), 0.001);
        assertEquals("CALCULATED", row.vsokoDynamicStatus());
    }

    @Test
    void getTeacherSummariesCalculatesTeacherPerformanceByVsokoPolicy() {
        PaReportAnalysisSummary entry = summary(1L, "ENTRY", LocalDate.of(2025, 9, 10), 70D);
        PaReportAnalysisSummary exit = summary(2L, "EXIT", LocalDate.of(2026, 5, 10), 80D);
        when(summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc("2025/2026"))
                .thenReturn(List.of(entry, exit));
        when(reportVersionRepository.findAll()).thenReturn(List.of(version(1L), version(2L)));
        when(studentResultRepository.findAllByReportVersionIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(student(101L, 1L, "Иванов Иван", 70D, 3), student(201L, 2L, "Иванов Иван", 80D, 4)));
        when(taskResultRepository.findAllByReportVersionIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(
                        task(2L, 201L, 3, "REPEAT", 1, 8D, 10D, 80D),
                        task(2L, 201L, 4, "NEW", null, 9D, 10D, 90D)
                ));
        PaTeacherAnalyticsServiceImpl service = new PaTeacherAnalyticsServiceImpl(
                summaryRepository,
                studentResultRepository,
                taskResultRepository,
                reportVersionRepository
        );

        List<PaAnalyticsDtos.TeacherSummaryRow> rows = service.getTeacherSummaries("2025/2026", null, null);

        assertEquals(1, rows.size());
        PaAnalyticsDtos.TeacherSummaryRow row = rows.get(0);
        assertNotNull(row.paPerformanceScore());
        assertEquals(4D, row.paPerformanceScore(), 0.001);
        assertEquals(4, row.paPerformanceMark());
    }

    private PaReportAnalysisSummary summary(Long reportVersionId, String workType, LocalDate workDate, Double avgPercent) {
        PaReportAnalysisSummary summary = new PaReportAnalysisSummary();
        summary.setReportVersionId(reportVersionId);
        summary.setAcademicYear("2025/2026");
        summary.setSubjectName("Русский язык");
        summary.setClassName("8-Ц");
        summary.setTeacherFio("Самаркина Ольга Сергеевна");
        summary.setWorkType(workType);
        summary.setWorkDate(workDate);
        summary.setLevel("BASIC");
        summary.setStudentsWithResult(1);
        summary.setAvgPercent(avgPercent);
        summary.setAvgMark(avgPercent >= 80D ? 5D : 4D);
        summary.setSuccessPercent(100D);
        summary.setQualityPercent(100D);
        summary.setAnalysisStatus(PaAnalysisStatus.SUCCESS);
        return summary;
    }

    private PaReportVersion version(Long id) {
        PaReportVersion version = new PaReportVersion();
        version.setId(id);
        version.setActiveVersion(true);
        version.setStatus("ACCEPTED");
        version.setUploadedBackSuccess(true);
        version.setSourceFilePath("/tmp/report-" + id + ".xlsx");
        version.setSubjectName("Русский язык");
        version.setScopeValue("8-Ц");
        return version;
    }

    private PaReportStudentResult student(Long id, Long reportVersionId, String fio, Double percent, Integer mark) {
        PaReportStudentResult student = new PaReportStudentResult();
        student.setId(id);
        student.setReportVersionId(reportVersionId);
        student.setStudentFio(fio);
        student.setStudentFioNormalized(fio.toUpperCase());
        student.setPercent(percent);
        student.setMark(mark);
        student.setRowStatus(PaStudentResultStatus.PRESENT_WITH_RESULT);
        return student;
    }

    private PaReportTaskResult task(Long reportVersionId, Long studentResultId, Integer taskNo, String kind, Integer repeatFrom, Double score, Double maxScore, Double percent) {
        PaReportTaskResult task = new PaReportTaskResult();
        task.setReportVersionId(reportVersionId);
        task.setStudentResultId(studentResultId);
        task.setTaskNo(taskNo);
        task.setTaskKind(kind);
        task.setRepeatFromTaskNo(repeatFrom);
        task.setScore(score);
        task.setMaxScore(maxScore);
        task.setPercent(percent);
        return task;
    }
}
