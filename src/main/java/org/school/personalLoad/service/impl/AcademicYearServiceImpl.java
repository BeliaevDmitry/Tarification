package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.AcademicYear;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.repository.AcademicYearRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final StudyPeriodSettingRepository studyPeriodSettingRepository;

    @Override
    public List<AcademicYear> findAll() {
        ensureCurrentYearExists();
        return academicYearRepository.findAllByOrderByStartYearAsc();
    }

    @Override
    public AcademicYear resolveCurrent() {
        ensureCurrentYearExists();
        int expectedStartYear = currentStartYearBySystemDate();
        return academicYearRepository.findByStartYear(expectedStartYear)
                .orElseGet(() -> findNearestByStartYear(expectedStartYear));
    }

    @Override
    public AcademicYear resolveByNameOrCurrent(String name) {
        ensureCurrentYearExists();
        if (name == null || name.isBlank()) {
            return resolveCurrent();
        }
        return academicYearRepository.findByName(name.trim())
                .orElseGet(this::resolveCurrent);
    }

    @Override
    public AcademicYear create(Integer startYear) {
        if (startYear == null || startYear < 2000 || startYear > 3000) {
            throw new IllegalArgumentException("Некорректный стартовый год");
        }
        if (academicYearRepository.findByStartYear(startYear).isPresent()) {
            throw new IllegalStateException("Учебный год уже существует: " + formatName(startYear));
        }
        AcademicYear created = buildYear(startYear);
        AcademicYear saved = academicYearRepository.save(created);
        cloneStudyPeriodsFromPreviousYearIfExists(startYear);
        return saved;
    }

    @Override
    public void delete(Long id) {
        AcademicYear existing = academicYearRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Учебный год не найден"));
        academicYearRepository.delete(existing);
    }

    @Override
    public String formatName(int startYear) {
        return startYear + "/" + (startYear + 1);
    }

    private void ensureCurrentYearExists() {
        if (academicYearRepository.count() > 0) {
            return;
        }
        academicYearRepository.save(buildYear(currentStartYearBySystemDate()));
    }

    private AcademicYear buildYear(int startYear) {
        AcademicYear year = new AcademicYear();
        year.setStartYear(startYear);
        year.setName(formatName(startYear));
        year.setStartDate(LocalDate.of(startYear, 8, 1));
        year.setEndDate(LocalDate.of(startYear + 1, 7, 31));
        return year;
    }

    private int currentStartYearBySystemDate() {
        LocalDate now = LocalDate.now();
        return now.getMonthValue() >= 8 ? now.getYear() : now.getYear() - 1;
    }

    private AcademicYear findNearestByStartYear(int targetYear) {
        return academicYearRepository.findAllByOrderByStartYearAsc().stream()
                .min(Comparator.comparingInt(value -> Math.abs(value.getStartYear() - targetYear)))
                .orElseGet(() -> academicYearRepository.save(buildYear(targetYear)));
    }

    private void cloneStudyPeriodsFromPreviousYearIfExists(int startYear) {
        AcademicYear prev = academicYearRepository.findByStartYear(startYear - 1).orElse(null);
        if (prev == null) return;
        List<StudyPeriodSetting> prevRows = studyPeriodSettingRepository.findAllByAcademicYearOrderByParallelFromAscParallelToAscIdAsc(prev.getName());
        if (prevRows.isEmpty()) return;
        if (!studyPeriodSettingRepository.findAllByAcademicYearOrderByParallelFromAscParallelToAscIdAsc(formatName(startYear)).isEmpty()) {
            return;
        }
        for (StudyPeriodSetting prevRow : prevRows) {
            StudyPeriodSetting copy = new StudyPeriodSetting();
            copy.setCode(prevRow.getCode() + "_" + startYear);
            copy.setAcademicYear(formatName(startYear));
            copy.setStudyPeriod(prevRow.getStudyPeriod());
            copy.setParallelFrom(prevRow.getParallelFrom());
            copy.setParallelTo(prevRow.getParallelTo());
            copy.setDisplayName(prevRow.getDisplayName());
            copy.setDefaultRule(prevRow.isDefaultRule());
            copy.setStartDate(prevRow.getStartDate().plusYears(1));
            copy.setEndDate(prevRow.getEndDate().plusYears(1));
            copy.setUpdatedAt(java.time.LocalDateTime.now());
            studyPeriodSettingRepository.save(copy);
        }
    }
}
