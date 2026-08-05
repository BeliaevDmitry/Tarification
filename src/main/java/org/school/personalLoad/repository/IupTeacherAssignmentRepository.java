package org.school.personalLoad.repository;

import org.school.personalLoad.model.IupTeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IupTeacherAssignmentRepository extends JpaRepository<IupTeacherAssignment, Long> {
    List<IupTeacherAssignment> findAllBySubjectLine_IupPlan_Id(Long iupPlanId);

    void deleteAllBySubjectLine_IupPlan_Id(Long iupPlanId);
}
