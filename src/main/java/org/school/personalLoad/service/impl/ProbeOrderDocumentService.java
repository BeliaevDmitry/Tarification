package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProbeOrderDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TEMPLATE = "templates/prikaz_template.docx";
    private static final String WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final BigInteger RIGHT_TAB_POSITION = BigInteger.valueOf(9921);

    public byte[] generate(DocumentData data) {
        if (data == null) {
            throw new IllegalArgumentException("Данные приказа не переданы");
        }
        try (InputStream in = openTemplate();
             XWPFDocument document = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<String, String> replacements = replacements(data);
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                fillParagraph(paragraph, data, replacements);
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            fillParagraph(paragraph, data, replacements);
                        }
                    }
                }
            }
            replaceParticipantTable(document, data.participants());
            removeTemplateMarkers(document);
            document.write(out);
            return out.toByteArray();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось сформировать Word-приказ: " + rootMessage(exception), exception);
        }
    }

    private InputStream openTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE);
        if (resource.exists()) return resource.getInputStream();
        throw new IllegalStateException("Внутренний шаблон Word-приказа отсутствует в запущенной сборке");
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private Map<String, String> replacements(DocumentData data) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{eventDate}", formatDate(data.eventDate()));
        values.put("{className}", data.formattedClasses());
        values.put("{number}", String.valueOf(data.participants().size()));
        values.put("{venue}", text(data.venue()));
        values.put("{address}", text(data.eventAddress()));
        values.put("{eventTime}", formatTime(data.startTime()));
        values.put("{leader}", data.primaryCompanion().dativeOrName());
        values.put("{deputy}", "");
        values.put("{gatheringTime}", formatTime(data.gatheringTime()));
        values.put("{gatheringPlace}", text(data.gatheringPlace()));
        values.put("{returnTime}", formatTime(data.returnTime()));
        values.put("{curator}", text(data.curator()));
        values.put("{leaderDative}", data.primaryCompanion().dativeOrName());
        values.put("{classWord}", data.classWord());
        values.put("{accompanyingTitle}", allCompanions(data).size() == 1
                ? "Сопровождающий" : "Сопровождающие");
        values.put("{accompanying}", companionLines(data));
        return values;
    }

    private void fillParagraph(XWPFParagraph paragraph,
                               DocumentData data,
                               Map<String, String> replacements) {
        String original = paragraph.getText();
        if (original == null || original.isBlank()) {
            return;
        }
        if (original.contains("Направить {eventDate}")) {
            replaceParagraph(paragraph, actionParagraph(data));
            applyOrderListNumbering(paragraph);
            return;
        }
        if (original.trim().startsWith("от") && original.contains("№")) {
            replaceOrderRequisitesParagraph(paragraph, data);
            return;
        }
        if (original.contains("к Приказу №")) {
            replaceParagraph(paragraph, "к Приказу № " + text(data.orderNumber())
                    + " от " + formatDate(data.orderDate()) + " г.", 12);
            paragraph.setAlignment(ParagraphAlignment.RIGHT);
            return;
        }
        if (original.contains("Директор") && original.contains("Жданова")) {
            replaceSignatureParagraph(paragraph, data);
            return;
        }
        if (original.trim().startsWith("5.") && original.contains("охране труда")) {
            // Ответственный за безопасность постоянный и уже указан в утверждённом шаблоне.
            // Не подменяем его сотрудником из справочника при формировании приказа.
            replaceParagraph(paragraph, removeManualNumber(original));
            applyOrderListNumbering(paragraph);
            return;
        }
        if (original.trim().startsWith("6.") && original.contains("Ждановой И. Д.")) {
            String updatedPersonnel = removeManualNumber(original)
                    .replace("Ждановой И. Д.", dative(data.director()))
                    .replace("Власовой Ю.С.", dative(data.deputyDirector()))
                    .replace("{leader}", data.primaryCompanion().dativeOrName());
            replaceParagraph(paragraph, cleanup(updatedPersonnel));
            applyOrderListNumbering(paragraph);
            return;
        }
        if (original.trim().startsWith("7.") && original.contains("Контроль за исполнением")) {
            replaceParagraph(paragraph, removeManualNumber(original));
            applyOrderListNumbering(paragraph);
            return;
        }
        if (original.trim().startsWith("Исп.:")) {
            replaceParagraph(paragraph, "Исп.: " + executorName(data.executor()));
            return;
        }
        if (original.trim().equals("8-916-116-02-21")) {
            replaceParagraph(paragraph, data.executor() == null || text(data.executor().phone()).isBlank()
                    ? "Телефон исполнителя не указан" : data.executor().phone());
            return;
        }

        String updated = original
                .replace("2025–2026", text(data.academicYear()).replace('/', '–'))
                .replace("2025-2026", text(data.academicYear()).replace('/', '-'));
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            updated = updated.replace(entry.getKey(), entry.getValue());
        }
        if (original.contains("{className}") && original.contains("ГБОУ Школа № 7")) {
            String headingWord = data.formattedClasses().contains(",") ? "классы" : "класс";
            updated = updated.replace(data.formattedClasses() + "класс",
                    data.formattedClasses() + " " + headingWord);
        }
        boolean numberedOrderItem = original.contains("Сбор обучающихся назначить")
                || original.contains("Руководителю группы {leader}");
        if (original.contains("по окончании мероприятия доложить")) {
            updated = updated.replaceFirst("^\\s*-\\s*", "");
            if (updated.startsWith("по окончании")) {
                updated = "По" + updated.substring(2);
            }
            numberedOrderItem = true;
        }
        updated = cleanup(updated);
        if (!Objects.equals(original, updated)) {
            replaceParagraph(paragraph, updated);
        }
        if (numberedOrderItem) {
            applyOrderListNumbering(paragraph);
        }
    }

    private String actionParagraph(DocumentData data) {
        String secondary = data.secondaryCompanion() == null
                ? ""
                : ", заместителем руководителя группы " + data.secondaryCompanion().accusativeOrName();
        String additional = data.additionalCompanions().isEmpty()
                ? ""
                : ", сопровождающими " + data.additionalCompanions().stream()
                .map(PersonData::accusativeOrName).collect(java.util.stream.Collectors.joining(", "));
        String pronoun = allCompanions(data).size() == 1 ? "него" : "них";
        return "Направить " + formatDate(data.eventDate()) + " года обучающихся "
                + text(data.formattedClasses()) + " " + text(data.classWord())
                + " ГБОУ Школа № 7 в количестве " + data.participants().size()
                + " человек согласно списку (Приложение 1) на профессиональную пробу в рамках проекта "
                + "«Мастерство начинается здесь» в " + text(data.venue()) + " по адресу: "
                + text(data.eventAddress()) + " к " + formatTime(data.startTime())
                + ". Назначить руководителем группы " + data.primaryCompanion().accusativeOrName()
                + secondary + additional + " и возложить на " + pronoun
                + " ответственность за жизнь и здоровье несовершеннолетних участников мероприятия во время "
                + "выездного мероприятия, а также по всему маршруту следования, от места сбора группы до места "
                + "проведения мероприятия и обратно.";
    }

    private void replaceOrderRequisitesParagraph(XWPFParagraph paragraph, DocumentData data) {
        clearRuns(paragraph);
        configureRightTab(paragraph);
        XWPFRun left = paragraph.createRun();
        styleRun(left, true, 14);
        left.setText("от " + formatDate(data.orderDate()) + " г.");
        left.addTab();
        XWPFRun right = paragraph.createRun();
        styleRun(right, true, 14);
        right.setText("№ " + text(data.orderNumber()));
    }

    private void replaceSignatureParagraph(XWPFParagraph paragraph, DocumentData data) {
        clearRuns(paragraph);
        configureRightTab(paragraph);
        XWPFRun position = paragraph.createRun();
        styleRun(position, true, 14);
        position.setText(text(data.signerPosition()));
        position.addTab();
        XWPFRun signer = paragraph.createRun();
        styleRun(signer, true, 14);
        signer.setText(data.signer().initialsOrName());
    }

    private void replaceParticipantTable(XWPFDocument document, List<ParticipantData> participants) {
        XWPFTable target = document.getTables().stream()
                .filter(table -> !table.getRows().isEmpty())
                .filter(table -> table.getRow(0).getTableCells().stream()
                        .map(XWPFTableCell::getText)
                        .anyMatch(value -> value != null && value.contains("ФИО")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В шаблоне не найдена таблица участников"));

        XWPFTableRow header = target.getRow(0);
        ensureCells(header, 4);
        setCell(header.getCell(0), "№", ParagraphAlignment.CENTER);
        setCell(header.getCell(1), "ФИО", ParagraphAlignment.CENTER);
        setCell(header.getCell(2), "ФИО представителя", ParagraphAlignment.CENTER);
        setCell(header.getCell(3), "Телефон представителя", ParagraphAlignment.CENTER);

        while (target.getNumberOfRows() > 1) {
            target.removeRow(target.getNumberOfRows() - 1);
        }
        int index = 1;
        for (ParticipantData participant : participants) {
            XWPFTableRow row = target.createRow();
            ensureCells(row, 4);
            setCell(row.getCell(0), index + ".", ParagraphAlignment.CENTER);
            setCell(row.getCell(1), text(participant.fullName()), ParagraphAlignment.LEFT);
            setCell(row.getCell(2), dashIfBlank(participant.representativeName()), ParagraphAlignment.LEFT);
            setCell(row.getCell(3), dashIfBlank(participant.representativePhone()), ParagraphAlignment.CENTER);
            index++;
        }
    }

    private void ensureCells(XWPFTableRow row, int count) {
        while (row.getTableCells().size() < count) {
            row.addNewTableCell();
        }
    }

    private void setCell(XWPFTableCell cell, String value, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphs().get(0);
        clearRuns(paragraph);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);
        XWPFRun run = paragraph.createRun();
        styleRun(run, false, 14);
        run.setText(value);
    }

    private void replaceParagraph(XWPFParagraph paragraph, String value) {
        replaceParagraph(paragraph, value, 14);
    }

    private void replaceParagraph(XWPFParagraph paragraph, String value, int size) {
        boolean bold = paragraph.getRuns().stream().anyMatch(run -> Boolean.TRUE.equals(run.isBold()));
        clearRuns(paragraph);
        String[] lines = text(value).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            styleRun(run, bold, size);
            run.setText(lines[i]);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private void configureRightTab(XWPFParagraph paragraph) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTTabs tabs = properties.isSetTabs() ? properties.getTabs() : properties.addNewTabs();
        while (tabs.sizeOfTabArray() > 0) {
            tabs.removeTab(0);
        }
        CTTabStop rightTab = tabs.addNewTab();
        rightTab.setVal(STTabJc.RIGHT);
        rightTab.setPos(RIGHT_TAB_POSITION);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setIndentationLeft(0);
        paragraph.setIndentationRight(0);
        paragraph.setIndentationHanging(0);
        paragraph.setIndentationFirstLine(0);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
    }

    private void applyOrderListNumbering(XWPFParagraph paragraph) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (properties.isSetTabs()) {
            properties.unsetTabs();
        }
        paragraph.setNumID(BigInteger.ONE);
        paragraph.setNumILvl(BigInteger.ZERO);
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setIndentationLeft(0);
        paragraph.setIndentationRight(0);
        paragraph.setIndentationHanging(0);
        paragraph.setIndentationFirstLine(0);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);

        if (!paragraph.getRuns().isEmpty()) {
            XWPFRun firstRun = paragraph.getRuns().get(0);
            String firstText = firstRun.getText(0);
            if (firstText != null && !firstText.startsWith(" ")) {
                firstRun.setText(" " + firstText, 0);
            }
        }
    }

    private String removeManualNumber(String value) {
        return text(value).replaceFirst("^\\d+\\.\\s*", "");
    }

    private void removeTemplateMarkers(XWPFDocument document) {
        removeTemplateMarkers(document.getDocument().getDomNode());
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                removeTemplateMarkers(paragraph.getCTP().getDomNode());
            }
            for (XWPFTable table : header.getTables()) {
                removeTemplateMarkers(table);
            }
        }
        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                removeTemplateMarkers(paragraph.getCTP().getDomNode());
            }
            for (XWPFTable table : footer.getTables()) {
                removeTemplateMarkers(table);
            }
        }
    }

    private void removeTemplateMarkers(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    removeTemplateMarkers(paragraph.getCTP().getDomNode());
                }
                for (XWPFTable nestedTable : cell.getTables()) {
                    removeTemplateMarkers(nestedTable);
                }
            }
        }
    }

    private void removeTemplateMarkers(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            boolean wordElement = WORD_NAMESPACE.equals(child.getNamespaceURI());
            boolean highlight = wordElement && "highlight".equals(child.getLocalName());
            boolean directRunShading = wordElement && "shd".equals(child.getLocalName())
                    && child.getParentNode() != null
                    && "rPr".equals(child.getParentNode().getLocalName());
            if (highlight || directRunShading) {
                parent.removeChild(child);
            } else {
                removeTemplateMarkers(child);
            }
            child = next;
        }
    }

    private void clearRuns(XWPFParagraph paragraph) {
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
    }

    private void styleRun(XWPFRun run, boolean bold, int size) {
        run.setFontFamily("Times New Roman");
        run.setFontSize(size);
        run.setBold(bold);
    }

    private String companionLines(DocumentData data) {
        return allCompanions(data).stream()
                .map(person -> person.fullName() + (text(person.phone()).isBlank() ? "" : " " + person.phone()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private List<PersonData> allCompanions(DocumentData data) {
        List<PersonData> people = new ArrayList<>();
        people.add(data.primaryCompanion());
        if (data.secondaryCompanion() != null) people.add(data.secondaryCompanion());
        people.addAll(data.additionalCompanions());
        return people;
    }

    private String cleanup(String value) {
        String cleaned = text(value)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+,", ",")
                .replaceAll(",\\s*,", ",")
                .replaceAll("\\s+:", ":")
                .replaceAll("(?<=\\d)по адресу", " по адресу")
                .replaceAll(" {2,}", " ")
                .trim();
        return cleaned.replaceFirst("^(\\d+\\.)\\s+", "$1 ");
    }

    private String dashIfBlank(String value) {
        return text(value).isBlank() ? "—" : value.trim();
    }

    private String dative(PersonData person) {
        return person == null ? "" : text(person.dativeOrName());
    }

    private String executorName(PersonData person) {
        return person == null ? "исполнитель не указан" : text(person.initialsOrName());
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    private String formatTime(LocalTime value) {
        return value == null ? "" : TIME.format(value);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record PersonData(Long id,
                             String fullName,
                             String dative,
                             String accusative,
                             String initials,
                             String phone) {
        public String dativeOrName() {
            return dative == null || dative.isBlank() ? fullName : dative;
        }

        public String accusativeOrName() {
            return accusative == null || accusative.isBlank() ? fullName : accusative;
        }

        public String initialsOrName() {
            return initials == null || initials.isBlank() ? fullName : initials;
        }
    }

    public record ParticipantData(String fullName, String representativeName, String representativePhone) {
    }

    public record DocumentData(String academicYear,
                               String orderNumber,
                               LocalDate orderDate,
                               LocalDate eventDate,
                               LocalTime startTime,
                               String formattedClasses,
                               String classWord,
                               String venue,
                               String eventAddress,
                               LocalTime gatheringTime,
                               String gatheringPlace,
                               LocalTime returnTime,
                               String curator,
                               PersonData primaryCompanion,
                               PersonData secondaryCompanion,
                               List<PersonData> additionalCompanions,
                               PersonData signer,
                               String signerPosition,
                               PersonData director,
                               PersonData deputyDirector,
                               PersonData executor,
                               List<ParticipantData> participants) {
        public DocumentData {
            additionalCompanions = additionalCompanions == null ? List.of() : List.copyOf(additionalCompanions);
        }

        public DocumentData(String academicYear,
                            String orderNumber,
                            LocalDate orderDate,
                            LocalDate eventDate,
                            LocalTime startTime,
                            String formattedClasses,
                            String classWord,
                            String venue,
                            String eventAddress,
                            LocalTime gatheringTime,
                            String gatheringPlace,
                            LocalTime returnTime,
                            String curator,
                            PersonData primaryCompanion,
                            PersonData secondaryCompanion,
                            PersonData signer,
                            String signerPosition,
                            PersonData director,
                            PersonData deputyDirector,
                            PersonData executor,
                            List<ParticipantData> participants) {
            this(academicYear, orderNumber, orderDate, eventDate, startTime, formattedClasses, classWord, venue,
                    eventAddress, gatheringTime, gatheringPlace, returnTime, curator, primaryCompanion,
                    secondaryCompanion, List.of(), signer, signerPosition, director, deputyDirector, executor,
                    participants);
        }
    }
}
