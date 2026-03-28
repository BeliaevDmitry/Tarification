package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.StudyPeriod;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Component
public class CurriculumExcelParser {

    // В текущем шаблоне классы всегда начинаются с колонки C.
    private static final int CLASS_COLUMNS_START = 2; // C

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
        if (sheet == null) {
            return;
        }

        // 1) Год читаем из верхней части листа (обычно первые строки).
        String academicYear = extractAcademicYear(sheet);

        // 2) Ключевые строки шапки. Если шаблон изменится — правятся эти маркеры/поиск.
        int periodRow = findHeaderRowIndex(sheet, "период обучения");
        int directionRow = findHeaderRowIndex(sheet, "направленность класса");
        int teacherRow = findHeaderRowIndex(sheet, "фио классного руководителя");
        int classRow = findHeaderRowIndex(sheet, "класс");
        int requiredPartRow = findHeaderRowIndex(sheet, "обязательная часть");

        if (requiredPartRow < 0 || classRow < 0 || directionRow < 0 || periodRow < 0) {
            return;
        }

        int maxCol = findMaxCol(sheet, requiredPartRow);
        List<ColumnMeta> columns = extractColumnsMeta(sheet, stage, periodRow, directionRow, teacherRow, classRow, maxCol);
        if (columns.isEmpty()) {
            return;
        }

