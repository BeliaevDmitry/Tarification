package org.school.personalLoad.repository;

import org.school.personalLoad.model.CorrectionStudentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionStudentAssignmentRepository extends JpaRepository<CorrectionStudentAssignment, Long> {
    List<CorrectionStudentAssignment> findAllByAcademicYear(String academicYear);
    List<CorrectionStudentAssignment> findAllByAcademicYearAndStudent_Id(String academicYear, Long studentId);
    List<CorrectionStudentAssignment> findAllByAcademicYearAndGroup_Id(String academicYear, Long groupId);
    Optional<CorrectionStudentAssignment> findByAcademicYearAndStudent_IdAndSpecialist_Id(
            String academicYear, Long studentId, Long specialistId);
    long countByAcademicYearAndStaff_Id(String academicYear, Long staffId);
    void deleteAllByAcademicYearAndStudent_Id(String academicYear, Long studentId);
    void deleteAllByGroup_Id(Long groupId);
}
