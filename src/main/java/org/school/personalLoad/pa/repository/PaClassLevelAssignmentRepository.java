package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaClassLevelAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaClassLevelAssignmentRepository extends JpaRepository<PaClassLevelAssignment, Long> {
    List<PaClassLevelAssignment> findAllByAcademicYear(String academicYear);
    Optional<PaClassLevelAssignment> findByAcademicYearAndSubjectNameAndClassNameAndWorkType(String academicYear, String subjectName, String className, org.school.personalLoad.pa.model.PaWorkType workType);
}
