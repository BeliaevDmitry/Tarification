package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentGroupMembershipRepository extends JpaRepository<StudentGroupMembership, Long> {
    List<StudentGroupMembership> findAllByAcademicYear(String academicYear);

    List<StudentGroupMembership> findAllByStudent_IdAndAcademicYear(Long studentId, String academicYear);

    void deleteAllByIupSubjectLineIdIn(List<Long> iupSubjectLineIds);
}
