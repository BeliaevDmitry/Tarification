package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudyPeriodSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyPeriodSettingRepository extends JpaRepository<StudyPeriodSetting, Long> {
    Optional<StudyPeriodSetting> findBySettingKey(String settingKey);
    List<StudyPeriodSetting> findAllByOrderByParallelFromAscParallelToAscStudyPeriodAscDisplayNameAsc();
}
