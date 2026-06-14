package org.school.personalLoad.repository;

import org.school.personalLoad.model.PrimarySubjectRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrimarySubjectRuleRepository extends JpaRepository<PrimarySubjectRule, Long> {
    Optional<PrimarySubjectRule> findByPrimarySubjectIgnoreCase(String primarySubject);
    List<PrimarySubjectRule> findAllByOrderByPriorityAscPrimarySubjectAsc();
}
