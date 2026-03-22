package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyPeriodSettingRepository extends JpaRepository<StudyPeriodSetting, Long> {
    Optional<StudyPeriodSetting> findByStudyPeriod(StudyPeriod studyPeriod);
}
