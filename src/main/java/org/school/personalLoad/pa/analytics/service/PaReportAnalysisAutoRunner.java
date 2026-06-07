package org.school.personalLoad.pa.analytics.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.analytics.event.PaReportAcceptedForAnalysisEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaReportAnalysisAutoRunner {

    private final PaReportAnalysisJobRunner paReportAnalysisJobRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void analyzeAcceptedReport(PaReportAcceptedForAnalysisEvent event) {
        try {
            paReportAnalysisJobRunner.analyzeOneInNewTransaction(event.reportVersionId());
        } catch (Exception exception) {
            saveErrorSafely(event.reportVersionId(), exception);
        }
    }

    private void saveErrorSafely(Long reportVersionId, Exception exception) {
        try {
            paReportAnalysisJobRunner.saveAnalysisErrorInNewTransaction(reportVersionId, exception);
        } catch (Exception ignored) {
            // Аналитика не должна ломать успешную сдачу отчёта ПА.
        }
    }
}
