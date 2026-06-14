package org.school.personalLoad.repository;

import org.school.personalLoad.model.TeacherPrimarySubjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherPrimarySubjectAssignmentRepository extends JpaRepository<TeacherPrimarySubjectAssignment, Long> {
    List<TeacherPrimarySubjectAssignment> findAllByAcademicYear(String academicYear);
    Optional<TeacherPrimarySubjectAssignment> findByAcademicYearAndTeacherId(String academicYear, Long teacherId);
}
