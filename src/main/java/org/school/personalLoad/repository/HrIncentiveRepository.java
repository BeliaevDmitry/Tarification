package org.school.personalLoad.repository;

import org.school.personalLoad.model.HrIncentive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HrIncentiveRepository extends JpaRepository<HrIncentive, Long> {
    List<HrIncentive> findAllByAcademicYear(String academicYear);
    Optional<HrIncentive> findByAcademicYearAndTeacherId(String academicYear, Long teacherId);
}
