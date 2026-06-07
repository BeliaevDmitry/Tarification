package org.school.personalLoad.pa.analytics.service;

import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;

import java.util.List;

public interface PaReportAnalysisService {

    void analyzeReport(Long reportVersionId);

    void saveAnalysisError(Long reportVersionId, Exception exception);

    PaAnalyticsDtos.RebuildAllResult rebuildAll(String academicYear);

    PaAnalyticsDtos.ReportAnalysisDetails getDetails(Long reportVersionId);

    List<PaAnalyticsDtos.ReportAnalysisListItem> getReports(String academicYear,
                                                            String subjectName,
                                                            String teacherFio,
                                                            String className,
                                                            String workType,
                                                            Boolean onlyProblems,
                                                            Boolean onlyNeedsReview);
}
