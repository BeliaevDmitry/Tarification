package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.AcademicLoadOrderType;

import java.io.ByteArrayInputStream;
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
                List.of(
                        new AcademicLoadOrderDocumentService.CurriculumPlanRow("СП-1", "Начальное общее образование", "1А, 1Б, 2А, 2Б"),
                        new AcademicLoadOrderDocumentService.CurriculumPlanRow("СП-1", "Основное общее образование", "5А, 5Б, 6А, 6Б"),
                        new AcademicLoadOrderDocumentService.CurriculumPlanRow("СП-2", "Среднее общее образование", "10А, 11А")
                ),
                List.of()
        );

        byte[] document = service.generate(data);
        assertDocument(document, "Об утверждении учебных планов", "Перечень утверждаемых учебных планов", "СП-2");
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
