package org.school.personalLoad.repository;

import org.school.personalLoad.model.OvzSpecialistSupportEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OvzSpecialistSupportEntryRepository extends JpaRepository<OvzSpecialistSupportEntry, Long> {
    List<OvzSpecialistSupportEntry> findAllByAcademicYear(String academicYear);

    List<OvzSpecialistSupportEntry> findAllByAcademicYearAndStudent_Id(String academicYear, Long studentId);

    Optional<OvzSpecialistSupportEntry> findByAcademicYearAndStudent_IdAndSpecialist_Id(
            String academicYear, Long studentId, Long specialistId);
}
