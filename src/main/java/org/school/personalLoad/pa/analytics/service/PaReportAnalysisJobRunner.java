package org.school.personalLoad.pa.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaReportAnalysisJobRunner {

    private final PaReportAnalysisService paReportAnalysisService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeOneInNewTransaction(Long reportVersionId) {
        paReportAnalysisService.analyzeReport(reportVersionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAnalysisErrorInNewTransaction(Long reportVersionId, Exception exception) {
        paReportAnalysisService.saveAnalysisError(reportVersionId, exception);
    }
}
