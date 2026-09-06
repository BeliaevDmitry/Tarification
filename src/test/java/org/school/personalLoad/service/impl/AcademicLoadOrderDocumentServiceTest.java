package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.AcademicLoadOrderType;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.StudyPeriod;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicLoadOrderDocumentServiceTest {

    private final AcademicLoadOrderDocumentService service = new AcademicLoadOrderDocumentService();

    @Test
    void generatesSchool7CurriculumApprovalOrder() throws Exception {
        var data = new AcademicLoadOrderDocumentService.DocumentData(
                AcademicLoadOrderType.CURRICULUM_APPROVAL,
                "7",
                "ГБОУ Школа № 7",
                "2026/2027",
                "125/1-ОД",
                LocalDate.of(2026, 8, 28),
                "1",
                LocalDate.of(2026, 8, 27),
                null,
                "Иванова Ирина Ивановна",
                "Директор",
                "Петрова Анна Сергеевна",
                "",
                sampleCurriculumRows(),
                List.of()
        );

        byte[] document = service.generate(data);
        assertDocument(document, "Об утверждении учебных планов", "Учебный план 1 параллели",
                "Учебный план 11 параллели", "СП-2");
        assertElevenCompactCurriculumPages(document);
        writeQaDocument("load-order-school-7-curriculum.docx", document);
    }

    @Test
    void generatesSchool1811LoadApprovalOrder() throws Exception {
        var data = new AcademicLoadOrderDocumentService.DocumentData(
                AcademicLoadOrderType.LOAD_APPROVAL,
                "1811",
                "ГБОУ Школа № 1811",
                "2026/2027",
                "86-ОД",
                LocalDate.of(2026, 8, 29),
                "",
                null,
                LocalDate.of(2026, 9, 1),
                "Смирнова Светлана Сергеевна",
                "Директор",
                "",
                "",
                List.of(),
                sampleLoadRows()
        );

        byte[] document = service.generate(data);
        assertDocument(document, "Об утверждении учебной нагрузки", "Учебная нагрузка педагогических работников", "Борисов Алексей Петрович");
        writeQaDocument("load-order-school-1811-load.docx", document);
    }

    @Test
    void rendersDifferentSemesterHoursAsSeparateRows() throws Exception {
        var data = new AcademicLoadOrderDocumentService.DocumentData(
                AcademicLoadOrderType.LOAD_APPROVAL,
                "7",
                "ГБОУ Школа № 7",
                "2026/2027",
                "1-ОД",
                LocalDate.of(2026, 8, 31),
                "",
                null,
                LocalDate.of(2026, 9, 1),
                "Иванова Ирина Ивановна",
                "Директор",
                "",
                "",
                List.of(),
                List.of(
                        new AcademicLoadOrderDocumentService.LoadRow(
                                "Конышева Галина Ибрагимовна",
                                "Занимательная математика юного москвича",
                                "4-К", "1", "01.09.2026 — 31.12.2026"),
                        new AcademicLoadOrderDocumentService.LoadRow(
                                "Конышева Галина Ибрагимовна",
                                "Занимательная математика юного москвича",
                                "4-К", "2", "11.01.2027 — 31.05.2027")
                )
        );

        byte[] document = service.generate(data);

        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(document))) {
            XWPFTable loadTable = word.getTables().stream()
                    .filter(table -> table.getNumberOfRows() > 0 && table.getRow(0).getTableCells().size() == 6)
                    .findFirst()
                    .orElseThrow();
            assertThat(loadTable.getNumberOfRows()).isEqualTo(3);
            assertThat(loadTable.getRow(1).getCell(4).getText()).isEqualTo("1");
            assertThat(loadTable.getRow(1).getCell(5).getText()).isEqualTo("01.09.2026 — 31.12.2026");
            assertThat(loadTable.getRow(2).getCell(4).getText()).isEqualTo("2");
            assertThat(loadTable.getRow(2).getCell(5).getText()).isEqualTo("11.01.2027 — 31.05.2027");
        }
        writeQaDocument("load-order-semester-hours.docx", document);
    }

    private void assertDocument(byte[] bytes, String... expectedText) throws Exception {
        assertThat(bytes).hasSizeGreaterThan(10_000);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            String tables = document.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .map(cell -> cell.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            for (String expected : expectedText) {
                assertThat(text + tables).contains(expected);
            }
            assertThat(document.getAllPictures()).isNotEmpty();
        }
    }

    private void writeQaDocument(String filename, byte[] bytes) throws Exception {
        Path directory = Path.of("target", "load-order-qa");
        Files.createDirectories(directory);
        Files.write(directory.resolve(filename), bytes);
    }

    private void assertElevenCompactCurriculumPages(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<XWPFTable> curriculumTables = document.getTables().stream()
                    .filter(table -> table.getNumberOfRows() > 0)
                    .filter(table -> table.getRow(0).getCell(0).getText().equals("№"))
                    .toList();
            assertThat(curriculumTables).hasSize(11);
            for (XWPFTable table : curriculumTables) {
                assertThat(table.getRows().stream().skip(1)).allSatisfy(row -> {
                    assertThat(row.getCell(2).getText()).isNotBlank();
                    assertThat(row.getTableCells().stream().skip(3).map(cell -> cell.getText().trim()))
                            .anyMatch(value -> !value.isBlank());
                });
            }

            long continuationBreaks = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getCTP().isSetPPr())
                    .filter(paragraph -> paragraph.getCTP().getPPr().isSetPageBreakBefore())
                    .count();
            assertThat(continuationBreaks).isEqualTo(10);
            assertThat(document.getDocument().getBody().getSectPr().getPgSz().getOrient().toString())
                    .isEqualTo("landscape");
            assertThat(new BigDecimal(document.getDocument().getBody().getSectPr().getPgSz().getW().toString()).intValue())
                    .isEqualTo(16838);
            assertThat(curriculumTables.get(9).getRows().stream()
                    .map(row -> row.getCell(2).getText() + "|" + row.getCell(3).getText())
                    .toList()).contains("Профильная математика|2/3");
        }
    }

    private List<AcademicLoadOrderDocumentService.CurriculumPlanRow> sampleCurriculumRows() {
        List<AcademicLoadOrderDocumentService.CurriculumPlanRow> rows = new ArrayList<>();
        for (int parallel = 1; parallel <= 11; parallel++) {
            String building = parallel <= 9 ? "СП-1" : "СП-2";
            String className = parallel + "-А";
            rows.add(curriculumRow(parallel, building, className, CurriculumPart.CORE,
                    "Русский язык", StudyPeriod.YEAR, parallel <= 4 ? 5 : 3));
            rows.add(curriculumRow(parallel, building, className, CurriculumPart.FORMABLE,
                    "Функциональная грамотность", StudyPeriod.YEAR, 1));
        }
        for (char letter = 'А'; letter <= 'З'; letter++) {
            for (int subjectIndex = 1; subjectIndex <= 45; subjectIndex++) {
                rows.add(curriculumRow(1, "СП-1", "1-" + letter,
                        subjectIndex % 3 == 0 ? CurriculumPart.FORMABLE : CurriculumPart.CORE,
                        "Дополнительный учебный предмет с длинным названием " + subjectIndex,
                        StudyPeriod.YEAR, 1));
            }
        }
        rows.add(curriculumRow(10, "СП-2", "10-А", CurriculumPart.CORE,
                "Профильная математика", StudyPeriod.H1, 2));
        rows.add(curriculumRow(10, "СП-2", "10-А", CurriculumPart.CORE,
                "Профильная математика", StudyPeriod.H2, 3));
        rows.add(curriculumRow(10, "СП-2", "10-А", CurriculumPart.CORE,
                "Профильная математика", StudyPeriod.H2, 3));
        return rows;
    }

    private AcademicLoadOrderDocumentService.CurriculumPlanRow curriculumRow(
            int parallel,
            String building,
            String className,
            CurriculumPart part,
            String subject,
            StudyPeriod period,
            int hours) {
        return new AcademicLoadOrderDocumentService.CurriculumPlanRow(
                parallel, building, className, part, subject, period, BigDecimal.valueOf(hours));
    }

    private List<AcademicLoadOrderDocumentService.LoadRow> sampleLoadRows() {
        List<AcademicLoadOrderDocumentService.LoadRow> rows = new ArrayList<>();
        rows.add(new AcademicLoadOrderDocumentService.LoadRow("Александрова Мария Ивановна", "Русский язык", "5А, 5Б", "18", "Учебный год"));
        rows.add(new AcademicLoadOrderDocumentService.LoadRow("Борисов Алексей Петрович", "Математика", "7А, 7Б", "20", "Учебный год"));
        rows.add(new AcademicLoadOrderDocumentService.LoadRow("Васильева Ольга Сергеевна", "История", "9А", "8", "01.09.2026 — 31.05.2027"));
        for (int index = 1; index <= 60; index++) {
            rows.add(new AcademicLoadOrderDocumentService.LoadRow(
                    "Педагог " + String.format("%02d", index) + " Тестовый",
                    index % 2 == 0 ? "Иностранный язык" : "Математика",
                    (5 + index % 6) + "А, " + (5 + index % 6) + "Б",
                    String.valueOf(6 + index % 19),
                    index % 3 == 0 ? "1 полугодие" : "Учебный год"));
        }
        return rows;
    }
}
