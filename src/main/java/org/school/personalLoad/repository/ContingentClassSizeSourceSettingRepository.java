package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentClassSizeSourceSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContingentClassSizeSourceSettingRepository extends JpaRepository<ContingentClassSizeSourceSetting, Long> {
    Optional<ContingentClassSizeSourceSetting> findByAcademicYear(String academicYear);
}
