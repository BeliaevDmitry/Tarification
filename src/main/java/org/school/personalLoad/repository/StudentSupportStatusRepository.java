package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentSupportStatusRepository extends JpaRepository<StudentSupportStatus, Long> {
    List<StudentSupportStatus> findAllByAcademicYear(String academicYear);

    List<StudentSupportStatus> findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(Long studentId, String academicYear);
}
