package org.school.personalLoad.repository;

import org.school.personalLoad.model.LoadInRateRuleSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LoadInRateRuleSubjectRepository extends JpaRepository<LoadInRateRuleSubject, Long> {
    List<LoadInRateRuleSubject> findAllByRuleIdOrderBySubject_SubjectNameAsc(Long ruleId);

    List<LoadInRateRuleSubject> findAllByRuleIdIn(Collection<Long> ruleIds);

    void deleteAllByRuleId(Long ruleId);
}
