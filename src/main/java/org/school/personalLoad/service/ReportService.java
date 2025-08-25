package org.school.personalLoad.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ReportService {

    public void createReport(List<TarifficationPerson> tarifficationList,
                             List<SubjectWithGroup> subjectWithGroupList,
                             String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            createTarifficationSheet(workbook, tarifficationList);
            createGroupsSheet(workbook, subjectWithGroupList);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createTarifficationSheet(Workbook workbook, List<TarifficationPerson> tarifficationList) {
        Sheet sheet = workbook.createSheet("Тарификация");
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

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}