        // 3) После "Обязательная часть" идут строки предметов и часы по классам.
        CurriculumPart currentPart = CurriculumPart.CORE;
        for (int rowIndex = requiredPartRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String subject = extractSubject(sheet, rowIndex);
            if (subject.isBlank()) {
                continue;
            }

            String normalizedSubject = normalizeSubject(subject);
            if (isPartMarker(normalizedSubject)) {
                currentPart = mapPart(normalizedSubject, currentPart);
                continue;
            }
            if (isServiceRow(normalizedSubject)) {
                continue;
            }

            // 4) Идём по всем классам (колонкам) и берём часы на пересечении строка/колонка.
            for (ColumnMeta column : columns) {
                BigDecimal hours = parseHours(readMergedCell(sheet, rowIndex, column.colIndex));
                if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                out.add(new CurriculumImportRow(
                        academicYear,
                        stage,
                        column.className,
                        column.classDirection,
                        normalizedSubject,
                        hours,
                        column.studyPeriod,
                        currentPart
                ));
            }
        }
    }

    private List<ColumnMeta> extractColumnsMeta(Sheet sheet,
                                                CurriculumStage stage,
                                                int periodRow,
                                                int directionRow,
                                                int teacherRow,
                                                int classRow,
                                                int maxCol) {
        List<ColumnMeta> result = new ArrayList<>();
        ColumnMeta prev = null;

        for (int col = CLASS_COLUMNS_START; col <= maxCol; col++) {
            String className = ClassNameNormalizer.normalize(readMergedCell(sheet, classRow, col));
            String classDirection = normalizeText(readMergedCell(sheet, directionRow, col));
            String teacherName = teacherRow >= 0 ? normalizeText(readMergedCell(sheet, teacherRow, col)) : "";
            StudyPeriod period = mapPeriod(readMergedCell(sheet, periodRow, col));

            // Для СОО в паре колонок (1П/2П) во второй колонке значения могут быть пустыми — наследуем слева.
            if (stage == CurriculumStage.SOO && prev != null) {
                if (className.isBlank()) className = prev.className;
                if (classDirection.isBlank()) classDirection = prev.classDirection;
                if (teacherName.isBlank()) teacherName = prev.teacherName;
            }

            if (className.isBlank()) {
                continue;
            }

            if (period == null) {
                period = StudyPeriod.YEAR;
            }

            ColumnMeta meta = new ColumnMeta(col, className, classDirection, teacherName, period);
            result.add(meta);
            prev = meta;
        }

        return result;
    }

    private String extractAcademicYear(Sheet sheet) {
        for (int row = 0; row <= Math.min(sheet.getLastRowNum(), 20); row++) {
            for (int col = 0; col <= 8; col++) {
                String value = normalizeText(readMergedCell(sheet, row, col)).toLowerCase(Locale.ROOT);
                if (value.contains("учеб") && value.contains("год")) {
                    return normalizeText(readMergedCell(sheet, row, col));
                }
            }
        }
        return "";
    }

    private int findHeaderRowIndex(Sheet sheet, String marker) {
        String m = normalizeText(marker).toLowerCase(Locale.ROOT);

        // Основной источник: колонка A, т.к. в вашем шаблоне маркеры именно там.
        for (int row = 0; row <= sheet.getLastRowNum(); row++) {
            String a = normalizeText(readMergedCell(sheet, row, 0)).toLowerCase(Locale.ROOT);
            if (a.equals(m) || a.startsWith(m)) {
                return row;
            }
        }

        // Fallback: поиск в первых двух колонках для нестандартных файлов.
        for (int row = 0; row <= sheet.getLastRowNum(); row++) {
            for (int col = 0; col <= 1; col++) {
                String value = normalizeText(readMergedCell(sheet, row, col)).toLowerCase(Locale.ROOT);
                if (value.equals(m) || value.startsWith(m)) {
                    return row;
                }
            }
        }
        return -1;
    }

    private int findMaxCol(Sheet sheet, int rowIndex) {
        int max = CLASS_COLUMNS_START;
        for (int row = 0; row <= Math.min(sheet.getLastRowNum(), Math.max(rowIndex + 10, 40)); row++) {
            Row r = sheet.getRow(row);
            if (r != null && r.getLastCellNum() > max) {
                max = Math.max(max, r.getLastCellNum() - 1);
            }
        }
        return max;
    }

    private String extractSubject(Sheet sheet, int rowIndex) {
        String a = normalizeText(readMergedCell(sheet, rowIndex, 0));
        String b = normalizeText(readMergedCell(sheet, rowIndex, 1));
        return b.isBlank() ? a : b;
    }

    private boolean isPartMarker(String subject) {
        String value = normalizeText(subject).toLowerCase(Locale.ROOT);
        return value.contains("обязательная часть")
                || value.contains("часть, формируемая участниками образовательных отношений")
                || value.contains("внеурочная деятельность");
    }

    private CurriculumPart mapPart(String marker, CurriculumPart fallback) {
        String value = normalizeText(marker).toLowerCase(Locale.ROOT);
        if (value.contains("обязательная часть")) return CurriculumPart.CORE;
        if (value.contains("формируемая")) return CurriculumPart.FORMABLE;
        if (value.contains("внеурочная")) return CurriculumPart.EXTRACURRICULAR;
        return fallback;
    }

    private boolean isServiceRow(String subject) {
        String value = normalizeText(subject).toLowerCase(Locale.ROOT);
        if (value.isBlank()) return true;

        List<String> markers = List.of(
                "обязательная часть",
                "часть, формируемая участниками образовательных отношений",
                "внеурочная деятельность",
                "итого",
                "всего",
                "максимально допустим",
                "недельная нагрузка",
                "количество учебных недель",
                "учебный план",
                "аудиторная нагрузка",
                "Количество учебных недель",
                "Количество часов за год по учебному плану"
        );

        return markers.stream().anyMatch(value::contains);
    }

    private StudyPeriod mapPeriod(String raw) {
        String value = normalizeText(raw).toUpperCase(Locale.ROOT).replace(" ", "");
        if (value.isBlank()) return StudyPeriod.YEAR;
        if (value.contains("1П")) return StudyPeriod.H1;
        if (value.contains("2П")) return StudyPeriod.H2;
        return StudyPeriod.YEAR;
    }

    private BigDecimal parseHours(String raw) {
        String value = normalizeText(raw)
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    // Универсальное чтение merged cells: если ячейка пуста, берём верхнюю-левую ячейку merge-диапазона.
    private String readMergedCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(colIndex);
        String direct = readCell(cell);
        if (!direct.isBlank()) {
            return direct;
        }

        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(new CellAddress(rowIndex, colIndex))) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                Cell firstCell = firstRow == null ? null : firstRow.getCell(range.getFirstColumn());
                return readCell(firstCell);
            }
        }
        return "";
    }

    private boolean isMergedCell(Sheet sheet, int rowIndex, int colIndex) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(new CellAddress(rowIndex, colIndex))) {
                return true;
            }
        }
        return false;
    }

    private String readCell(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> normalizeText(cell.getStringCellValue());
            case NUMERIC -> normalizeText(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> readFormulaCell(cell);
            default -> "";
        };
    }

    private String readFormulaCell(Cell cell) {
        try {
            return normalizeText(cell.getStringCellValue());
        } catch (Exception ignored) {
            try {
                return normalizeText(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
            } catch (Exception ignored2) {
                return "";
            }
        }
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeSubject(String subject) {
        return normalizeText(subject);
    }

    private static class ColumnMeta {
        private final int colIndex;
        private final String className;
        private final String classDirection;
        private final String teacherName;
        private final StudyPeriod studyPeriod;

        private ColumnMeta(int colIndex, String className, String classDirection, String teacherName, StudyPeriod studyPeriod) {
            this.colIndex = colIndex;
            this.className = className;
            this.classDirection = classDirection;
            this.teacherName = teacherName;
            this.studyPeriod = studyPeriod;
        }
    }
}
