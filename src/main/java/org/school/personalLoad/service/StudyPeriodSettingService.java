package org.school.personalLoad.service;

import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.model.StudyPeriodSettingKey;

import java.time.LocalDate;
import java.util.List;

public interface StudyPeriodSettingService {
    List<StudyPeriodSetting> findAll(String academicYear);
    List<StudyPeriodSetting> saveAll(String academicYear, List<StudyPeriodSettingRequest> requests);
    StudyPeriodSetting create(String academicYear, StudyPeriodSettingRequest request);
    java.util.Map<StudyPeriodSettingKey, DateRange> rangesByKey(String academicYear);
    DateRange resolveDateRange(String academicYear, String className, StudyPeriod studyPeriod);
    StudyPeriod inferStudyPeriod(String academicYear, String className, LocalDate loadFromDate, LocalDate loadToDate);
    List<StudyPeriodSetting> findAvailableForClass(String academicYear, String className);
    StudyPeriodSetting resolveRuleForClassAndPeriod(String academicYear, String className, StudyPeriod studyPeriod);

    default List<StudyPeriodSetting> findAll() { return findAll(null); }
    default List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests) { return saveAll(null, requests); }
    default StudyPeriodSetting create(StudyPeriodSettingRequest request) { return create(null, request); }
    default java.util.Map<StudyPeriodSettingKey, DateRange> rangesByKey() { return rangesByKey(null); }
    default DateRange resolveDateRange(String className, StudyPeriod studyPeriod) { return resolveDateRange(null, className, studyPeriod); }
    default StudyPeriod inferStudyPeriod(String className, LocalDate loadFromDate, LocalDate loadToDate) { return inferStudyPeriod(null, className, loadFromDate, loadToDate); }
    default List<StudyPeriodSetting> findAvailableForClass(String className) { return findAvailableForClass(null, className); }
    default StudyPeriodSetting resolveRuleForClassAndPeriod(String className, StudyPeriod studyPeriod) { return resolveRuleForClassAndPeriod(null, className, studyPeriod); }

    record DateRange(LocalDate startDate, LocalDate endDate) {
        public boolean fullyContains(LocalDate fromDate, LocalDate toDate) {
            return fromDate != null && toDate != null && !fromDate.isBefore(startDate) && !toDate.isAfter(endDate);
        }
    }
}
