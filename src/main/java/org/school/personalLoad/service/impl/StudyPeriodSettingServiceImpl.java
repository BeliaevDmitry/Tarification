package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
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

    private static final Map<StudyPeriod, String> DISPLAY_NAMES = Map.of(
            StudyPeriod.YEAR, "1–9 классы · учебный год",
            StudyPeriod.H1, "10–11 классы · 1 полугодие",
            StudyPeriod.H2, "10–11 классы · 2 полугодие"
    );

    private final StudyPeriodSettingRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<StudyPeriodSetting> findAll() {
        ensureDefaults();
        List<StudyPeriodSetting> result = new ArrayList<>();
        for (StudyPeriod period : StudyPeriod.values()) {
            result.add(repository.findByStudyPeriod(period).orElseThrow());
        }
        return result;
    }

    @Override
    public List<StudyPeriodSetting> saveAll(List<StudyPeriodSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Передайте хотя бы один период обучения");
        }

        ensureDefaults();
        Map<StudyPeriod, StudyPeriodSettingRequest> byPeriod = new EnumMap<>(StudyPeriod.class);
        for (StudyPeriodSettingRequest request : requests) {
            if (request == null || request.getStudyPeriod() == null) {
                throw new IllegalArgumentException("studyPeriod is required");
            }
            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new IllegalArgumentException("Для периода " + request.getStudyPeriod() + " обязательны startDate и endDate");
            }
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new IllegalArgumentException("Для периода " + request.getStudyPeriod() + " startDate должен быть раньше или равен endDate");
            }
            byPeriod.put(request.getStudyPeriod(), request);
        }

        List<StudyPeriodSetting> updated = new ArrayList<>();
        for (StudyPeriod period : StudyPeriod.values()) {
            StudyPeriodSettingRequest request = byPeriod.get(period);
            if (request == null) {
                throw new IllegalArgumentException("Не передана настройка для периода " + period);
            }
            StudyPeriodSetting entity = repository.findByStudyPeriod(period).orElseGet(StudyPeriodSetting::new);
            entity.setStudyPeriod(period);
            entity.setDisplayName(DISPLAY_NAMES.get(period));
            entity.setStartDate(request.getStartDate());
            entity.setEndDate(request.getEndDate());
            entity.setUpdatedAt(LocalDateTime.now());
            updated.add(repository.save(entity));
        }
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<StudyPeriod, DateRange> rangesByPeriod() {
        Map<StudyPeriod, DateRange> result = new EnumMap<>(StudyPeriod.class);
        findAll().forEach(setting -> result.put(setting.getStudyPeriod(), new DateRange(setting.getStartDate(), setting.getEndDate())));
        return result;
    }

    private void ensureDefaults() {
        if (repository.count() >= StudyPeriod.values().length) {
            return;
        }

        LocalDate now = LocalDate.now();
        int startYear = now.getMonthValue() < 9 ? now.getYear() : now.getYear() + 1;
        LocalDate yearStart = LocalDate.of(startYear, 9, 1);
        LocalDate h1End = LocalDate.of(startYear, 12, 31);
        LocalDate h2Start = LocalDate.of(startYear + 1, 1, 1);
        LocalDate yearEnd = LocalDate.of(startYear + 1, 5, 31);

        createIfMissing(StudyPeriod.YEAR, yearStart, yearEnd);
        createIfMissing(StudyPeriod.H1, yearStart, h1End);
        createIfMissing(StudyPeriod.H2, h2Start, yearEnd);
    }

    private void createIfMissing(StudyPeriod period, LocalDate startDate, LocalDate endDate) {
        if (repository.findByStudyPeriod(period).isPresent()) {
            return;
        }
        StudyPeriodSetting entity = new StudyPeriodSetting();
        entity.setStudyPeriod(period);
        entity.setDisplayName(DISPLAY_NAMES.get(period));
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }
}
