package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.StudyPeriod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class CurriculumExcelImportParser {

    private static final List<String> SERVICE_ROWS = List.of(
            "обязательная часть",
            "часть, формируемая участниками образовательных отношений",
            "внеурочная деятельность",
            "итого",
            "всего",
            "максимально допустим",
            "недельная нагрузка",
            "учебный план",
            "аудиторная нагрузка"
    );

    List<ImportedCurriculumRow> parse(Workbook workbook) {
        List<ImportedCurriculumRow> result = new ArrayList<>();
        for (Sheet sheet : workbook) {
            String stage = detectStage(sheet.getSheetName());
            if (stage == null) {
                continue;
            }
            result.addAll(parseStageSheet(sheet, stage));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Не найдены листы НОО, ООО или СОО для импорта учебного плана");
        }
        return result;
    }

    private List<ImportedCurriculumRow> parseStageSheet(Sheet sheet, String stage) {
        String academicYear = detectAcademicYear(sheet);
        HeaderMarkers markers = detectMarkers(sheet);
        List<ClassColumn> classColumns = detectClassColumns(sheet, markers, stage);
        if (classColumns.isEmpty()) {
            throw new IllegalArgumentException("На листе " + sheet.getSheetName() + " не найдены колонки классов");
        }

        List<ImportedCurriculumRow> rows = new ArrayList<>();
        CurriculumPart currentPart = CurriculumPart.CORE;
        for (int rowIndex = markers.firstDataRowIndex(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String subjectName = resolveSubjectName(sheet, rowIndex);
            String normalizedSubject = normalize(subjectName);
            if (normalizedSubject.isBlank()) {
                continue;
            }

            CurriculumPart partMarker = detectPartMarker(normalizedSubject);
            if (partMarker != null) {
                currentPart = partMarker;
                continue;
            }
            if (isServiceRow(normalizedSubject)) {
                continue;
            }

            for (ClassColumn classColumn : classColumns) {
                String rawHours = readCell(row.getCell(classColumn.columnIndex()));
                Double plannedHours = parseHours(rawHours);
                if (plannedHours == null || plannedHours <= 0) {
                    continue;
                }
                rows.add(new ImportedCurriculumRow(
                        academicYear,
                        stage,
                        classColumn.className(),
                        classColumn.classDirection(),
                        classColumn.classTeacher(),
                        subjectName.trim(),
                        currentPart,
                        classColumn.studyPeriod(),
                        plannedHours
                ));
            }
        }
        return rows;
    }

    private HeaderMarkers detectMarkers(Sheet sheet) {
        Integer periodRow = null;
        Integer directionRow = null;
        Integer teacherRow = null;
        Integer classRow = null;
        Integer mandatoryPartRow = null;

        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 40); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                String value = normalize(readCell(cell)).toLowerCase(Locale.ROOT);
                if (value.contains("период обучения")) {
                    periodRow = i;
                } else if (value.contains("направленность класса")) {
                    directionRow = i;
                } else if (value.contains("фио классного руководителя")) {
                    teacherRow = i;
                } else if (value.equals("класс") || value.contains("класс")) {
                    classRow = i;
                } else if (value.contains("обязательная часть")) {
                    mandatoryPartRow = i;
                }
            }
        }

        if (periodRow == null || directionRow == null || teacherRow == null || classRow == null || mandatoryPartRow == null) {
            throw new IllegalArgumentException("Не удалось определить строки-маркеры шапки учебного плана на листе " + sheet.getSheetName());
        }
        return new HeaderMarkers(periodRow, directionRow, teacherRow, classRow, mandatoryPartRow + 1);
    }

    private List<ClassColumn> detectClassColumns(Sheet sheet, HeaderMarkers markers, String stage) {
        List<ClassColumn> columns = new ArrayList<>();
        Row classRow = sheet.getRow(markers.classRowIndex());
        Row periodRow = sheet.getRow(markers.periodRowIndex());
        Row directionRow = sheet.getRow(markers.directionRowIndex());
        Row teacherRow = sheet.getRow(markers.teacherRowIndex());
        String previousClassName = "";
        String previousDirection = "";
        String previousTeacher = "";

        for (int columnIndex = 2; columnIndex <= classRow.getLastCellNum(); columnIndex++) {
            String className = normalize(readCell(classRow.getCell(columnIndex)));
            String classDirection = normalize(readCell(directionRow.getCell(columnIndex)));
            String classTeacher = normalize(readCell(teacherRow.getCell(columnIndex)));
            String rawPeriod = normalize(readCell(periodRow.getCell(columnIndex)));

            if ("SOO".equals(stage)) {
                if (className.isBlank()) {
                    className = previousClassName;
                }
                if (classDirection.isBlank()) {
                    classDirection = previousDirection;
                }
                if (classTeacher.isBlank()) {
                    classTeacher = previousTeacher;
                }
            }

            if (className.isBlank()) {
                continue;
            }

            StudyPeriod studyPeriod = parseStudyPeriod(rawPeriod, stage);
            columns.add(new ClassColumn(
                    columnIndex,
                    ClassNameNormalizer.normalize(className),
                    classDirection.isBlank() ? "Не указана" : classDirection,
                    classTeacher,
                    studyPeriod
            ));

            previousClassName = className;
            previousDirection = classDirection.isBlank() ? previousDirection : classDirection;
            previousTeacher = classTeacher.isBlank() ? previousTeacher : classTeacher;
        }
        return columns;
    }

    private String resolveSubjectName(Sheet sheet, int rowIndex) {
        String mergedA = readMergedValue(sheet, rowIndex, 0);
        if (!normalize(mergedA).isBlank()) {
            return mergedA;
        }
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        String fromB = readCell(row.getCell(1));
        if (!normalize(fromB).isBlank()) {
            return fromB;
        }
        return readCell(row.getCell(0));
    }

    private CurriculumPart detectPartMarker(String subjectName) {
        String normalized = normalize(subjectName).toLowerCase(Locale.ROOT);
        if (normalized.contains("обязательная часть")) {
            return CurriculumPart.CORE;
        }
        if (normalized.contains("формируем")) {
            return CurriculumPart.FORMABLE;
        }
        if (normalized.contains("внеурочная")) {
            return CurriculumPart.EXTRACURRICULAR;
        }
        return null;
    }

    private boolean isServiceRow(String subjectName) {
        String normalized = normalize(subjectName).toLowerCase(Locale.ROOT);
        return SERVICE_ROWS.stream().anyMatch(normalized::contains);
    }

    private String detectAcademicYear(Sheet sheet) {
        Pattern pattern = Pattern.compile("(20\\d{2})\\s*[/-]\\s*(20\\d{2})");
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 12); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                String value = normalize(readCell(cell)).toLowerCase(Locale.ROOT);
                if (value.contains("учеб") && value.contains("год")) {
                    Matcher matcher = pattern.matcher(value.replace('–', '-').replace('—', '-'));
                    if (matcher.find()) {
                        return matcher.group(1) + "-" + matcher.group(2);
                    }
                    return readCell(cell);
                }
            }
        }
        return "";
    }

    private String detectStage(String sheetName) {
        String normalized = normalize(sheetName).toUpperCase(Locale.ROOT);
        if (normalized.contains("НОО")) {
            return "NOO";
        }
        if (normalized.contains("ООО")) {
            return "OOO";
        }
        if (normalized.contains("СОО")) {
            return "SOO";
        }
        return null;
    }

    private StudyPeriod parseStudyPeriod(String rawValue, String stage) {
        String normalized = normalize(rawValue).toUpperCase(Locale.ROOT);
        if (!"SOO".equals(stage)) {
            return StudyPeriod.YEAR;
        }
        if (normalized.contains("1П")) {
            return StudyPeriod.H1;
        }
        if (normalized.contains("2П")) {
            return StudyPeriod.H2;
        }
        return StudyPeriod.YEAR;
    }

    private Double parseHours(String rawValue) {
        String normalized = normalize(rawValue).replace(" ", "").replace(",", ".");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String readMergedValue(Sheet sheet, int rowIndex, int columnIndex) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(rowIndex, columnIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                if (firstRow == null) {
                    return "";
                }
                return readCell(firstRow.getCell(region.getFirstColumn()));
            }
        }
        Row row = sheet.getRow(rowIndex);
        return row == null ? "" : readCell(row.getCell(columnIndex));
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    record ImportedCurriculumRow(String academicYear,
                                 String stage,
                                 String className,
                                 String classDirection,
                                 String classTeacher,
                                 String subjectName,
                                 CurriculumPart curriculumPart,
                                 StudyPeriod studyPeriod,
                                 Double plannedHours) {
    }

    private record HeaderMarkers(int periodRowIndex,
                                 int directionRowIndex,
                                 int teacherRowIndex,
                                 int classRowIndex,
                                 int firstDataRowIndex) {
    }

    private record ClassColumn(int columnIndex,
                               String className,
                               String classDirection,
                               String classTeacher,
                               StudyPeriod studyPeriod) {
    }
}
