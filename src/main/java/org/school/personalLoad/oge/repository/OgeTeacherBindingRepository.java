package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeTeacherBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OgeTeacherBindingRepository extends JpaRepository<OgeTeacherBinding, Long> {
    List<OgeTeacherBinding> findAllByAcademicYearOrderByClassNameAscSubjectNameAscFullNameAsc(String academicYear);

    Optional<OgeTeacherBinding> findByAcademicYearAndClassNameAndFullNameAndSubjectName(
            String academicYear, String className, String fullName, String subjectName);
}

