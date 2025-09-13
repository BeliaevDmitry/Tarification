package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.DataReaderService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataReaderServiceImpl implements DataReaderService { //

    private FormulaEvaluator formulaEvaluator;

    public DataReaderServiceImpl() {
        // Простой конструктор
    }

    public void setFormulaEvaluator(FormulaEvaluator formulaEvaluator) {
        this.formulaEvaluator = formulaEvaluator;
    }

    public void readExcelData(String inputPath,
                               List<TarifficationPerson> tarifficationList,
                               List<SubjectWithGroup> groupList) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(inputPath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            setFormulaEvaluator(evaluator);

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName().toLowerCase();

                if (sheetName.contains("корп")) {
                    System.out.println("📊 Анализируем лист: " + sheet.getSheetName());
                    tarifficationList.addAll(analyzeSheet(sheet));
                    groupList.addAll(searchGroup(sheet));
                } else {
                    System.out.println("⏭️ Пропускаем лист: " + sheet.getSheetName());
                }
            }
        }
    }


    public List<TarifficationPerson> analyzeSheet(Sheet sheet) throws IOException {
        List<TarifficationPerson> tarifficationList = new ArrayList<>();
        String numberSchoolBuilding = sheet.getSheetName();

        // Предварительно читаем строку с классами (строка 1)
        Row classRow = sheet.getRow(1);
        if (classRow == null) return tarifficationList;

        // Кэшируем названия классов для колонок
        String[] classNames = new String[classRow.getLastCellNum() + 1];
        for (int i = 12; i <= classRow.getLastCellNum(); i++) {
            Cell cell = classRow.getCell(i);
            classNames[i] = (cell != null) ? getCellValueAsStringFast(cell) : "";
        }

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row teacherRow = sheet.getRow(numberCurrentRow);
            if (teacherRow == null) continue;

            // Быстрое чтение основных ячеек
            String subjectName = getCellValueAsStringFast(teacherRow.getCell(0));
            String fioTeacher = getCellValueAsStringFast(teacherRow.getCell(1));
            int teacherLoadInRow = getCellValueAsIntFast(teacherRow.getCell(2));

            // Быстрая проверка на пропуск строки
            if (shouldSkipRowFast(fioTeacher, subjectName)) {
                continue;
            }

            // Обрабатываем нагрузку по колонкам
            processTeacherColumnsFast(teacherRow, subjectName, fioTeacher,
                    numberSchoolBuilding, classNames, tarifficationList);
        }
        return tarifficationList;
    }

    private boolean shouldSkipRowFast(String fioTeacher, String subjectName) {
        if (fioTeacher == null || subjectName == null) return true;

        // Быстрая проверка через switch
        switch (fioTeacher) {
            case "по УП":
            case "выставлено выше":
            case "выставлено":
            case "количество групп":
            case "системный":
                return true;
            default:
                return subjectName.endsWith("КРО");
        }
    }

    private void processTeacherColumnsFast(Row teacherRow, String subjectName, String fioTeacher,
                                           String numberSchoolBuilding, String[] classNames,
                                           List<TarifficationPerson> tarifficationList) {

        int lastCellNum = Math.min(teacherRow.getLastCellNum(), classNames.length - 1);

        for (int currentColumn = 12; currentColumn <= lastCellNum; currentColumn++) {
            Cell currentCell = teacherRow.getCell(currentColumn);
            if (currentCell == null) continue;

            int currentLoad = getCellValueAsIntFast(currentCell);
            if (currentLoad <= 0) continue;

            String className = classNames[currentColumn];
            if (className == null || shouldSkipClassFast(className)) continue;

            tarifficationList.add(new TarifficationPerson(
                    fioTeacher, numberSchoolBuilding, subjectName, className, currentLoad
            ));
        }
    }

    private boolean shouldSkipClassFast(String className) {
        return "1-4кл".equals(className) || "5-9кл".equals(className) || "сш".equals(className);
    }

    public List<SubjectWithGroup> searchGroup(Sheet sheet) {
        List<SubjectWithGroup> subjectWithGroupList = new ArrayList<>();
        String numberSchoolBuilding = sheet.getSheetName();

        // Предварительно читаем строку с классами
        Row classRow = sheet.getRow(1);
        if (classRow == null) return subjectWithGroupList;

        // Кэшируем названия классов
        String[] classNames = new String[classRow.getLastCellNum() + 1];
        for (int i = 12; i <= classRow.getLastCellNum(); i++) {
            Cell cell = classRow.getCell(i);
            classNames[i] = (cell != null) ? getCellValueAsStringFast(cell) : "";
        }

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row currentRow = sheet.getRow(numberCurrentRow);
            if (currentRow == null) continue;

            String division = getCellValueAsStringFast(currentRow.getCell(1));
            if ("количество групп".equals(division)) {
                processGroupRowFast(sheet, numberCurrentRow, numberSchoolBuilding,
                        classNames, subjectWithGroupList);
            }
        }
        return subjectWithGroupList;
    }

    private void processGroupRowFast(Sheet sheet, int currentRowIndex, String numberSchoolBuilding,
                                     String[] classNames, List<SubjectWithGroup> subjectWithGroupList) {

        Row currentRow = sheet.getRow(currentRowIndex);
        Row subjectRow = sheet.getRow(currentRowIndex - 1);
        Row nextRow = sheet.getRow(currentRowIndex + 1);

        if (subjectRow == null || nextRow == null) return;

        String subjectName = getCellValueAsStringFast(subjectRow.getCell(0));
        if (subjectName == null || subjectName.isEmpty()) return;

        int lastCellNum = Math.min(currentRow.getLastCellNum(), classNames.length - 1);

        for (int currentColumn = 12; currentColumn <= lastCellNum; currentColumn++) {
            int numberOfGroups = getCellValueAsIntFast(currentRow.getCell(currentColumn));
            int hoursSet = getCellValueAsIntFast(nextRow.getCell(currentColumn));

            if (numberOfGroups == 2 && hoursSet > 0) {
                String className = classNames[currentColumn];
                if (className != null && !shouldSkipClassFast(className)) {
                    subjectWithGroupList.add(new SubjectWithGroup(
                            subjectName, className, numberSchoolBuilding
                    ));
                }
            }
        }
    }

    // ОЧЕНЬ БЫСТРОЕ чтение строковых значений
    private String getCellValueAsStringFast(Cell cell) {
        if (cell == null) return "";

        try {
            CellType cellType = cell.getCellType();

            switch (cellType) {
                case STRING:
                    return cell.getStringCellValue().trim();

                case NUMERIC:
                    // Для числовых значений возвращаем целое число как строку
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (int) numValue) {
                        return String.valueOf((int) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }

                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());

                case FORMULA:
                    // Для формул пытаемся быстро получить значение
                    try {
                        // Сначала пробуем числовое значение
                        double formulaNumValue = cell.getNumericCellValue();
                        if (formulaNumValue == (int) formulaNumValue) {
                            return String.valueOf((int) formulaNumValue);
                        } else {
                            return String.valueOf(formulaNumValue);
                        }
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
            return "";
        }
    }

    // ОЧЕНЬ БЫСТРОЕ чтение целочисленных значений
    private int getCellValueAsIntFast(Cell cell) {
        if (cell == null) return 0;

        try {
            CellType cellType = cell.getCellType();

            switch (cellType) {
                case NUMERIC:
                    return (int) cell.getNumericCellValue();

                case STRING:
                    String stringValue = cell.getStringCellValue().trim();
                    if (stringValue.isEmpty()) return 0;
                    try {
                        return Integer.parseInt(stringValue);
                    } catch (NumberFormatException e) {
                        return 0;
                    }

                case FORMULA:
                    // Для формул пытаемся быстро получить числовое значение
                    try {
                        return (int) cell.getNumericCellValue();
                    } catch (Exception e) {
                        try {
                            String formulaString = cell.getStringCellValue().trim();
                            return formulaString.isEmpty() ? 0 : Integer.parseInt(formulaString);
                        } catch (Exception ex) {
                            return 0;
                        }
                    }

                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}