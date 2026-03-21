package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.StudyPeriod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumExcelImportParserTest {

    private final CurriculumExcelImportParser parser = new CurriculumExcelImportParser();

    @Test
    void parsesMergedSubjectFromColumnA() {
        try (XSSFWorkbook workbook = baseWorkbook("НОО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, false);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(6, 6, 0, 1));
            row(sheet, 6, "Русский язык", "", "5");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals(1, rows.size());
            assertEquals("Русский язык", rows.get(0).subjectName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void parsesSubjectFromColumnBWhenAIsEmpty() {
        try (XSSFWorkbook workbook = baseWorkbook("ООО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, false);
            row(sheet, 6, "", "Алгебра", "4");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals("Алгебра", rows.get(0).subjectName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void parsesFractionalHoursWithComma() {
        try (XSSFWorkbook workbook = baseWorkbook("ООО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, false);
            row(sheet, 6, "", "Музыка", "0,5");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals(0.5d, rows.get(0).plannedHours());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void parsesOooSingleColumnClass() {
        try (XSSFWorkbook workbook = baseWorkbook("ООО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, false);
            row(sheet, 6, "", "История", "2");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals(1, rows.size());
            assertEquals("7-А", rows.get(0).className());
            assertEquals(CurriculumPart.CORE, rows.get(0).curriculumPart());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void parsesSooHalfYearsWithInheritedClassMetadata() {
        try (XSSFWorkbook workbook = baseWorkbook("СОО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, true);
            row(sheet, 6, "", "Математика", "3", "2");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals(2, rows.size());
            assertEquals(StudyPeriod.H1, rows.get(0).studyPeriod());
            assertEquals(StudyPeriod.H2, rows.get(1).studyPeriod());
            assertEquals("10-А", rows.get(1).className());
            assertEquals("Профиль", rows.get(1).classDirection());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void skipsServiceRows() {
        try (XSSFWorkbook workbook = baseWorkbook("НОО")) {
            Sheet sheet = workbook.getSheetAt(0);
            fillHeader(sheet, false);
            row(sheet, 6, "", "Итого", "10");
            row(sheet, 7, "", "Окружающий мир", "2");

            List<CurriculumExcelImportParser.ImportedCurriculumRow> rows = parser.parse(workbook);
            assertEquals(1, rows.size());
            assertEquals("Окружающий мир", rows.get(0).subjectName());
        } catch (Exception e) {
            fail(e);
        }
    }

    private XSSFWorkbook baseWorkbook(String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet(sheetName);
        return workbook;
    }

    private void fillHeader(Sheet sheet, boolean sooPair) {
        row(sheet, 0, "Учебный год 2026-2027");
        row(sheet, 1, "Период обучения", "", sooPair ? "1П" : "Год", sooPair ? "2П" : "");
        row(sheet, 2, "Направленность класса", "", "Профиль", sooPair ? "" : "");
        row(sheet, 3, "ФИО классного руководителя", "", "Иванова И.И.", sooPair ? "" : "");
        row(sheet, 4, "Класс", "", sooPair ? "10-А" : "7-А", sooPair ? "" : "");
        row(sheet, 5, "Обязательная часть");
    }

    private void row(Sheet sheet, int rowIndex, String... values) {
        var row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
