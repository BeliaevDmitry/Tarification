package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDirectoryServiceImpl implements TeacherDirectoryService {
    private static final String VACANCY_TEACHER = "Вакансия";

    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        int imported = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = findTeachersSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("Лист 'Педагоги' не найден");
            }

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String fio = getCellStringValue(row.getCell(0));
                if (fio.isBlank()) {
                    skipped++;
                    continue;
                }

                String normalized = fio.trim();
                if (normalized.equalsIgnoreCase("фио") || normalized.equalsIgnoreCase("педагог")) {
                    skipped++;
                    continue;
                }

                if (!seen.add(normalized.toLowerCase())) {
                    skipped++;
                    continue;
                }

                if (teacherDirectoryRepository.findByFioTeacher(normalized).isPresent()) {
                    skipped++;
                    continue;
                }

                TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                entry.setFioTeacher(normalized);
                teacherDirectoryRepository.save(entry);
                imported++;
            }

            log.info("Импорт педагогов завершен: imported={}, skipped={}", imported, skipped);
            return Map.of(
                    "status", "ok",
                    "imported", imported,
                    "skipped", skipped,
                    "sheet", sheet.getSheetName()
            );
        } catch (Exception e) {
            log.error("Ошибка импорта педагогов", e);
            throw new RuntimeException("Не удалось импортировать педагогов из Excel", e);
        }
    }

    @Override
    public TeacherDirectoryEntry create(TeacherCreateRequest request) {
        if (request == null || request.getFioTeacher() == null || request.getFioTeacher().isBlank()) {
            throw new IllegalArgumentException("fioTeacher is required");
        }

        String normalized = request.getFioTeacher().trim();
        String dative = normalizeOptional(request.getFioTeacherDative());
        return teacherDirectoryRepository.findByFioTeacher(normalized)
                .orElseGet(() -> {
                    TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                    entry.setFioTeacher(normalized);
                    entry.setFioTeacherDative(dative);
                    return teacherDirectoryRepository.save(entry);
                });
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry update(Long teacherId, TeacherUpdateRequest request) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (request != null) {
            entry.setFioTeacherDative(normalizeOptional(request.getFioTeacherDative()));
        }
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry markForDismissal(Long teacherId, LocalDate dismissalDate) {
        if (dismissalDate == null) {
            throw new IllegalArgumentException("dismissalDate is required");
        }

        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        entry.setDismissalDate(dismissalDate);

        TeacherDirectoryEntry vacancyTeacher = ensureVacancyTeacher();
        manualLoadEntryRepository.findByFioTeacherIgnoreCase(entry.getFioTeacher()).forEach(loadEntry -> {
            if (loadEntry.getLoadToDate() == null) {
                return;
            }
            if (loadEntry.getLoadToDate().isAfter(dismissalDate)) {
                LocalDate originalLoadToDate = loadEntry.getLoadToDate();
                loadEntry.setBackupLoadToDate(loadEntry.getLoadToDate());
                loadEntry.setLoadToDate(dismissalDate);
                loadEntry.setDismissalAdjusted(true);
                manualLoadEntryRepository.save(loadEntry);

                LocalDate vacancyFrom = dismissalDate.plusDays(1);
                if (!vacancyFrom.isAfter(originalLoadToDate) && !isLoadAlreadyAssigned(loadEntry, vacancyFrom, originalLoadToDate)) {
                    var vacancyEntry = new org.school.personalLoad.model.ManualLoadEntry();
                    vacancyEntry.setFioTeacher(vacancyTeacher.getFioTeacher());
                    vacancyEntry.setNumberSchoolBuilding(loadEntry.getNumberSchoolBuilding());
                    vacancyEntry.setSubjectName(loadEntry.getSubjectName());
                    vacancyEntry.setClassName(loadEntry.getClassName());
                    vacancyEntry.setLoad(loadEntry.getLoad());
                    vacancyEntry.setGroupNameEducationalPlan(loadEntry.getGroupNameEducationalPlan());
                    vacancyEntry.setGroupLoad(loadEntry.getGroupLoad());
                    vacancyEntry.setEducationLevel(loadEntry.getEducationLevel());
                    vacancyEntry.setStudyPeriod(loadEntry.getStudyPeriod());
                    vacancyEntry.setLoadFromDate(vacancyFrom);
                    vacancyEntry.setLoadToDate(originalLoadToDate);
                    manualLoadEntryRepository.save(vacancyEntry);
                }
            }
        });

        return teacherDirectoryRepository.save(entry);
    }


    @Override
    @Transactional
    public TeacherDirectoryEntry restore(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        manualLoadEntryRepository.findByFioTeacherIgnoreCase(entry.getFioTeacher()).forEach(loadEntry -> {
            if (!loadEntry.isDismissalAdjusted() || loadEntry.getBackupLoadToDate() == null) {
                return;
            }
            loadEntry.setLoadToDate(loadEntry.getBackupLoadToDate());
            loadEntry.setBackupLoadToDate(null);
            loadEntry.setDismissalAdjusted(false);
            manualLoadEntryRepository.save(loadEntry);
        });

        entry.setDismissalDate(null);
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    public void deleteById(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (manualLoadEntryRepository.existsByFioTeacherIgnoreCase(entry.getFioTeacher())) {
            throw new IllegalStateException("Педагог назначен на нагрузку. Сначала снимите нагрузку, затем удаляйте из справочника.");
        }

        teacherDirectoryRepository.delete(entry);
    }

    @Override
    public List<TeacherDirectoryEntry> findAll() {
        ensureVacancyTeacher();
        return teacherDirectoryRepository.findAll();
    }

    @Override
    public void clearAll() {
        teacherDirectoryRepository.deleteAll();
    }

    private Sheet findTeachersSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName().toLowerCase();
            if (name.contains("педагог")) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue()).trim();
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        yield String.valueOf((int) cell.getNumericCellValue()).trim();
                    } catch (Exception ignored) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private TeacherDirectoryEntry ensureVacancyTeacher() {
        return teacherDirectoryRepository.findByFioTeacher(VACANCY_TEACHER).orElseGet(() -> {
            TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
            entry.setFioTeacher(VACANCY_TEACHER);
            entry.setFioTeacherDative("Вакансии");
            return teacherDirectoryRepository.save(entry);
        });
    }

    private boolean isLoadAlreadyAssigned(org.school.personalLoad.model.ManualLoadEntry source,
                                          LocalDate fromDate,
                                          LocalDate toDate) {
        return manualLoadEntryRepository.findAll().stream().anyMatch(existing -> {
            if (existing.getId() != null && existing.getId().equals(source.getId())) {
                return false;
            }
            if (existing.getFioTeacher() == null || VACANCY_TEACHER.equalsIgnoreCase(existing.getFioTeacher())) {
                return false;
            }
            if (!Objects.equals(existing.getSubjectName(), source.getSubjectName())) return false;
            if (!Objects.equals(existing.getClassName(), source.getClassName())) return false;
            if (!Objects.equals(existing.getGroupNameEducationalPlan(), source.getGroupNameEducationalPlan())) return false;
            if (!Objects.equals(existing.getEducationLevel(), source.getEducationLevel())) return false;
            if (existing.getLoadFromDate() == null || existing.getLoadToDate() == null) return false;
            return !existing.getLoadFromDate().isAfter(toDate) && !existing.getLoadToDate().isBefore(fromDate);
        });
    }
}
