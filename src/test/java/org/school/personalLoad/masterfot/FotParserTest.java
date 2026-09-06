package org.school.personalLoad.masterfot;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
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
    @Test @EnabledIfSystemProperty(named="master.fot.sample",matches=".+")
    void readsSuppliedExportWithoutStoringPersonalDataInRepository() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(System.getProperty("master.fot.sample")))) {
            var file = new MockMultipartFile("file","sample.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",in);
            var source = new FotParser().parse(file,"2026/2027");
            assertThat(source.rows()).hasSize(1629);
            assertThat(source.rows().stream().map(FotDtos.SourceRow::total).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("3241");
            assertThat(source.rows().stream().map(FotDtos.SourceRow::unassigned).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("83");
        }
    }
}
