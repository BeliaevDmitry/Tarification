package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentClassEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentClassEnrollmentRepository extends JpaRepository<StudentClassEnrollment, Long> {
    List<StudentClassEnrollment> findAllByAcademicYear(String academicYear);

    List<StudentClassEnrollment> findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(Long studentId, String academicYear);

    Optional<StudentClassEnrollment> findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(
            Long studentId,
            String academicYear
    );

    long countByClassRef_Id(Long classId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update student_class_enrollment set class_id = null where class_id = :classId", nativeQuery = true)
    int detachClassReference(@Param("classId") Long classId);
}
