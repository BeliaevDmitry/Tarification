package org.school.personalLoad.repository;

import org.school.personalLoad.model.IupPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IupPlanRepository extends JpaRepository<IupPlan, Long> {
    List<IupPlan> findAllByAcademicYear(String academicYear);

    List<IupPlan> findAllByStudent_IdAndAcademicYearOrderByVersionNumberDesc(Long studentId, String academicYear);
}
