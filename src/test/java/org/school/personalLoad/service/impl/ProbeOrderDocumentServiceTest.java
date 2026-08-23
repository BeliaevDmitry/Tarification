package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
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
            XWPFParagraph requisites = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains("от 17.09.2026 г."))
                    .findFirst()
                    .orElseThrow();
            List<XWPFParagraph> orderItems = document.getParagraphs().stream()
                    .filter(paragraph -> BigInteger.valueOf(5).equals(paragraph.getNumID()))
                    .collect(Collectors.toList());
            List<XWPFParagraph> instructionBullets = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().trim().startsWith("- "))
                    .collect(Collectors.toList());
            XWPFParagraph executor = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().startsWith("Исп.:"))
                    .findFirst()
                    .orElseThrow();
            XWPFParagraph companions = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains("Петрова Мария Сергеевна +7 (999)"))
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
            assertFalse(text.contains("9А, 9Б классы ГБОУ Школа № 7,"));
            assertEquals(3, participantTable.getNumberOfRows());
            assertEquals("№", participantTable.getRow(0).getCell(0).getText());
            assertEquals("1", participantTable.getRow(1).getCell(0).getText());
            assertEquals("Смирнов Алексей Павлович", participantTable.getRow(1).getCell(1).getText());
            assertEquals("—", participantTable.getRow(2).getCell(2).getText());
            assertEquals("—", participantTable.getRow(2).getCell(3).getText());
            assertEquals(ParagraphAlignment.LEFT, requisites.getAlignment());
            assertEquals(STTabJc.RIGHT,
                    requisites.getCTP().getPPr().getTabs().getTabArray(0).getVal());
            assertEquals(BigInteger.valueOf(9921),
                    requisites.getCTP().getPPr().getTabs().getTabArray(0).getPos());
            assertTrue(requisites.getRuns().stream()
                    .allMatch(run -> UnderlinePatterns.SINGLE.equals(run.getUnderline())));
            assertTrue(participantTable.getRows().stream()
                    .flatMap(row -> row.getTableCells().stream())
                    .flatMap(cell -> cell.getParagraphs().stream())
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .allMatch(run -> Integer.valueOf(14).equals(run.getFontSize())));
            assertEquals(STTblWidth.DXA, participantTable.getCTTbl().getTblPr().getTblW().getType());
            assertEquals(BigInteger.valueOf(9921), participantTable.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(List.of(
                            BigInteger.valueOf(497), BigInteger.valueOf(2923),
                            BigInteger.valueOf(3284), BigInteger.valueOf(3217)),
                    participantTable.getCTTbl().getTblGrid().getGridColList().stream()
                            .map(column -> (BigInteger) column.getW())
                            .collect(Collectors.toList()));
            assertTrue(participantTable.getRows().stream()
                    .flatMap(row -> row.getTableCells().stream())
                    .flatMap(cell -> cell.getParagraphs().stream())
                    .allMatch(paragraph -> ParagraphAlignment.LEFT.equals(paragraph.getAlignment())));
            double highlightCount = (double) XPathFactory.newInstance().newXPath().evaluate(
                    "count(//*[local-name()='highlight'])",
                    document.getDocument().getDomNode(),
                    XPathConstants.NUMBER);
            double directRunShadingCount = (double) XPathFactory.newInstance().newXPath().evaluate(
                    "count(//*[local-name()='rPr']/*[local-name()='shd'])",
                    document.getDocument().getDomNode(),
                    XPathConstants.NUMBER);
            double markerColorCount = (double) XPathFactory.newInstance().newXPath().evaluate(
                    "count(//*[local-name()='color' and translate(@*[local-name()='val'], 'abcdef', 'ABCDEF')='F9FAFB'])",
                    document.getDocument().getDomNode(),
                    XPathConstants.NUMBER);
            assertEquals(0D, highlightCount);
            assertEquals(0D, directRunShadingCount);
            assertEquals(0D, markerColorCount);
            assertEquals(7, orderItems.size());
            assertTrue(orderItems.get(0).getText().trim().startsWith("Направить "));
            assertTrue(orderItems.get(1).getText().trim().startsWith("Назначить руководителем группы"));
            assertTrue(orderItems.get(2).getText().trim().startsWith("Сбор обучающихся"));
            assertTrue(orderItems.get(3).getText().trim().startsWith("Руководителю группы"));
            assertTrue(orderItems.get(4).getText().trim().startsWith("Специалисту по охране труда"));
            assertTrue(orderItems.get(5).getText().trim().startsWith("Руководителю группы"));
            assertTrue(orderItems.get(6).getText().trim().startsWith("Контроль за исполнением"));
            assertTrue(orderItems.stream().allMatch(paragraph -> BigInteger.ZERO.equals(paragraph.getNumIlvl())));
            assertTrue(orderItems.stream().allMatch(paragraph -> paragraph.getIndentationLeft() == 0));
            assertTrue(orderItems.stream().allMatch(paragraph -> paragraph.getIndentationFirstLine() == 851));
            assertTrue(orderItems.stream().allMatch(paragraph -> paragraph.getIndentationHanging() == -1));
            assertTrue(orderItems.stream().allMatch(paragraph -> "ac".equals(paragraph.getStyle())));
            assertTrue(orderItems.stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .noneMatch(run -> Boolean.TRUE.equals(run.isBold())));
            assertEquals(4, instructionBullets.size());
            assertTrue(instructionBullets.stream().allMatch(paragraph -> paragraph.getNumID() == null));
            assertTrue(instructionBullets.get(1).getText().startsWith("- довести до сведения родителей"));
            assertTrue(instructionBullets.get(2).getText().startsWith("- собрать согласия"));
            assertTrue(instructionBullets.get(3).getText().startsWith("- по окончании мероприятия"));
            assertTrue(executor.getRuns().stream()
                    .allMatch(run -> Integer.valueOf(11).equals(run.getFontSize())));
            assertTrue(companions.getText().contains("+7 (999) 100-20-30"));
            assertTrue(companions.getText().contains("+7 (999) 300-40-50"));
            assertEquals(1.5D, companions.getSpacingBetween(), 0.001D);
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
