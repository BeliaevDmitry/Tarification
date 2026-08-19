package org.school.personalLoad.repository;

import org.school.personalLoad.model.OvzApplicationChoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OvzApplicationChoiceRepository extends JpaRepository<OvzApplicationChoice, Long> {
    List<OvzApplicationChoice> findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(Long studentId, String academicYear);
    void deleteAllByStudent_IdAndAcademicYear(Long studentId, String academicYear);
}
