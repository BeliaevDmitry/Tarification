package org.school.personalLoad.pa.analytics.repository;

import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaReportTaskResultRepository extends JpaRepository<PaReportTaskResult, Long> {

    List<PaReportTaskResult> findAllByReportVersionIdOrderByTaskNoAsc(Long reportVersionId);

    List<PaReportTaskResult> findAllByReportVersionIdIn(List<Long> reportVersionIds);

    void deleteByReportVersionId(Long reportVersionId);
}
