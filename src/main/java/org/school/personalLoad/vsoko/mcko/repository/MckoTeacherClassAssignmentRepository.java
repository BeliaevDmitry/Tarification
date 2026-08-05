package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.MckoTeacherClassAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MckoTeacherClassAssignmentRepository extends JpaRepository<MckoTeacherClassAssignment, Long> {
    List<MckoTeacherClassAssignment> findAllByAcademicYearOrderByClassNameAscSubjectNameAsc(String academicYear);
    Optional<MckoTeacherClassAssignment> findByAcademicYearAndClassNameIgnoreCaseAndSubjectNameIgnoreCase(
            String academicYear, String className, String subjectName);
}
