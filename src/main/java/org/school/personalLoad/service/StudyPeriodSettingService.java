package org.school.personalLoad.service;

import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StudyPeriodSettingService {
    List<StudyPeriodSetting> findAll();
    List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests);
    Map<StudyPeriod, DateRange> rangesByPeriod();

    record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
