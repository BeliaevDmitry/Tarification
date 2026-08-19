package org.school.personalLoad.repository;

import org.school.personalLoad.model.OvzRoadmapStage;
import org.school.personalLoad.model.OvzWorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OvzWorkflowStageRepository extends JpaRepository<OvzWorkflowStage, Long> {
    List<OvzWorkflowStage> findAllByAcademicYear(String academicYear);
    List<OvzWorkflowStage> findAllByStudent_IdAndAcademicYear(Long studentId, String academicYear);
    Optional<OvzWorkflowStage> findByStudent_IdAndAcademicYearAndStage(Long studentId, String academicYear, OvzRoadmapStage stage);
    void deleteAllByStudent_IdAndAcademicYear(Long studentId, String academicYear);
}
