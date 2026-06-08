package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.SubjectLevelCoefficientRequest;
import org.school.personalLoad.model.EducationStage;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;
import org.school.personalLoad.service.SubjectLevelCoefficientService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SubjectLevelCoefficientServiceImpl implements SubjectLevelCoefficientService {

    private final SubjectLevelCoefficientRepository repository;

    @Override
    public SubjectLevelCoefficientEntry save(SubjectLevelCoefficientRequest request) {
        String subjectName = normalizeSubjectName(request == null ? null : request.getSubjectName());
        EducationStage stage = request == null ? null : request.getEducationStage();
        if (stage == null) {
            throw new IllegalArgumentException("Уровень обучения обязателен");
        }
        BigDecimal coefficient = resolveCoefficient(request.getCoefficient());
        SubjectLevelCoefficientEntry entry = repository
                .findBySubjectNameIgnoreCaseAndEducationStage(subjectName, stage)
                .orElseGet(SubjectLevelCoefficientEntry::new);
        entry.setSubjectName(subjectName);
        entry.setEducationStage(stage);
        entry.setCoefficient(coefficient);
        return repository.save(entry);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public List<SubjectLevelCoefficientEntry> findAll() {
        return repository.findAll();
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
                throw new IllegalArgumentException("Лист с коэффициентами не найден");
            }
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String subjectWithStage = cellValue(row.getCell(0));
                if (subjectWithStage.isBlank() || subjectWithStage.equalsIgnoreCase("предмет")) {
                    skipped++;
                    continue;
                }
                BigDecimal coefficient = parseDecimal(cellValue(row.getCell(1)));
                if (coefficient == null) {
                    skipped++;
                    continue;
                }
                SubjectStage subjectStage = parseSubjectStage(subjectWithStage);
                BigDecimal resolvedCoefficient = resolveCoefficient(coefficient);
                for (EducationStage stage : subjectStage.stages()) {
                    String key = subjectStage.subjectName().toLowerCase(Locale.ROOT) + "|" + stage.name();
                    if (!seen.add(key)) {
                        skipped++;
                        continue;
                    }
                    SubjectLevelCoefficientEntry entry = repository
                            .findBySubjectNameIgnoreCaseAndEducationStage(subjectStage.subjectName(), stage)
                            .orElseGet(SubjectLevelCoefficientEntry::new);
                    entry.setSubjectName(subjectStage.subjectName());
                    entry.setEducationStage(stage);
                    entry.setCoefficient(resolvedCoefficient);
                    repository.save(entry);
                    imported++;
                }
            }
            return Map.of("status", "ok", "imported", imported, "skipped", skipped);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать коэффициенты предметов", e);
        }
    }

    @Override
    public Resource exportWorkbook() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Коэффициенты");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Предмет");
            header.createCell(1).setCellValue("Для расчета");
            List<SubjectLevelCoefficientEntry> rows = new ArrayList<>(repository.findAll());
            rows.sort(Comparator.comparing(SubjectLevelCoefficientEntry::getSubjectName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SubjectLevelCoefficientEntry::getEducationStage));
            int rowNum = 1;
            for (SubjectLevelCoefficientEntry entry : rows) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(subjectStageLabel(entry));
                row.createCell(1).setCellValue(resolveCoefficient(entry.getCoefficient()).stripTrailingZeros().toPlainString());
            }
            for (int i = 0; i <= 1; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось экспортировать коэффициенты предметов", e);
        }
    }

    private SubjectStage parseSubjectStage(String value) {
        String normalized = normalizeSubjectName(value);
        for (EducationStage stage : EducationStage.values()) {
            String suffix = " " + stageLabel(stage);
            if (normalized.toUpperCase(Locale.ROOT).endsWith(suffix)) {
                String subjectName = normalizeSubjectName(normalized.substring(0, normalized.length() - suffix.length()));
                return new SubjectStage(subjectName, List.of(stage));
            }
        }
        return new SubjectStage(normalized, List.of(EducationStage.NOO, EducationStage.OOO, EducationStage.SOO));
    }

    private String subjectStageLabel(SubjectLevelCoefficientEntry entry) {
        return normalizeSubjectName(entry.getSubjectName()) + " " + stageLabel(entry.getEducationStage());
    }

    private String normalizeSubjectName(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Предмет обязателен");
        }
        return normalized;
    }

    private BigDecimal resolveCoefficient(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ONE;
        return value;
    }

    private BigDecimal parseDecimal(String value) {
        String normalized = value == null ? "" : value.trim().replace(',', '.');
        if (normalized.isBlank()) return null;
        try { return new BigDecimal(normalized); }
        catch (Exception ignored) { return null; }
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield ""; }
            }
            default -> "";
        };
    }

    private record SubjectStage(String subjectName, List<EducationStage> stages) {}

    private String stageLabel(EducationStage stage) {
        if (stage == EducationStage.NOO) return "НОО";
        if (stage == EducationStage.OOO) return "ООО";
        if (stage == EducationStage.SOO) return "СОО";
        return "";
    }
}
