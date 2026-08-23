package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeOrderDocumentServiceTest {

    @Test
    void fillsReferenceTemplateAndReplacesSampleParticipantRows() throws Exception {
        ProbeOrderDocumentService service = new ProbeOrderDocumentService();
        byte[] content = service.generate(sampleData());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));
            XWPFTable participantTable = document.getTables().stream()
                    .filter(table -> table.getRow(0).getCell(1).getText().contains("ФИО"))
                    .findFirst()
                    .orElseThrow();

            assertTrue(text.contains("№ 184/пр"));
            assertTrue(text.contains("17.09.2026"));
            assertTrue(text.contains("Иванова И.И."));
            assertTrue(text.contains("Московский колледж технологий"));
            assertTrue(text.contains("Исп.: Петрова М.С."));
            assertTrue(text.contains("Орлова Светлана Викторовна"));
            assertFalse(text.contains("Жданова"));
            assertTrue(text.contains("Коваленко А.А."));
            assertTrue(text.contains("Беляковой И.В."));
            assertFalse(text.contains("Власова"));
            assertFalse(text.contains("{eventDate}"));
            assertFalse(text.contains("{className}"));
            assertFalse(text.contains("{"));
            assertEquals(3, participantTable.getNumberOfRows());
            assertEquals("Смирнов Алексей Павлович", participantTable.getRow(1).getCell(1).getText());
            assertEquals("—", participantTable.getRow(2).getCell(2).getText());
            assertEquals("—", participantTable.getRow(2).getCell(3).getText());
        }

        String qaOutput = System.getProperty("probe.qa.output");
        if (qaOutput != null && !qaOutput.isBlank()) {
            Path output = Path.of(qaOutput).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            Files.write(output, content);
        }
    }

    private ProbeOrderDocumentService.DocumentData sampleData() {
        ProbeOrderDocumentService.PersonData primary = new ProbeOrderDocumentService.PersonData(
                1L, "Петрова Мария Сергеевна", "Петровой Марии Сергеевне",
                "Петрову Марию Сергеевну", "Петрова М.С.", "+7 999 100-20-30");
        ProbeOrderDocumentService.PersonData secondary = new ProbeOrderDocumentService.PersonData(
                2L, "Сидоров Андрей Олегович", "Сидорову Андрею Олеговичу",
                "Сидорова Андрея Олеговича", "Сидоров А.О.", "+7 999 200-30-40");
        ProbeOrderDocumentService.PersonData signer = new ProbeOrderDocumentService.PersonData(
                3L, "Иванова Ирина Игоревна", "Ивановой Ирине Игоревне",
                "Иванову Ирину Игоревну", "Иванова И.И.", null);
        ProbeOrderDocumentService.PersonData additional = new ProbeOrderDocumentService.PersonData(
                4L, "Орлова Светлана Викторовна", "Орловой Светлане Викторовне",
                "Орлову Светлану Викторовну", "Орлова С.В.", "+7 999 300-40-50");
        return new ProbeOrderDocumentService.DocumentData(
                "2026/2027", "184/пр", LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 22), LocalTime.of(10, 30), "9А, 9Б", "классов",
                "Московский колледж технологий", "Москва, Учебная улица, дом 7",
                LocalTime.of(9, 20), "главный вход корпуса 2", LocalTime.of(14, 10),
                "Кузнецова Елена Викторовна", primary, secondary, List.of(additional),
                signer, "Заместитель директора",
                signer, signer, primary,
                List.of(
                        new ProbeOrderDocumentService.ParticipantData(
                                "Смирнов Алексей Павлович", "Смирнова Ольга Ивановна", "+7 900 111-22-33"),
                        new ProbeOrderDocumentService.ParticipantData(
                                "Фёдорова Анна Максимовна", null, null)
                ));
    }
}
