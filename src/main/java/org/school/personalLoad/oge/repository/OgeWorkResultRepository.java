package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeWorkResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OgeWorkResultRepository extends JpaRepository<OgeWorkResult, Long> {
    Optional<OgeWorkResult> findByAcademicYearAndFullNameAndBirthDateAndSnilsAndSubjectName(
            String academicYear, String fullName, String birthDate, String snils, String subjectName);

    List<OgeWorkResult> findAllByAcademicYearOrderByClassNameAscFullNameAscSubjectNameAsc(String academicYear);
}
