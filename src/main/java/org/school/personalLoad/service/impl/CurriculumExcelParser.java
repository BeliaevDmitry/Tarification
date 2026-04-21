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
        String currentSubjectArea = "Без области";
        for (int rowIndex = requiredPartRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String areaCell = normalizeText(readMergedCell(sheet, rowIndex, 0));
            String subjectCell = normalizeText(readMergedCell(sheet, rowIndex, 1));
            if (!areaCell.isBlank() && !isPartMarker(areaCell) && !isServiceRow(areaCell)) {
                currentSubjectArea = areaCell;
            }
            String subject = subjectCell.isBlank() ? areaCell : subjectCell;
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
                ParsedHours parsedHours = parseHoursWithMarkers(readMergedCell(sheet, rowIndex, column.colIndex));
                if (parsedHours.hours() == null || parsedHours.hours().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                out.add(new CurriculumImportRow(
                        academicYear,
                        stage,
                        column.className,
                        column.classDirection,
                        currentSubjectArea,
                        normalizedSubject,
                        parsedHours.hours(),
                        column.studyPeriod,
                        currentPart,
                        parsedHours.subgroupRequired(),
                        parsedHours.metaGroup()
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
            String className = ClassNameNormalizer.normalize(resolveClassName(sheet, stage, classRow, col));
            String classDirection = normalizeText(readMergedCell(sheet, directionRow, col));
            String teacherName = teacherRow >= 0 ? normalizeText(readMergedCell(sheet, teacherRow, col)) : "";
            String periodRawDirect = readCell(sheet.getRow(periodRow) == null ? null : sheet.getRow(periodRow).getCell(col));
            StudyPeriod period = mapPeriod(periodRawDirect.isBlank() ? readMergedCell(sheet, periodRow, col) : periodRawDirect);
            className = resolveAmbiguousSooClassName(stage, className, period, prev);
            if (period == StudyPeriod.H2 && prev != null && prev.studyPeriod == StudyPeriod.H1) {
                className = prev.className;
            }

            // Для СОО в паре колонок (1П/2П) во второй колонке значения могут быть пустыми — наследуем слева.
            if (stage == CurriculumStage.SOO && prev != null) {
                if (className.isBlank()) className = prev.className;
                if (classDirection.isBlank()) classDirection = prev.classDirection;
                if (teacherName.isBlank()) teacherName = prev.teacherName;
                if (period == null || (period == prev.studyPeriod && className.equals(prev.className))) {
                    period = prev.studyPeriod == StudyPeriod.H1 ? StudyPeriod.H2 : StudyPeriod.H1;
                }
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

    private String resolveClassName(Sheet sheet, CurriculumStage stage, int classRow, int col) {
        String raw = normalizeText(readMergedCell(sheet, classRow, col));
        if (stage != CurriculumStage.SOO) {
            return raw;
        }
        if (!raw.contains(",") && !raw.contains(";")) {
            return raw;
        }

        List<String> tokens = Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (tokens.size() <= 1) {
            return raw;
        }

        CellRangeAddress range = mergedRangeForCell(sheet, classRow, col);
        if (range == null) {
            return tokens.get(0);
        }

        int width = range.getLastColumn() - range.getFirstColumn() + 1;
        int columnsPerClass = Math.max(1, width / tokens.size());
        int index = Math.min(tokens.size() - 1, Math.max(0, (col - range.getFirstColumn()) / columnsPerClass));
        return tokens.get(index);
    }

    private String resolveAmbiguousSooClassName(CurriculumStage stage, String className, StudyPeriod period, ColumnMeta prev) {
        if (stage != CurriculumStage.SOO) {
            return normalizeText(className);
        }
        String normalized = normalizeText(className);
        if (normalized.isBlank()) {
            return normalized;
        }
        if (!normalized.contains(",") && !normalized.contains(";")) {
            return normalized;
        }

        List<String> tokens = Arrays.stream(normalized.split("[,;]"))
                .map(ClassNameNormalizer::normalize)
                .filter(s -> !s.isBlank())
                .toList();
        if (tokens.isEmpty()) {
            return "";
        }
        if (tokens.size() == 1) {
            return tokens.get(0);
        }

        if (period == StudyPeriod.H2 && prev != null && tokens.contains(prev.className)) {
            return prev.className;
        }

        if (prev != null) {
            return tokens.stream().filter(token -> !token.equals(prev.className)).findFirst().orElse(tokens.get(0));
        }
        return tokens.get(0);
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
                "количество часов за год по учебному плану",
                "количество часов за год",
                "учебный план",
                "аудиторная нагрузка"
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

    private ParsedHours parseHoursWithMarkers(String raw) {
        String value = normalizeText(raw)
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
        boolean subgroupRequired = false;
        boolean metaGroup = false;
        if (value.endsWith("**")) {
            metaGroup = true;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("*")) {
            subgroupRequired = true;
            value = value.substring(0, value.length() - 1);
        }
        while (value.endsWith("*")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return new ParsedHours(null, subgroupRequired, metaGroup);
        }
        try {
            return new ParsedHours(new BigDecimal(value), subgroupRequired, metaGroup);
        } catch (Exception ignored) {
            return new ParsedHours(null, subgroupRequired, metaGroup);
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

    private CellRangeAddress mergedRangeForCell(Sheet sheet, int rowIndex, int colIndex) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(new CellAddress(rowIndex, colIndex))) {
                return range;
            }
        }
        return null;
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

    private record ParsedHours(BigDecimal hours, boolean subgroupRequired, boolean metaGroup) {}
}
