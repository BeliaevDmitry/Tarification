package org.school.personalLoad.pa.analytics.service;

import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;

import java.util.List;

public interface PaTeacherAnalyticsService {

    List<PaAnalyticsDtos.TeacherSummaryRow> getTeacherSummaries(String academicYear,
                                                                String subjectName,
                                                                Boolean onlyNeedsReview);

    PaAnalyticsDtos.TeacherDetailsResponse getTeacherDetails(String academicYear, String teacherFio);
}
