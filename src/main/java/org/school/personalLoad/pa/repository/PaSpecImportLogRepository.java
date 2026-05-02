package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaSpecImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaSpecImportLogRepository extends JpaRepository<PaSpecImportLog, Long> {
    List<PaSpecImportLog> findAllByAcademicYearOrderByCreatedAtDescIdDesc(String academicYear);
    List<PaSpecImportLog> findAllByAcademicYearAndCreatedByOrderByCreatedAtDescIdDesc(String academicYear, String createdBy);
}
