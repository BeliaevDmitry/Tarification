package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.StudyPeriod;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class CurriculumExcelParser {

    public List<CurriculumImportRow> parse(InputStream inputStream) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<CurriculumImportRow> rows = new ArrayList<>();
            parseSheet(workbook.getSheet("НОО"), CurriculumStage.NOO, rows);
            parseSheet(workbook.getSheet("ООО"), CurriculumStage.OOO, rows);
            parseSheet(workbook.getSheet("СОО"), CurriculumStage.SOO, rows);
            return rows;
        }
    }

    private void parseSheet(Sheet sheet, CurriculumStage stage, List<CurriculumImportRow> out) {
        if (sheet == null) return;
        String academicYear = "";
        String className = null;
        String classDirection = "";
        StudyPeriod period = StudyPeriod.YEAR;

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String c0 = readMergedCell(sheet, row, 0);
            String c1 = readMergedCell(sheet, row, 1);
            String c2 = readMergedCell(sheet, row, 2);
            String c3 = readMergedCell(sheet, row, 3);

            String line = String.join(" ", List.of(c0, c1, c2, c3)).trim();
            if (line.contains("учеб") && line.contains("год")) {
                academicYear = line;
            }
            if (c0.toLowerCase().contains("класс")) {
                className = ClassNameNormalizer.normalize(c1);
            }
            if (c0.toLowerCase().contains("направлен")) {
                classDirection = c1;
            }
            if (c0.toLowerCase().contains("период")) {
                period = mapPeriod(c1);
            }

            String subject = c1.isBlank() ? c2 : c1;
            if (subject == null || subject.isBlank()) continue;
            String lower = subject.toLowerCase();
            if (lower.contains("обязательная часть") || lower.contains("часть, формируемая")) continue;

            Integer hours = parseInt(c3);
            if (hours == null || hours <= 0 || className == null || className.isBlank()) continue;

            out.add(new CurriculumImportRow(academicYear, stage, className, classDirection, subject.trim(), hours, period));
        }
    }

    private StudyPeriod mapPeriod(String raw) {
        String v = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        if (v.contains("1П") || v.contains("1 П")) return StudyPeriod.H1;
        if (v.contains("2П") || v.contains("2 П")) return StudyPeriod.H2;
        return StudyPeriod.YEAR;
    }

    private Integer parseInt(String value) {
        try {
            String v = String.valueOf(value == null ? "" : value).trim().replace(',', '.');
            if (v.isBlank()) return null;
            return (int) Math.round(Double.parseDouble(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readMergedCell(Sheet sheet, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            for (CellRangeAddress range : sheet.getMergedRegions()) {
                if (range.isInRange(row.getRowNum(), col)) {
                    Row firstRow = sheet.getRow(range.getFirstRow());
                    if (firstRow == null) return "";
                    Cell first = firstRow.getCell(range.getFirstColumn());
                    return readCell(first);
                }
            }
            return "";
        }
        return readCell(cell);
    }

    private String readCell(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try { yield String.valueOf(cell.getNumericCellValue()); } catch (Exception ignored) { yield ""; }
                }
            }
            default -> "";
        };
    }
}
