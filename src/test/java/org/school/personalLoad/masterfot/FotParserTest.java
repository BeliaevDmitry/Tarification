package org.school.personalLoad.masterfot;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FotParserTest {
    static MockMultipartFile file(boolean corrupt) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Тарификация");
            s.createRow(1).createCell(0).setCellValue("Тестовая школа · 2026/2027 · Состояние на 05.09.2026");
            Row head = s.createRow(3), hours = s.createRow(4);
            String[] names = {"Учебная группа","Должность","Часть УП","Предмет"};
            for (int i=0;i<names.length;i++) head.createCell(i).setCellValue(names[i]);
            String[] h = {"Всего","Назначено","Не назначено"};
            for (int i=0;i<h.length;i++) hours.createCell(4+i).setCellValue(h[i]);
            s.createRow(5).createCell(0).setCellValue("Иванов Иван Иванович");
            Row r = s.createRow(6); r.createCell(0).setCellValue("7-А"); r.createCell(1).setCellValue("Учитель");
            r.createCell(2).setCellValue("Обязательная часть"); r.createCell(3).setCellValue("Алгебра");
            r.createCell(4).setCellValue(3); r.createCell(5).setCellValue(3); r.createCell(6).setCellValue(0);
            s.createRow(7).createCell(0).setCellValue("ИТОГ ПО ГРУППЕ");
            Row end = s.createRow(8); end.createCell(0).setCellValue("ОБЩИЙ ИТОГ");
            end.createCell(4).setCellValue(corrupt ? 4 : 3); end.createCell(5).setCellValue(3); end.createCell(6).setCellValue(0);
            wb.write(out); return new MockMultipartFile("file","Тарификация.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
    static MockMultipartFile flatFile() throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Тарификация");
            s.createRow(1).createCell(0).setCellValue("Тестовая школа · 2026/2027 · Состояние на 05.09.2026");
            Row head = s.createRow(3), hours = s.createRow(4), total = s.createRow(5);
            String[] names = {"Педагог","Учебная группа","Должность","Часть УП","Предмет"};
            for (int i=0;i<names.length;i++) head.createCell(i).setCellValue(names[i]);
            head.createCell(5).setCellValue("Часы");
            String[] h = {"Всего","Назначено","Не назначено"};
            for (int i=0;i<h.length;i++) hours.createCell(5+i).setCellValue(h[i]);
            total.createCell(0).setCellValue("ВСЕГО ПО ШКОЛЕ");
            total.createCell(5).setCellFormula("SUM(F7:F7)");
            total.createCell(6).setCellFormula("SUM(G7:G7)");
            total.createCell(7).setCellFormula("SUM(H7:H7)");
            Row r = s.createRow(6); r.createCell(0).setCellValue("Иванов Иван Иванович");
            r.createCell(1).setCellValue("7-А"); r.createCell(2).setCellValue("Учитель");
            r.createCell(3).setCellValue("Обязательная часть"); r.createCell(4).setCellValue("Алгебра");
            r.createCell(5).setCellValue(3); r.createCell(6).setCellValue(3); r.createCell(7).setCellValue(0);
            wb.write(out); return new MockMultipartFile("file","Тарификация.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
    static MockMultipartFile groupedTopSummaryFile() throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Тарификация");
            s.createRow(1).createCell(0).setCellValue("Тестовая школа · 2026/2027 · Состояние на 05.09.2026");
            Row head = s.createRow(3), hours = s.createRow(4), summary = s.createRow(5);
            String[] names = {"Учебная группа", "Должность", "Часть УП", "Предмет"};
            for (int i = 0; i < names.length; i++) head.createCell(i).setCellValue(names[i]);
            for (int i = 0; i < 3; i++) hours.createCell(4 + i).setCellValue(List.of("Всего", "Назначено", "Не назначено").get(i));
            summary.createCell(0).setCellValue("ВСЕГО ПО ШКОЛЕ");
            summary.createCell(4).setCellFormula("SUM(E7,E10)");
            summary.createCell(5).setCellFormula("SUM(F7,F10)");
            summary.createCell(6).setCellFormula("SUM(G7,G10)");

            Row firstTotal = s.createRow(6); firstTotal.createCell(0).setCellValue("ИТОГО ПО ГРУППЕ");
            firstTotal.createCell(4).setCellFormula("SUM(E9:E9)"); firstTotal.createCell(5).setCellFormula("SUM(F9:F9)"); firstTotal.createCell(6).setCellFormula("SUM(G9:G9)");
            s.createRow(7).createCell(0).setCellValue("Иванов Иван Иванович");
            Row first = s.createRow(8); first.createCell(0).setCellValue("7-А"); first.createCell(1).setCellValue("Учитель");
            first.createCell(2).setCellValue("Обязательная часть"); first.createCell(3).setCellValue("Алгебра");
            first.createCell(4).setCellValue(3); first.createCell(5).setCellValue(3); first.createCell(6).setCellValue(0);

            Row secondTotal = s.createRow(9); secondTotal.createCell(0).setCellValue("ИТОГО ПО ГРУППЕ");
            secondTotal.createCell(4).setCellFormula("SUM(E12:E12)"); secondTotal.createCell(5).setCellFormula("SUM(F12:F12)"); secondTotal.createCell(6).setCellFormula("SUM(G12:G12)");
            s.createRow(10).createCell(0).setCellValue("Петров Пётр Петрович");
            Row second = s.createRow(11); second.createCell(0).setCellValue("Математика 8-Б 1гр"); second.createCell(1).setCellValue("Учитель");
            second.createCell(2).setCellValue("Обязательная часть"); second.createCell(3).setCellValue("Математика");
            second.createCell(4).setCellValue(4); second.createCell(5).setCellValue(4); second.createCell(6).setCellValue(0);
            wb.write(out);
            return new MockMultipartFile("file", "Тарификация.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
    @Test void readsHeadingWithoutCountingTotalsAsLoad() throws Exception {
        var source = new FotParser().parse(file(false),"2026/2027");
        assertThat(source.rows()).hasSize(1); assertThat(source.rows().get(0).teacher()).isEqualTo("Иванов Иван Иванович");
        assertThat(source.rows().get(0).total()).isEqualByComparingTo("3");
    }
    @Test void rejectsWrongYearAndIncompleteTotals() throws Exception {
        var good = file(false); var bad = file(true);
        assertThatThrownBy(() -> new FotParser().parse(good,"2025/2026")).hasMessageContaining("выбран 2025/2026");
        assertThatThrownBy(() -> new FotParser().parse(bad,"2026/2027")).hasMessageContaining("общим итогом");
    }
    @Test void readsCurrentFlatMasterFotExport() throws Exception {
        var source = new FotParser().parse(flatFile(), "2026/2027");
        assertThat(source.rows()).hasSize(1);
        assertThat(source.rows().get(0).teacher()).isEqualTo("Иванов Иван Иванович");
        assertThat(source.rows().get(0).total()).isEqualByComparingTo("3");
    }
    @Test void groupedTopSummaryUsesTeacherAfterEachGroupTotalAndColumnsAAndD() throws Exception {
        var source = new FotParser().parse(groupedTopSummaryFile(), "2026/2027");

        assertThat(source.rows()).hasSize(2);
        assertThat(source.rows().get(0).teacher()).isEqualTo("Иванов Иван Иванович");
        assertThat(source.rows().get(0).group()).isEqualTo("7-А");
        assertThat(source.rows().get(0).subject()).isEqualTo("Алгебра");
        assertThat(source.rows().get(1).teacher()).isEqualTo("Петров Пётр Петрович");
        assertThat(source.rows().get(1).group()).isEqualTo("Математика 8-Б 1гр");
        assertThat(source.rows().get(1).subject()).isEqualTo("Математика");
    }
    @Test @EnabledIfSystemProperty(named="master.fot.sample",matches=".+")
    void readsSuppliedExportWithoutStoringPersonalDataInRepository() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(System.getProperty("master.fot.sample")))) {
            var file = new MockMultipartFile("file","sample.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",in);
            var source = new FotParser().parse(file,"2026/2027");
            assertThat(source.rows()).hasSize(Integer.getInteger("master.fot.sample.rows", 1629));
            assertThat(source.rows().stream().map(FotDtos.SourceRow::total).reduce(BigDecimal.ZERO,BigDecimal::add))
                    .isEqualByComparingTo(System.getProperty("master.fot.sample.total", "3241"));
            assertThat(source.rows().stream().map(FotDtos.SourceRow::unassigned).reduce(BigDecimal.ZERO,BigDecimal::add))
                    .isEqualByComparingTo(System.getProperty("master.fot.sample.unassigned", "83"));
        }
    }
}
