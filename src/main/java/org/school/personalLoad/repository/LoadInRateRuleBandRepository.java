package org.school.personalLoad.repository;

import org.school.personalLoad.model.LoadInRateRuleBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoadInRateRuleBandRepository extends JpaRepository<LoadInRateRuleBand, Long> {
    List<LoadInRateRuleBand> findAllByRuleIdOrderByMinTotalHoursAsc(Long ruleId);
    void deleteAllByRuleId(Long ruleId);
}
