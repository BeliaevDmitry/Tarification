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
import java.util.*;
import java.util.stream.Collectors;

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
        return repository.findAllByOrderByParallelFromAscParallelToAscStudyPeriodAscDisplayNameAsc();
    }

    @Override
    public List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Передайте хотя бы одну настройку периода обучения");
        }
        ensureDefaults();

        List<StudyPeriodSetting> saved = new ArrayList<>();
        int customIndex = nextCustomIndex();
        for (StudyPeriodSettingRequest request : requests) {
            validateRequest(request);
            String settingKey = normalizeKey(request.getSettingKey());
            if (settingKey == null) {
                settingKey = String.format("CUSTOM_%02d", customIndex++);
            }

            StudyPeriodSetting entity = repository.findBySettingKey(settingKey).orElseGet(StudyPeriodSetting::new);
            entity.setSettingKey(settingKey);
            entity.setDisplayName(String.valueOf(request.getDisplayName() == null ? "" : request.getDisplayName()).trim());
            entity.setStudyPeriod(request.getStudyPeriod());
            entity.setParallelFrom(request.getParallelFrom());
            entity.setParallelTo(request.getParallelTo());
            entity.setStartDate(request.getStartDate());
            entity.setEndDate(request.getEndDate());
            entity.setUpdatedAt(LocalDateTime.now());
            saved.add(repository.save(entity));
        }
        return saved.stream()
                .sorted(Comparator.comparing(StudyPeriodSetting::getParallelFrom)
                        .thenComparing(StudyPeriodSetting::getParallelTo)
                        .thenComparing(StudyPeriodSetting::getStudyPeriod)
                        .thenComparing(StudyPeriodSetting::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Map<StudyPeriodSettingKey, DateRange> rangesByKey() {
        Map<String, StudyPeriodSetting> byCode = findAll().stream()
                .collect(Collectors.toMap(StudyPeriodSetting::getSettingKey, s -> s, (a, b) -> a));
        Map<StudyPeriodSettingKey, DateRange> result = new EnumMap<>(StudyPeriodSettingKey.class);
        for (StudyPeriodSettingKey key : StudyPeriodSettingKey.values()) {
            StudyPeriodSetting setting = byCode.get(key.name());
            if (setting != null) {
                result.put(key, new DateRange(setting.getStartDate(), setting.getEndDate()));
            }
        }
        return result;
    }

    @Override
    public DateRange resolveDateRange(String className, StudyPeriod studyPeriod) {
        ensureDefaults();
        int parallel = Optional.ofNullable(ClassNameNormalizer.extractParallel(className)).orElse(1);
        StudyPeriod effective = studyPeriod == null ? StudyPeriod.YEAR : studyPeriod;
        return findAll().stream()
                .filter(s -> s.getStudyPeriod() == effective)
                .filter(s -> parallel >= s.getParallelFrom() && parallel <= s.getParallelTo())
                .sorted(Comparator.comparingInt((StudyPeriodSetting s) -> s.getParallelTo() - s.getParallelFrom())
                        .thenComparing(StudyPeriodSetting::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> new DateRange(s.getStartDate(), s.getEndDate()))
                .findFirst()
                .orElseGet(() -> {
                    StudyPeriodSettingKey fallback = resolveKey(className, effective);
                    DateRange dr = rangesByKey().get(fallback);
                    if (dr == null) {
                        throw new IllegalArgumentException("Настройка периода не найдена: " + fallback);
                    }
                    return dr;
                });
    }

    @Override
    public StudyPeriod inferStudyPeriod(String className, LocalDate loadFromDate, LocalDate loadToDate) {
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        if (parallel == null) {
            return StudyPeriod.YEAR;
        }
        ensureDefaults();
        Map<StudyPeriodSettingKey, DateRange> ranges = rangesByKey();
        if (parallel >= 10) {
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

    private void ensureDefaults() {
        createIfMissing(StudyPeriodSettingKey.YEAR_1_9, DEFAULT_YEAR_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_1_9, DEFAULT_YEAR_START, DEFAULT_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_1_9, DEFAULT_H2_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_10, DEFAULT_YEAR_START, DEFAULT_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_10, DEFAULT_H2_START, DEFAULT_YEAR_END);
        createIfMissing(StudyPeriodSettingKey.H1_11, DEFAULT_YEAR_START, DEFAULT_11_H1_END);
        createIfMissing(StudyPeriodSettingKey.H2_11, DEFAULT_11_H2_START, DEFAULT_YEAR_END);
    }

    private void createIfMissing(StudyPeriodSettingKey key, LocalDate startDate, LocalDate endDate) {
        if (repository.findBySettingKey(key.name()).isPresent()) {
            return;
        }
        StudyPeriodSetting entity = new StudyPeriodSetting();
        entity.setSettingKey(key.name());
        entity.setStudyPeriod(key.getStudyPeriod());
        entity.setParallelFrom(key.getParallelFrom());
        entity.setParallelTo(key.getParallelTo());
        entity.setDisplayName(key.getDisplayName());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private int nextCustomIndex() {
        return findAll().stream()
                .map(StudyPeriodSetting::getSettingKey)
                .filter(Objects::nonNull)
                .filter(v -> v.startsWith("CUSTOM_"))
                .map(v -> v.substring("CUSTOM_".length()))
                .mapToInt(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (Exception ex) {
                        return 0;
                    }
                })
                .max()
                .orElse(0) + 1;
    }

    private String normalizeKey(String value) {
        String v = String.valueOf(value == null ? "" : value).trim();
        return v.isBlank() ? null : v.toUpperCase(Locale.ROOT);
    }

    private void validateRequest(StudyPeriodSettingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Пустая строка настройки периода");
        }
        if (request.getStudyPeriod() == null) {
            throw new IllegalArgumentException("studyPeriod обязателен");
        }
        if (request.getParallelFrom() == null || request.getParallelTo() == null) {
            throw new IllegalArgumentException("parallelFrom/parallelTo обязательны");
        }
        if (request.getParallelFrom() < 1 || request.getParallelTo() > 11 || request.getParallelFrom() > request.getParallelTo()) {
            throw new IllegalArgumentException("Некорректный диапазон классов");
        }
        if (request.getStartDate() == null || request.getEndDate() == null || request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Некорректные даты периода");
        }
        if (String.valueOf(request.getDisplayName() == null ? "" : request.getDisplayName()).trim().isBlank()) {
            throw new IllegalArgumentException("displayName обязателен");
        }
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
        if (loadFromDate.equals(year.startDate()) && loadToDate.equals(year.endDate())) return StudyPeriod.YEAR;
        if (loadFromDate.equals(h1.startDate()) && loadToDate.equals(h1.endDate())) return StudyPeriod.H1;
        if (loadFromDate.equals(h2.startDate()) && loadToDate.equals(h2.endDate())) return StudyPeriod.H2;
        if (h1.fullyContains(loadFromDate, loadToDate)) return StudyPeriod.H1;
        if (h2.fullyContains(loadFromDate, loadToDate)) return StudyPeriod.H2;
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
        if (loadFromDate.equals(h1.startDate()) && loadToDate.equals(h1.endDate())) return StudyPeriod.H1;
        if (loadFromDate.equals(h2.startDate()) && loadToDate.equals(h2.endDate())) return StudyPeriod.H2;
        if (h1.fullyContains(loadFromDate, loadToDate)) return StudyPeriod.H1;
        if (h2.fullyContains(loadFromDate, loadToDate)) return StudyPeriod.H2;
        long overlapH1 = overlapDays(loadFromDate, loadToDate, h1.startDate(), h1.endDate());
        long overlapH2 = overlapDays(loadFromDate, loadToDate, h2.startDate(), h2.endDate());
        return overlapH2 > overlapH1 ? StudyPeriod.H2 : StudyPeriod.H1;
    }

    private long overlapDays(LocalDate fromA, LocalDate toA, LocalDate fromB, LocalDate toB) {
        if (fromA == null || toA == null || fromB == null || toB == null) return 0;
        LocalDate start = fromA.isAfter(fromB) ? fromA : fromB;
        LocalDate end = toA.isBefore(toB) ? toA : toB;
        if (end.isBefore(start)) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
}
