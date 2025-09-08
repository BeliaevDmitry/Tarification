package org.school.personalLoad.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public void createReport(List<TarifficationPerson> tarifficationList,
                             List<SubjectWithGroup> subjectWithGroupList,
                             List<TarifficationChanges> changes,
                             String outputPath,
                             List<String> listGroup,
                             Map<String, List<String>> disabledStudentsGroups) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            createTarifficationSheet(workbook, tarifficationList);
            createGroupsSheet(workbook, subjectWithGroupList);
            createChangesSheet(workbook, changes);
            createUniqueNamesSheet(workbook, listGroup);
            createDisabledStudentsSheet(workbook, disabledStudentsGroups);
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createChangesSheet(Workbook workbook, List<TarifficationChanges> changes) {
        if (changes == null || changes.isEmpty()) return;

        Sheet sheet = workbook.createSheet("Изменения тарификации");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "Группа",
                "Количество часов", "Часов в группе", "Тип изменения", "Дата изменения"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_ORANGE);

        int rowNum = 1;
        for (TarifficationChanges change : changes) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(change.getFioTeacher());
            row.createCell(1).setCellValue(change.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(change.getSubjectName());
            row.createCell(3).setCellValue(change.getClassName());
            row.createCell(4).setCellValue(change.getGroupName() != null ? change.getGroupName() : "");
            row.createCell(5).setCellValue(change.getLoad());
            row.createCell(6).setCellValue(change.getGroupLoad() != null ? change.getGroupLoad() : 0);
            row.createCell(7).setCellValue(change.getChangeTypeRussian());
            row.createCell(8).setCellValue(change.getChangeDate().format(dateFormatter));
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createTarifficationSheet(Workbook workbook, List<TarifficationPerson> tarifficationList) {
        Sheet sheet = workbook.createSheet("Тарификация");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "группа", "Количество часов", "Количество часов в группе"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.GREY_25_PERCENT);

        int rowNum = 1;
        for (TarifficationPerson record : tarifficationList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getFioTeacher());
            row.createCell(1).setCellValue(record.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(record.getSubjectName());
            row.createCell(3).setCellValue(record.getClassName());
            row.createCell(4).setCellValue(record.getGroupName());
            row.createCell(5).setCellValue(record.getLoad());
            row.createCell(6).setCellValue(record.getGroupLoad());
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createGroupsSheet(Workbook workbook, List<SubjectWithGroup> subjectWithGroupList) {
        Sheet sheet = workbook.createSheet("Группы");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"корпус", "Предмет", "Класс", "Количество групп"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_BLUE);

        int rowNum = 1;
        for (SubjectWithGroup group : subjectWithGroupList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(group.getNumberSchoolBuilding());
            row.createCell(1).setCellValue(group.getSubjectName());
            row.createCell(2).setCellValue(group.getClassName());
            row.createCell(3).setCellValue("Деление на группы");
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createHeaderRow(Row headerRow, String[] headers, Workbook workbook, IndexedColors color) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(color.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createUniqueNamesSheet(Workbook workbook, List<String> listGroup) {
        if (listGroup == null || listGroup.isEmpty()) return;

        Sheet sheet = workbook.createSheet("Уникальные названия групп");
        sheet.createFreezePane(0, 1, 0, 1);
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Уникальные названия групп/классов по УП"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_GREEN);

        int rowNum = 1;
        for (String name : listGroup) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(name);
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createDisabledStudentsSheet(Workbook workbook, Map<String, List<String>> disabledStudentsGroups) {
        if (disabledStudentsGroups == null || disabledStudentsGroups.isEmpty()) {
            return;
        }

        Sheet sheet = workbook.createSheet("Инвалиды и группы");
        sheet.createFreezePane(0, 1, 0, 1);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО Ребенка", "Группы"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_YELLOW);

        // Сортируем студентов по ФИО для удобства чтения
        List<String> sortedStudents = new ArrayList<>(disabledStudentsGroups.keySet());
        sortedStudents.sort(String::compareToIgnoreCase);

        int rowNum = 1;
        for (String student : sortedStudents) {
            List<String> groups = disabledStudentsGroups.get(student);
            if (groups != null && !groups.isEmpty()) {
                Row row = sheet.createRow(rowNum++);

                // Колонка 1: ФИО ребенка
                row.createCell(0).setCellValue(student);

                // Колонка 2: Все группы через запятую в одну ячейку
                String groupsString = String.join(", ", groups);
                row.createCell(1).setCellValue(groupsString);
            }
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}