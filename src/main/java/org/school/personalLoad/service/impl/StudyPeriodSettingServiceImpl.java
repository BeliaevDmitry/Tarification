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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        return repository.findAll().stream()
                .sorted(Comparator.comparing(StudyPeriodSetting::getParallelFrom)
                        .thenComparing(StudyPeriodSetting::getParallelTo)
                        .thenComparing(StudyPeriodSetting::getStudyPeriod)
                        .thenComparing(StudyPeriodSetting::getId))
                .toList();
    }

    @Override
    public StudyPeriodSetting create(StudyPeriodSettingRequest request) {
        validateRequest(request, false);
        StudyPeriodSetting entity = new StudyPeriodSetting();
        fillEntity(entity, request);
        return repository.save(entity);
    }

    @Override
    public List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Передайте хотя бы одну настройку периода обучения");
        }
        for (StudyPeriodSettingRequest request : requests) {
            validateRequest(request, true);
            StudyPeriodSetting entity = repository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Период не найден: " + request.getId()));
            fillEntity(entity, request);
            repository.save(entity);
        }
        return findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyPeriodSetting> findAvailableForClass(String className) {
        ensureDefaults();
        int parallel = resolveParallel(className);
        return repository.findByParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(parallel, parallel);
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPeriodSetting resolveRuleForClassAndPeriod(String className, StudyPeriod studyPeriod) {
        int parallel = resolveParallel(className);
        StudyPeriod effectivePeriod = studyPeriod == null ? (parallel <= 9 ? StudyPeriod.YEAR : StudyPeriod.H1) : studyPeriod;
        List<StudyPeriodSetting> byType = repository.findByParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc(parallel, parallel, effectivePeriod);
        if (!byType.isEmpty()) {
            return byType.get(0);
        }
        List<StudyPeriodSetting> all = repository.findByParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(parallel, parallel);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Не найден период обучения для класса " + className);
        }
        return all.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<StudyPeriodSettingKey, DateRange> rangesByKey() {
        ensureDefaults();
        java.util.Map<StudyPeriodSettingKey, DateRange> result = new java.util.EnumMap<>(StudyPeriodSettingKey.class);
        for (StudyPeriodSettingKey key : StudyPeriodSettingKey.values()) {
            repository.findByCode(key.name()).ifPresent(row -> result.put(key, new DateRange(row.getStartDate(), row.getEndDate())));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DateRange resolveDateRange(String className, StudyPeriod studyPeriod) {
        StudyPeriodSetting setting = resolveRuleForClassAndPeriod(className, studyPeriod);
        return new DateRange(setting.getStartDate(), setting.getEndDate());
    }

    @Override
    public StudyPeriod inferStudyPeriod(String className, LocalDate loadFromDate, LocalDate loadToDate) {
        int parallel = resolveParallel(className);
        List<StudyPeriodSetting> options = findAvailableForClass(className);
        for (StudyPeriodSetting option : options) {
            DateRange range = new DateRange(option.getStartDate(), option.getEndDate());
            if (range.fullyContains(loadFromDate, loadToDate)) {
                return option.getStudyPeriod();
            }
        }
        return parallel <= 9 ? StudyPeriod.YEAR : StudyPeriod.H1;
    }

    private int resolveParallel(String className) {
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        return parallel == null ? 1 : parallel;
    }

    private void validateRequest(StudyPeriodSettingRequest request, boolean requireId) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (requireId && request.getId() == null) throw new IllegalArgumentException("id is required");
        if (request.getStudyPeriod() == null) throw new IllegalArgumentException("studyPeriod is required");
        if (request.getParallelFrom() == null || request.getParallelTo() == null) throw new IllegalArgumentException("parallelFrom/parallelTo is required");
        if (request.getParallelFrom() < 1 || request.getParallelTo() > 11 || request.getParallelFrom() > request.getParallelTo()) {
            throw new IllegalArgumentException("Некорректный диапазон классов");
        }
        if (request.getStartDate() == null || request.getEndDate() == null || request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Некорректные даты периода");
        }
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }

    private void fillEntity(StudyPeriodSetting entity, StudyPeriodSettingRequest request) {
        entity.setCode((request.getCode() == null || request.getCode().isBlank()) ? defaultCode(request) : request.getCode().trim());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setStudyPeriod(request.getStudyPeriod());
        entity.setParallelFrom(request.getParallelFrom());
        entity.setParallelTo(request.getParallelTo());
        entity.setDefaultRule(Boolean.TRUE.equals(request.getDefaultRule()));
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private String defaultCode(StudyPeriodSettingRequest request) {
        return (request.getStudyPeriod().name() + "_" + request.getParallelFrom() + "_" + request.getParallelTo() + "_" + System.currentTimeMillis())
                .toLowerCase(Locale.ROOT);
    }

    private void ensureDefaults() {
        if (repository.count() > 0) return;
        seed("YEAR_1_9", "1–9 классы · учебный год", StudyPeriod.YEAR, 1, 9, DEFAULT_YEAR_START, DEFAULT_YEAR_END);
        seed("H1_1_9", "1–9 классы · 1 полугодие", StudyPeriod.H1, 1, 9, DEFAULT_YEAR_START, DEFAULT_H1_END);
        seed("H2_1_9", "1–9 классы · 2 полугодие", StudyPeriod.H2, 1, 9, DEFAULT_H2_START, DEFAULT_YEAR_END);
        seed("H1_10", "10 класс · 1 полугодие", StudyPeriod.H1, 10, 10, DEFAULT_YEAR_START, DEFAULT_H1_END);
        seed("H2_10", "10 класс · 2 полугодие", StudyPeriod.H2, 10, 10, DEFAULT_H2_START, DEFAULT_YEAR_END);
        seed("YEAR_10", "10 класс · учебный год", StudyPeriod.YEAR, 10, 10, DEFAULT_YEAR_START, DEFAULT_YEAR_END);
        seed("H1_11", "11 класс · 1 полугодие", StudyPeriod.H1, 11, 11, DEFAULT_YEAR_START, DEFAULT_11_H1_END);
        seed("H2_11", "11 класс · 2 полугодие", StudyPeriod.H2, 11, 11, DEFAULT_11_H2_START, DEFAULT_YEAR_END);
        seed("YEAR_11", "11 класс · учебный год", StudyPeriod.YEAR, 11, 11, DEFAULT_YEAR_START, DEFAULT_YEAR_END);
    }

    private void seed(String code, String displayName, StudyPeriod period, int from, int to, LocalDate startDate, LocalDate endDate) {
        StudyPeriodSetting entity = new StudyPeriodSetting();
        entity.setCode(code);
        entity.setDisplayName(displayName);
        entity.setStudyPeriod(period);
        entity.setParallelFrom(from);
        entity.setParallelTo(to);
        entity.setDefaultRule(true);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }
}
