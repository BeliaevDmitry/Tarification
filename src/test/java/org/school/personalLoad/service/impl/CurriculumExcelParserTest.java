package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.StudyPeriod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumExcelParserTest {

    private final CurriculumExcelParser parser = new CurriculumExcelParser();

    @Test
    void parseNooAndMergedAndHalfHourAndSubjectAorB() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet noo = wb.createSheet("НОО");

        row(noo, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(noo, 1).createCell(0).setCellValue("Период обучения");
        row(noo, 1).createCell(2).setCellValue("");
        row(noo, 2).createCell(0).setCellValue("Направленность класса");
        row(noo, 2).createCell(2).setCellValue("универсальный");
        row(noo, 3).createCell(0).setCellValue("ФИО классного руководителя");
        row(noo, 3).createCell(2).setCellValue("Иванова И.И.");
        row(noo, 4).createCell(0).setCellValue("Класс");
        row(noo, 4).createCell(2).setCellValue("5А");
        row(noo, 5).createCell(0).setCellValue("Обязательная часть");

        row(noo, 6).createCell(0).setCellValue("Математика");
        noo.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(6, 6, 0, 1));
        row(noo, 6).createCell(2).setCellValue(5);

        row(noo, 7).createCell(1).setCellValue("Физика");
        row(noo, 7).createCell(2).setCellValue("0,5");

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertEquals(2, rows.size());

        CurriculumImportRow math = rows.stream().filter(r -> r.getSubjectName().equals("Математика")).findFirst().orElseThrow();
        assertEquals(CurriculumStage.NOO, math.getStage());
        assertEquals("5-А", math.getClassName());
        assertEquals(StudyPeriod.YEAR, math.getStudyPeriod());
        assertEquals(new BigDecimal("5"), math.getPlannedHours());

        CurriculumImportRow physics = rows.stream().filter(r -> r.getSubjectName().equals("Физика")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("0.5"), physics.getPlannedHours());
    }

    @Test
    void parseOooSingleColumn() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet ooo = wb.createSheet("ООО");

        row(ooo, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(ooo, 1).createCell(0).setCellValue("Период обучения");
        row(ooo, 1).createCell(2).setCellValue("");
        row(ooo, 2).createCell(0).setCellValue("Направленность класса");
        row(ooo, 2).createCell(2).setCellValue("технологический");
        row(ooo, 3).createCell(0).setCellValue("Класс");
        row(ooo, 3).createCell(2).setCellValue("9Б");
        row(ooo, 4).createCell(0).setCellValue("Обязательная часть");
        row(ooo, 5).createCell(1).setCellValue("История");
        row(ooo, 5).createCell(2).setCellValue(2);

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertEquals(1, rows.size());
        CurriculumImportRow row = rows.get(0);
        assertEquals(CurriculumStage.OOO, row.getStage());
        assertEquals("9-Б", row.getClassName());
        assertEquals(StudyPeriod.YEAR, row.getStudyPeriod());
    }

    @Test
    void parseSooPairColumnsAndPeriods() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet soo = wb.createSheet("СОО");

        row(soo, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(soo, 1).createCell(0).setCellValue("Период обучения");
        row(soo, 1).createCell(2).setCellValue("1П");
        row(soo, 1).createCell(3).setCellValue("2П");
        row(soo, 2).createCell(0).setCellValue("Направленность класса");
        row(soo, 2).createCell(2).setCellValue("гуманитарный");
        row(soo, 3).createCell(0).setCellValue("ФИО классного руководителя");
        row(soo, 3).createCell(2).setCellValue("Петров П.П.");
        row(soo, 4).createCell(0).setCellValue("Класс");
        row(soo, 4).createCell(2).setCellValue("10А");
        row(soo, 5).createCell(0).setCellValue("Обязательная часть");
        row(soo, 6).createCell(1).setCellValue("Алгебра");
        row(soo, 6).createCell(2).setCellValue(3);
        row(soo, 6).createCell(3).setCellValue(2);

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertEquals(2, rows.size());
        CurriculumImportRow h1 = rows.stream().filter(r -> r.getStudyPeriod() == StudyPeriod.H1).findFirst().orElseThrow();
        CurriculumImportRow h2 = rows.stream().filter(r -> r.getStudyPeriod() == StudyPeriod.H2).findFirst().orElseThrow();
        assertEquals("10-А", h1.getClassName());
        assertEquals("10-А", h2.getClassName());
    }

    @Test
    void skipsServiceRows() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("НОО");
        row(sheet, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(sheet, 1).createCell(0).setCellValue("Период обучения");
        row(sheet, 2).createCell(0).setCellValue("Направленность класса");
        row(sheet, 3).createCell(0).setCellValue("Класс");
        row(sheet, 1).createCell(2).setCellValue("");
        row(sheet, 2).createCell(2).setCellValue("обычный");
        row(sheet, 3).createCell(2).setCellValue("1А");
        row(sheet, 4).createCell(0).setCellValue("Обязательная часть");
        row(sheet, 5).createCell(1).setCellValue("Итого");
        row(sheet, 5).createCell(2).setCellValue(1);

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertTrue(rows.isEmpty());
    }

    @Test
    void skipsКоличествоУчебныхНедельRow() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("НОО");
        row(sheet, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(sheet, 1).createCell(0).setCellValue("Период обучения");
        row(sheet, 2).createCell(0).setCellValue("Направленность класса");
        row(sheet, 3).createCell(0).setCellValue("Класс");
        row(sheet, 1).createCell(2).setCellValue("");
        row(sheet, 2).createCell(2).setCellValue("обычный");
        row(sheet, 3).createCell(2).setCellValue("1А");
        row(sheet, 4).createCell(0).setCellValue("Обязательная часть");

        row(sheet, 5).createCell(1).setCellValue("Количество учебных недель");
        row(sheet, 5).createCell(2).setCellValue(34);

        row(sheet, 6).createCell(1).setCellValue("Русский язык");
        row(sheet, 6).createCell(2).setCellValue(5);

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertEquals(1, rows.size());
        assertEquals("Русский язык", rows.get(0).getSubjectName());
    }

    @Test
    void skipsКоличествоЧасовЗаГодПоУчебномуПлануRow() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("НОО");
        row(sheet, 0).createCell(0).setCellValue("Учебный год 2025-2026");
        row(sheet, 1).createCell(0).setCellValue("Период обучения");
        row(sheet, 2).createCell(0).setCellValue("Направленность класса");
        row(sheet, 3).createCell(0).setCellValue("Класс");
        row(sheet, 1).createCell(2).setCellValue("");
        row(sheet, 2).createCell(2).setCellValue("обычный");
        row(sheet, 3).createCell(2).setCellValue("1А");
        row(sheet, 4).createCell(0).setCellValue("Обязательная часть");

        row(sheet, 5).createCell(1).setCellValue("Количество часов за год по учебному плану");
        row(sheet, 5).createCell(2).setCellValue(165);

        row(sheet, 6).createCell(1).setCellValue("Русский язык");
        row(sheet, 6).createCell(2).setCellValue(5);

        List<CurriculumImportRow> rows = parseWorkbook(wb);
        assertEquals(1, rows.size());
        assertEquals("Русский язык", rows.get(0).getSubjectName());
    }

    private Row row(Sheet sheet, int index) {
        Row row = sheet.getRow(index);
        return row == null ? sheet.createRow(index) : row;
    }

    private List<CurriculumImportRow> parseWorkbook(Workbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return parser.parse(new ByteArrayInputStream(out.toByteArray()));
        }
    }
}
