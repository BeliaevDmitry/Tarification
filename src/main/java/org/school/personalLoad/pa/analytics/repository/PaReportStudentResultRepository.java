package org.school.personalLoad.pa.analytics.repository;

import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaReportStudentResultRepository extends JpaRepository<PaReportStudentResult, Long> {

    List<PaReportStudentResult> findAllByReportVersionIdOrderByStudentFioAsc(Long reportVersionId);

    List<PaReportStudentResult> findAllByReportVersionIdIn(List<Long> reportVersionIds);

    List<PaReportStudentResult> findAllByStudentIdOrderByAcademicYearAscIdAsc(Long studentId);

    List<PaReportStudentResult> findAllByAcademicYearAndClassName(String academicYear, String className);

    List<PaReportStudentResult> findAllByAcademicYear(String academicYear);

    void deleteByReportVersionId(Long reportVersionId);
}
