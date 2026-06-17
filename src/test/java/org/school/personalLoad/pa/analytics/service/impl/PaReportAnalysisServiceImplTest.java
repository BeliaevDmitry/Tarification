package org.school.personalLoad.pa.analytics.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisJobRunner;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaSpecificationTaskRepository;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaReportAnalysisServiceImplTest {

    @Mock
    private PaReportVersionRepository reportVersionRepository;
    @Mock
    private PaSpecificationRepository specificationRepository;
    @Mock
    private PaSpecificationTaskRepository specificationTaskRepository;
    @Mock
    private PaReportAnalysisSummaryRepository summaryRepository;
    @Mock
    private PaReportStudentResultRepository studentResultRepository;
    @Mock
    private PaReportTaskResultRepository taskResultRepository;
    @Mock
    private ObjectProvider<PaReportAnalysisJobRunner> jobRunnerProvider;
    @Mock
    private PaReportAnalysisJobRunner jobRunner;

    @Test
    void rebuildAllProcessesOnlyActiveAcceptedReportsWithExistingFiles() throws Exception {
        Path reportFile = Files.createTempFile("pa-report-analysis", ".xlsx");
        PaReportVersion legacyReport = new PaReportVersion();
        legacyReport.setId(42L);
        legacyReport.setAcademicYear("2025/2026");
        legacyReport.setSubjectName("Математика");
        legacyReport.setScopeValue("5-А");
        legacyReport.setStatus("ACCEPTED");
        legacyReport.setUploadedBackSuccess(false);
        legacyReport.setSourceFilePath(reportFile.toString());
        PaReportVersion inactiveOldVersion = new PaReportVersion();
        inactiveOldVersion.setId(13L);
        inactiveOldVersion.setAcademicYear("2025/2026");
        inactiveOldVersion.setSubjectName("Математика");
        inactiveOldVersion.setScopeValue("5-А");
        inactiveOldVersion.setStatus("ACCEPTED");
        inactiveOldVersion.setActiveVersion(false);
        inactiveOldVersion.setSourceFilePath(reportFile.toString());
        when(reportVersionRepository.findAll()).thenReturn(List.of(legacyReport, inactiveOldVersion));
        when(jobRunnerProvider.getObject()).thenReturn(jobRunner);
        PaReportAnalysisServiceImpl service = service();

        var result = service.rebuildAll("2025/2026");

        assertEquals(1, result.processed());
        assertEquals(0, result.failed());
        verify(jobRunner).analyzeOneInNewTransaction(42L);
        verify(jobRunner, never()).analyzeOneInNewTransaction(13L);
    }

    private PaReportAnalysisServiceImpl service() {
        return new PaReportAnalysisServiceImpl(
                reportVersionRepository,
                specificationRepository,
                specificationTaskRepository,
                summaryRepository,
                studentResultRepository,
                taskResultRepository,
                jobRunnerProvider
        );
    }
}
