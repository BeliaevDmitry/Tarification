package org.school.personalLoad.service;

import org.apache.poi.ss.formula.eval.NotImplementedException;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataReaderService {

    public DataReaderService(FormulaEvaluator formulaEvaluator) {
    }

    public List<TarifficationPerson> analyzeSheet(Sheet sheet) throws IOException {
        List<TarifficationPerson> tarifficationList = new ArrayList<>();

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row teacherRow = sheet.getRow(numberCurrentRow);
            if (teacherRow == null) continue;

            int numberColumnLoad = 12;
            int numberRowLoadClass = 1;

            String subjectName = getCellValueAsString(teacherRow.getCell(0)).trim();
            String fioTeacher = getCellValueAsString(teacherRow.getCell(1)).trim();
            int teacherLoadInRow = getCellValueAsInt(teacherRow.getCell(2));

            if (fioTeacher.equals("по УП") || fioTeacher.equals("выставлено выше") ||
                    fioTeacher.equals("выставлено") || fioTeacher.equals("количество групп")) {
                continue;
            }

            if (!subjectName.isEmpty() || !fioTeacher.isEmpty() || teacherLoadInRow > 0) {
                for (int currentColumn = numberColumnLoad; currentColumn <= teacherRow.getLastCellNum(); currentColumn++) {
                    Cell currentCell = teacherRow.getCell(currentColumn);
                    if (currentCell == null) continue;

                    int currentLoad = getCellValueAsInt(currentCell);
                    String numberSchoolBuilding = sheet.getSheetName();

                    if (currentLoad > 0) {
                        Row classRow = sheet.getRow(numberRowLoadClass);
                        if (classRow == null) continue;

                        Cell classCell = classRow.getCell(currentColumn);
                        String className = getCellValueAsString(classCell);
                        if (className.equals("1-4кл") || className.equals("5-9кл") || className.equals("сш")) {
                            continue;
                        }

                        tarifficationList.add(new TarifficationPerson(
                                fioTeacher, numberSchoolBuilding, subjectName, className, currentLoad
                        ));
                    }
                }
            }
        }
        return tarifficationList;
    }

    public List<SubjectWithGroup> searchGroup(Sheet sheet) {
        List<SubjectWithGroup> subjectWithGroupList = new ArrayList<>();
        String numberSchoolBuilding = sheet.getSheetName();

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row currentRow = sheet.getRow(numberCurrentRow);
            if (currentRow == null) continue;

            int numberColumnLoad = 12;
            int numberRowLoadClass = 1;

            String division = getCellValueAsString(currentRow.getCell(1)).trim();

            if (division.equals("количество групп")) {
                for (int currentColumn = numberColumnLoad; currentColumn <= currentRow.getLastCellNum(); currentColumn++) {
                    Cell currentCell = currentRow.getCell(currentColumn);
                    if (currentCell == null) continue;

                    int numberOfGroups = getCellValueAsInt(currentCell);
                    Row nextRow = sheet.getRow(numberCurrentRow + 1);
                    if (nextRow == null) continue;

                    int hoursSet = getCellValueAsInt(nextRow.getCell(currentColumn));
                    if (numberOfGroups == 2 && hoursSet > 0) {
                        Row subjectRow = sheet.getRow(numberCurrentRow - 1);
                        if (subjectRow == null) continue;

                        String subjectName = getCellValueAsString(subjectRow.getCell(0)).trim();
                        Row classRow = sheet.getRow(numberRowLoadClass);
                        if (classRow == null) continue;

                        String className = getCellValueAsString(classRow.getCell(currentColumn)).trim();
                        subjectWithGroupList.add(new SubjectWithGroup(
                                subjectName, className, numberSchoolBuilding
                        ));
                    }
                }
            }
        }
        return subjectWithGroupList;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    return String.valueOf((int) cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // Для формул пытаемся получить вычисленное значение
                    try {
                        // Сначала пробуем получить числовое значение
                        return String.valueOf((int) cell.getNumericCellValue());
                    } catch (Exception e) {
                        // Если не числовое, пробуем строковое
                        try {
                            return cell.getStringCellValue().trim();
                        } catch (Exception ex) {
                            return "";
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            System.err.println("Ошибка при чтении ячейки: " + e.getMessage());
            return "";
        }
    }

    private int getCellValueAsInt(Cell cell) {
        if (cell == null) return 0;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return (int) cell.getNumericCellValue();
                case STRING:
                    try {
                        return Integer.parseInt(cell.getStringCellValue().trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                case FORMULA:
                    // Для формул пытаемся получить вычисленное значение
                    try {
                        return (int) cell.getNumericCellValue();
                    } catch (Exception e) {
                        try {
                            return Integer.parseInt(cell.getStringCellValue().trim());
                        } catch (Exception ex) {
                            return 0;
                        }
                    }
                default:
                    return 0;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при чтении числовой ячейки: " + e.getMessage());
            return 0;
        }
    }
}