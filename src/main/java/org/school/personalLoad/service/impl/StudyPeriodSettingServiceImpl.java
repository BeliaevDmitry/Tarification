package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class StudyPeriodSettingServiceImpl implements StudyPeriodSettingService {

    private static final LocalDate DEFAULT_YEAR_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate DEFAULT_YEAR_END = LocalDate.of(2027, 5, 31);
    private static final LocalDate DEFAULT_H1_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DEFAULT_H2_START = LocalDate.of(2027, 1, 10);
    private static final LocalDate DEFAULT_11_H1_END = LocalDate.of(2027, 1, 31);
    private static final LocalDate DEFAULT_11_H2_START = LocalDate.of(2027, 2, 1);

    private final StudyPeriodSettingRepository repository;

    @Override
    public List<StudyPeriodSetting> findAll() {
        ensureDefaults();
        List<StudyPeriodSetting> result = new ArrayList<>();
        for (StudyPeriodSettingKey key : StudyPeriodSettingKey.values()) {
            result.add(repository.findBySettingKey(key).orElseThrow());
        }
        return result;
    }

    @Override
    public List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Передайте хотя бы одну настройку периода обучения");
        }

        ensureDefaults();
        Map<StudyPeriodSettingKey, StudyPeriodSettingRequest> byKey = new EnumMap<>(StudyPeriodSettingKey.class);
        for (StudyPeriodSettingRequest request : requests) {
            if (request == null || request.getSettingKey() == null) {
                throw new IllegalArgumentException("settingKey is required");
            }
            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new IllegalArgumentException("Для настройки " + request.getSettingKey() + " обязательны startDate и endDate");
            }
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new IllegalArgumentException("Для настройки " + request.getSettingKey() + " startDate должен быть раньше или равен endDate");
            }
            byKey.put(request.getSettingKey(), request);
        }

        List<StudyPeriodSetting> updated = new ArrayList<>();
        for (StudyPeriodSettingKey key : StudyPeriodSettingKey.values()) {
            StudyPeriodSettingRequest request = byKey.get(key);
            if (request == null) {
                throw new IllegalArgumentException("Не передана настройка для периода " + key);
            }
            StudyPeriodSetting entity = repository.findBySettingKey(key).orElseGet(StudyPeriodSetting::new);
            fillEntity(entity, key, request.getStartDate(), request.getEndDate());
            updated.add(repository.save(entity));
        }
        return updated;
    }

    @Override
    public Map<StudyPeriodSettingKey, DateRange> rangesByKey() {
        Map<StudyPeriodSettingKey, DateRange> result = new EnumMap<>(StudyPeriodSettingKey.class);
        findAll().forEach(setting -> result.put(setting.getSettingKey(), new DateRange(setting.getStartDate(), setting.getEndDate())));
        return result;
    }

    @Override
    public DateRange resolveDateRange(String className, StudyPeriod studyPeriod) {
        ensureDefaults();
        StudyPeriodSettingKey key = resolveKey(className, studyPeriod == null ? StudyPeriod.YEAR : studyPeriod);
        StudyPeriodSetting setting = repository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Настройка периода не найдена: " + key));
        return new DateRange(setting.getStartDate(), setting.getEndDate());
    }

    @Override
    public StudyPeriod inferStudyPeriod(String className, LocalDate loadFromDate, LocalDate loadToDate) {
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        if (parallel == null) {
            return StudyPeriod.YEAR;
        }
        ensureDefaults();
        Map<StudyPeriodSettingKey, DateRange> ranges = rangesByKey();

        if (parallel >= 11) {
            return inferForHighSchool(parallel, loadFromDate, loadToDate, ranges);
        }
        if (parallel == 10) {
            return inferForHighSchool(parallel, loadFromDate, loadToDate, ranges);
        }
        return inferForMiddleSchool(loadFromDate, loadToDate, ranges);
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPeriodSettingKey resolveKey(String className, StudyPeriod studyPeriod) {
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        if (parallel == null || parallel <= 9) {
            if (studyPeriod == StudyPeriod.H1) return StudyPeriodSettingKey.H1_1_9;
            if (studyPeriod == StudyPeriod.H2) return StudyPeriodSettingKey.H2_1_9;
            return StudyPeriodSettingKey.YEAR_1_9;
        }
        if (parallel == 10) {
            return studyPeriod == StudyPeriod.H2 ? StudyPeriodSettingKey.H2_10 : StudyPeriodSettingKey.H1_10;
        }
        return studyPeriod == StudyPeriod.H2 ? StudyPeriodSettingKey.H2_11 : StudyPeriodSettingKey.H1_11;
    }

    private StudyPeriod inferForMiddleSchool(LocalDate loadFromDate,
                                             LocalDate loadToDate,
                                             Map<StudyPeriodSettingKey, DateRange> ranges) {
        DateRange year = ranges.get(StudyPeriodSettingKey.YEAR_1_9);
        DateRange h1 = ranges.get(StudyPeriodSettingKey.H1_1_9);
        DateRange h2 = ranges.get(StudyPeriodSettingKey.H2_1_9);

        if (loadFromDate == null || loadToDate == null || year == null || h1 == null || h2 == null) {
            return StudyPeriod.YEAR;
        }
        if (loadFromDate.equals(year.startDate()) && loadToDate.equals(year.endDate())) {
            return StudyPeriod.YEAR;
        }
        if (loadFromDate.equals(h1.startDate()) && loadToDate.equals(h1.endDate())) {
            return StudyPeriod.H1;
        }
        if (loadFromDate.equals(h2.startDate()) && loadToDate.equals(h2.endDate())) {
            return StudyPeriod.H2;
        }
        if (h1.fullyContains(loadFromDate, loadToDate)) {
            return StudyPeriod.H1;
        }
        if (h2.fullyContains(loadFromDate, loadToDate)) {
            return StudyPeriod.H2;
        }
        return StudyPeriod.YEAR;
    }

    private StudyPeriod inferForHighSchool(int parallel,
                                           LocalDate loadFromDate,
                                           LocalDate loadToDate,
                                           Map<StudyPeriodSettingKey, DateRange> ranges) {
        StudyPeriodSettingKey h1Key = parallel >= 11 ? StudyPeriodSettingKey.H1_11 : StudyPeriodSettingKey.H1_10;
        StudyPeriodSettingKey h2Key = parallel >= 11 ? StudyPeriodSettingKey.H2_11 : StudyPeriodSettingKey.H2_10;
        DateRange h1 = ranges.get(h1Key);
        DateRange h2 = ranges.get(h2Key);
        if (loadFromDate == null || loadToDate == null || h1 == null || h2 == null) {
            return StudyPeriod.H1;
        }
        if (loadFromDate.equals(h1.startDate()) && loadToDate.equals(h1.endDate())) {
            return StudyPeriod.H1;
        }
        if (loadFromDate.equals(h2.startDate()) && loadToDate.equals(h2.endDate())) {
            return StudyPeriod.H2;
        }
        if (h1.fullyContains(loadFromDate, loadToDate)) {
            return StudyPeriod.H1;
        }
        if (h2.fullyContains(loadFromDate, loadToDate)) {
            return StudyPeriod.H2;
        }
        long overlapH1 = overlapDays(loadFromDate, loadToDate, h1.startDate(), h1.endDate());
        long overlapH2 = overlapDays(loadFromDate, loadToDate, h2.startDate(), h2.endDate());
        return overlapH2 > overlapH1 ? StudyPeriod.H2 : StudyPeriod.H1;
    }

    private long overlapDays(LocalDate fromA, LocalDate toA, LocalDate fromB, LocalDate toB) {
        if (fromA == null || toA == null || fromB == null || toB == null) {
            return 0;
        }
        LocalDate start = fromA.isAfter(fromB) ? fromA : fromB;
        LocalDate end = toA.isBefore(toB) ? toA : toB;
        if (end.isBefore(start)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void ensureDefaults() {
        if (repository.count() >= StudyPeriodSettingKey.values().length) {
            return;
        }

        createIfMissing(StudyPeriodSettingKey.YEAR_1_9, DEFAULT_YEAR_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_1_9, DEFAULT_YEAR_START, DEFAULT_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_1_9, DEFAULT_H2_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_10, DEFAULT_YEAR_START, DEFAULT_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_10, DEFAULT_H2_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_11, DEFAULT_YEAR_START, DEFAULT_11_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_11, DEFAULT_11_H2_START, DEFAULT_YEAR_END);
    }

    private void createIfMissing(StudyPeriodSettingKey key, LocalDate startDate, LocalDate endDate) {
        if (repository.findBySettingKey(key).isPresent()) {
            return;
        }
        StudyPeriodSetting entity = new StudyPeriodSetting();
        fillEntity(entity, key, startDate, endDate);
        repository.save(entity);
    }

    private void fillEntity(StudyPeriodSetting entity, StudyPeriodSettingKey key, LocalDate startDate, LocalDate endDate) {
        entity.setSettingKey(key);
        entity.setStudyPeriod(key.getStudyPeriod());
        entity.setParallelFrom(key.getParallelFrom());
        entity.setParallelTo(key.getParallelTo());
        entity.setDisplayName(key.getDisplayName());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setUpdatedAt(LocalDateTime.now());
    }
}