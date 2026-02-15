package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDirectoryServiceImpl implements TeacherDirectoryService {

    private final TeacherDirectoryRepository teacherDirectoryRepository;

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

                // пропускаем заголовки
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
    public List<TeacherDirectoryEntry> findAll() {
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
}
