package org.school.personalLoad.repository;

import org.school.personalLoad.model.IupSubjectLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IupSubjectLineRepository extends JpaRepository<IupSubjectLine, Long> {
    List<IupSubjectLine> findAllByIupPlan_IdOrderBySubjectNameAsc(Long iupPlanId);

    void deleteAllByIupPlan_Id(Long iupPlanId);
}
