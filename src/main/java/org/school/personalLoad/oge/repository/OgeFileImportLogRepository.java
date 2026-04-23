package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeFileImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OgeFileImportLogRepository extends JpaRepository<OgeFileImportLog, Long> {
    List<OgeFileImportLog> findAllByAcademicYearAndWorkSourceOrderByCreatedAtDescIdDesc(String academicYear, String workSource);
}

