package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentClassEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentClassEnrollmentRepository extends JpaRepository<StudentClassEnrollment, Long> {
    List<StudentClassEnrollment> findAllByAcademicYear(String academicYear);

    List<StudentClassEnrollment> findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(Long studentId, String academicYear);

    Optional<StudentClassEnrollment> findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(
            Long studentId,
            String academicYear
    );
}
