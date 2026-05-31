package org.school.personalLoad.repository;

import org.school.personalLoad.model.SalarySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalarySettingsRepository extends JpaRepository<SalarySettings, Long> {
}
