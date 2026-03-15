package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.SubjectCatalogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SubjectCatalogServiceImpl implements SubjectCatalogService {

    private final SubjectCatalogRepository repository;

    @Override
    public SubjectCatalogEntry create(SubjectCreateRequest request) {
        String name = validateName(request);
        SubjectType type = validateType(request);

        return repository.findBySubjectNameAndSubjectType(name, type)
                .orElseGet(() -> {
                    SubjectCatalogEntry entry = new SubjectCatalogEntry();
                    entry.setSubjectName(name);
                    entry.setSubjectType(type);
                    return repository.save(entry);
                });
    }

    @Override
    public SubjectCatalogEntry update(Long id, SubjectCreateRequest request) {
        String name = validateName(request);
        SubjectType type = validateType(request);

        SubjectCatalogEntry existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        repository.findBySubjectNameAndSubjectType(name, type)
                .filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> {
                    throw new IllegalArgumentException("Предмет с таким названием и типом уже существует");
                });

        existing.setSubjectName(name);
        existing.setSubjectType(type);
        return repository.save(existing);
    }

    @Override
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Subject not found");
        }
        repository.deleteById(id);
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
                if (subjectName.isBlank() || subjectName.equalsIgnoreCase("предмет")) {
                    skipped++;
                    continue;
                }

                String typeRaw = cellValue(row.getCell(1));
                SubjectType type = parseType(typeRaw);
                if (type == null) {
                    skipped++;
                    continue;
                }

                String key = subjectName.trim().toLowerCase() + "|" + type.name();
                if (!seen.add(key)) {
                    skipped++;
                    continue;
                }

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

    @Override
    public Resource buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Предметы");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Предмет");
            header.createCell(1).setCellValue("Тип");

            List<SubjectCatalogEntry> rows = repository.findAll();
            if (rows.isEmpty()) {
                Row ex1 = sheet.createRow(1);
                ex1.createCell(0).setCellValue("Математика");
                ex1.createCell(1).setCellValue("1");

                Row ex2 = sheet.createRow(2);
                ex2.createCell(0).setCellValue("Разговоры о важном");
                ex2.createCell(1).setCellValue("2");
            } else {
                rows.sort(Comparator.comparing(SubjectCatalogEntry::getSubjectName, String.CASE_INSENSITIVE_ORDER));
                int idx = 1;
                for (SubjectCatalogEntry entry : rows) {
                    Row row = sheet.createRow(idx++);
                    row.createCell(0).setCellValue(entry.getSubjectName());
                    row.createCell(1).setCellValue(entry.getSubjectType() == SubjectType.EXTRACURRICULAR ? "2" : "1");
                }
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать шаблон", e);
        }
    }

    private String validateName(SubjectCreateRequest request) {
        if (request == null || request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        return request.getSubjectName().trim();
    }

    private SubjectType validateType(SubjectCreateRequest request) {
        if (request.getSubjectType() == null) {
            throw new IllegalArgumentException("subjectType is required");
        }
        return request.getSubjectType();
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
        if (v.contains("основ") || v.contains("формир") || v.contains("core") || v.contains("form")) {
            return SubjectType.CORE_FORMABLE;
        }
        if (v.equals("1")) return SubjectType.CORE_FORMABLE;
        if (v.equals("2")) return SubjectType.EXTRACURRICULAR;
        return null;
    }
}
