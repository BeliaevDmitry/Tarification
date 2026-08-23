package org.school.personalLoad.repository;

import org.school.personalLoad.model.CorrectionSpecialistStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionSpecialistStaffRepository extends JpaRepository<CorrectionSpecialistStaff, Long> {
    List<CorrectionSpecialistStaff> findAllByOrderBySpecialist_NameAscTeacher_FioTeacherAsc();
    List<CorrectionSpecialistStaff> findAllBySpecialist_IdAndActiveTrueOrderByTeacher_FioTeacherAsc(Long specialistId);
    Optional<CorrectionSpecialistStaff> findBySpecialist_IdAndTeacher_Id(Long specialistId, Long teacherId);
}
