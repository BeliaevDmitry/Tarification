package org.school.personalLoad.service;

import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.model.StudyPeriodSettingKey;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StudyPeriodSettingService {
    List<StudyPeriodSetting> findAll();
    List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests);
    Map<StudyPeriodSettingKey, DateRange> rangesByKey();
    DateRange resolveDateRange(String className, StudyPeriod studyPeriod);
    StudyPeriod inferStudyPeriod(String className, LocalDate loadFromDate, LocalDate loadToDate);
    StudyPeriodSettingKey resolveKey(String className, StudyPeriod studyPeriod);

    record DateRange(LocalDate startDate, LocalDate endDate) {
        public boolean contains(LocalDate date) {
            return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
        }

        public boolean fullyContains(LocalDate fromDate, LocalDate toDate) {
            return fromDate != null && toDate != null && !fromDate.isBefore(startDate) && !toDate.isAfter(endDate);
        }
    }
}
