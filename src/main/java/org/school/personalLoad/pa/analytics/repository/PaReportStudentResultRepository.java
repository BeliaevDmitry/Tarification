package org.school.personalLoad.pa.analytics.repository;

import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaReportStudentResultRepository extends JpaRepository<PaReportStudentResult, Long> {

    List<PaReportStudentResult> findAllByReportVersionIdOrderByStudentFioAsc(Long reportVersionId);

    void deleteByReportVersionId(Long reportVersionId);
}
