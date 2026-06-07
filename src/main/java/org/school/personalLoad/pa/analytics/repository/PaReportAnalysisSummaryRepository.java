package org.school.personalLoad.pa.analytics.repository;

import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaReportAnalysisSummaryRepository extends JpaRepository<PaReportAnalysisSummary, Long> {

    Optional<PaReportAnalysisSummary> findByReportVersionId(Long reportVersionId);

    void deleteByReportVersionId(Long reportVersionId);

    List<PaReportAnalysisSummary> findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc(String academicYear);

    List<PaReportAnalysisSummary> findAllByAcademicYearAndAnalysisStatus(String academicYear, PaAnalysisStatus status);
}
