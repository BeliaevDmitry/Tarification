package org.school.personalLoad.repository;

import org.school.personalLoad.model.LoadInRateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoadInRateRuleRepository extends JpaRepository<LoadInRateRule, Long> {
    List<LoadInRateRule> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByNameIgnoreCase(String name);
}
