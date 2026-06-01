package org.school.educationalwork.repository;

import org.school.educationalwork.model.EducationalWorkReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EducationalWorkReportRepository extends JpaRepository<EducationalWorkReportEntity, Long> {
    Optional<EducationalWorkReportEntity> findByAcademicYearAndSchoolClass(String academicYear, String schoolClass);

    List<EducationalWorkReportEntity> findAllByAcademicYear(String academicYear);
}
