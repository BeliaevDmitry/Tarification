package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.repository.AcademicYearRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;

    @Override
    public List<AcademicYearConfig> findAll() {
        List<AcademicYearConfig> years = academicYearRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicYearConfig::getCode))
                .toList();
        if (years.isEmpty()) {
            create(currentByDate());
            years = academicYearRepository.findAll().stream()
                    .sorted(Comparator.comparing(AcademicYearConfig::getCode))
                    .toList();
        }
        return years;
    }

    @Override
    public AcademicYearConfig create(String code) {
        String normalized = normalizeCode(code);
        if (academicYearRepository.existsByCode(normalized)) {
            throw new IllegalArgumentException("Учебный год уже существует: " + normalized);
        }
        AcademicYearConfig entity = new AcademicYearConfig();
        entity.setCode(normalized);
        return academicYearRepository.save(entity);
    }

    @Override
    public void delete(Long id) {
        academicYearRepository.deleteById(id);
    }

    @Override
    public String resolveRequestedOrDefault(String requestedCode) {
        List<AcademicYearConfig> years = findAll();
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            boolean exists = years.stream().anyMatch(y -> y.getCode().equals(normalized));
            if (exists) {
                return normalized;
            }
        }

        String current = currentByDate();
        boolean currentExists = years.stream().anyMatch(y -> y.getCode().equals(current));
        if (currentExists) {
            return current;
        }

        return years.stream()
                .map(AcademicYearConfig::getCode)
                .filter(code -> code.compareTo(current) > 0)
                .sorted()
                .findFirst()
                .orElseGet(() -> years.stream().map(AcademicYearConfig::getCode).max(String::compareTo).orElse(current));
    }

    @Override
    public String currentByDate() {
        LocalDate now = LocalDate.now();
        int startYear = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    @Override
    @Transactional
    public AcademicYearConfig markContinuityApplied(String code) {
        String normalized = normalizeCode(code);
        AcademicYearConfig year = academicYearRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Учебный год не найден: " + normalized));
        year.setContinuityApplied(true);
        return academicYearRepository.save(year);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        String normalized = code.trim().replace('\\', '/');
        if (!normalized.matches("\\d{4}/\\d{4}")) {
            throw new IllegalArgumentException("Формат учебного года должен быть YYYY/YYYY");
        }
        int from = Integer.parseInt(normalized.substring(0, 4));
        int to = Integer.parseInt(normalized.substring(5));
        if (to != from + 1) {
            throw new IllegalArgumentException("Учебный год должен быть последовательным, например 2026/2027");
        }
        return normalized;
    }
}
