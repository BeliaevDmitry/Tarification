package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.SubjectCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SubjectCatalogServiceImpl implements SubjectCatalogService {

    private final SubjectCatalogRepository repository;

    @Override
    public SubjectCatalogEntry create(SubjectCreateRequest request) {
        if (request == null || request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getSubjectType() == null) {
            throw new IllegalArgumentException("subjectType is required");
        }

        String name = request.getSubjectName().trim();
        return repository.findBySubjectNameAndSubjectType(name, request.getSubjectType())
                .orElseGet(() -> {
                    SubjectCatalogEntry entry = new SubjectCatalogEntry();
                    entry.setSubjectName(name);
                    entry.setSubjectType(request.getSubjectType());
                    return repository.save(entry);
                });
    }

    @Override
    public List<SubjectCatalogEntry> findAll() {
        return repository.findAll();
    }

    @Override
    public void clearAll() {
        repository.deleteAll();
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        int imported = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Лист с предметами не найден");
            }

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String subjectName = cellValue(row.getCell(0));
                if (subjectName.isBlank()) { skipped++; continue; }
                if (subjectName.equalsIgnoreCase("предмет")) { skipped++; continue; }

                String typeRaw = cellValue(row.getCell(1));
                SubjectType type = parseType(typeRaw);
                if (type == null) { skipped++; continue; }

                String key = subjectName.trim().toLowerCase() + "|" + type.name();
                if (!seen.add(key)) { skipped++; continue; }

                if (repository.findBySubjectNameAndSubjectType(subjectName.trim(), type).isPresent()) {
                    skipped++;
                    continue;
                }

                SubjectCatalogEntry entry = new SubjectCatalogEntry();
                entry.setSubjectName(subjectName.trim());
                entry.setSubjectType(type);
                repository.save(entry);
                imported++;
            }

            return Map.of("status", "ok", "imported", imported, "skipped", skipped);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать предметы", e);
        }
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue()).trim();
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    private SubjectType parseType(String value) {
        String v = String.valueOf(value == null ? "" : value).trim().toLowerCase();
        if (v.isBlank()) return SubjectType.CORE_FORMABLE;
        if (v.contains("внеур")) return SubjectType.EXTRACURRICULAR;
        if (v.contains("основ") || v.contains("формир") || v.contains("core") || v.contains("form")) return SubjectType.CORE_FORMABLE;
        if (v.equals("1")) return SubjectType.CORE_FORMABLE;
        if (v.equals("2")) return SubjectType.EXTRACURRICULAR;
        return null;
    }
}
