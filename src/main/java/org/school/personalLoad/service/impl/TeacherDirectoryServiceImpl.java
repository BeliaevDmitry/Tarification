package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.TeacherCreateRequest;
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
@Transactional
public class TeacherDirectoryServiceImpl implements TeacherDirectoryService {

    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final AuditService auditService;

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

            Map<String, Object> result = Map.of(
                    "status", "ok",
                    "imported", imported,
                    "skipped", skipped,
                    "sheet", sheet.getSheetName()
            );
            auditService.log(ActionType.CREATE, "Teacher", null, null, result, "Teachers imported from Excel");
            log.info("Импорт педагогов завершен: imported={}, skipped={}", imported, skipped);
            return result;
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
        TeacherDirectoryEntry created = teacherDirectoryRepository.findByFioTeacher(normalized)
                .orElseGet(() -> {
                    TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                    entry.setFioTeacher(normalized);
                    return teacherDirectoryRepository.save(entry);
                });
        auditService.log(ActionType.CREATE, "Teacher", created.getId(), null, created, "Teacher created");
        return created;
    }

    @Override
    public TeacherDirectoryEntry markForDismissal(Long teacherId, LocalDate dismissalDate) {
        if (dismissalDate == null) {
            throw new IllegalArgumentException("dismissalDate is required");
        }

        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        TeacherDirectoryEntry oldValue = new TeacherDirectoryEntry();
        oldValue.setId(entry.getId());
        oldValue.setFioTeacher(entry.getFioTeacher());
        oldValue.setDismissalDate(entry.getDismissalDate());
        oldValue.setCreatedAt(entry.getCreatedAt());

        entry.setDismissalDate(dismissalDate);
        TeacherDirectoryEntry saved = teacherDirectoryRepository.save(entry);
        auditService.log(ActionType.UPDATE, "Teacher", saved.getId(), oldValue, saved, "Teacher marked for dismissal");
        return saved;
    }

    @Override
    public void deleteById(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (manualLoadEntryRepository.existsByFioTeacherIgnoreCase(entry.getFioTeacher())) {
            throw new IllegalStateException("Педагог назначен на нагрузку. Сначала снимите нагрузку, затем удаляйте из справочника.");
        }

        teacherDirectoryRepository.delete(entry);
        auditService.log(ActionType.DELETE, "Teacher", teacherId, entry, null, "Teacher deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherDirectoryEntry> findAll() {
        return teacherDirectoryRepository.findAll();
    }

    @Override
    public void clearAll() {
        List<TeacherDirectoryEntry> oldValue = teacherDirectoryRepository.findAll();
        teacherDirectoryRepository.deleteAll();
        auditService.log(ActionType.DELETE, "Teacher", null, oldValue, null, "All teachers removed");
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
}
