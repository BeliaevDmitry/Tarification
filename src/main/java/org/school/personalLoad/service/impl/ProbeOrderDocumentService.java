package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
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
    private static final BigInteger ORDER_NUMBERING_ID = BigInteger.valueOf(5);
    private static final BigInteger ORDER_FIRST_LINE = BigInteger.valueOf(851);
    private static final BigInteger PARTICIPANT_TABLE_WIDTH = BigInteger.valueOf(9921);
    private static final BigInteger[] PARTICIPANT_COLUMN_WIDTHS = {
            BigInteger.valueOf(497), BigInteger.valueOf(2500), BigInteger.valueOf(1600),
            BigInteger.valueOf(3324), BigInteger.valueOf(2000)
    };
    private static final String TEMPLATE_MARKER_COLOR = "F9FAFB";

    public byte[] generate(DocumentData data) {
        if (data == null) {
            throw new IllegalArgumentException("Данные приказа не переданы");
        }
        try (InputStream in = openTemplate();
             XWPFDocument document = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            rebuildOrderBody(document, data);
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
            removeBlankParagraphsBeforeParticipantTable(document);
            placeExecutorOnLastOrderPage(document, data);
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
        return values;
    }

    private void fillParagraph(XWPFParagraph paragraph,
                               DocumentData data,
                               Map<String, String> replacements) {
        String original = paragraph.getText();
        if (original == null || original.isBlank()) {
            return;
        }
        if (original.trim().startsWith("от") && original.contains("№")) {
            replaceOrderRequisitesParagraph(paragraph, data);
            return;
        }
        if (original.trim().matches("Приложение\\s*(?:№\\s*)?1")) {
            replaceParagraph(paragraph, "Приложение № 1", 12, false);
            paragraph.setAlignment(ParagraphAlignment.RIGHT);
            paragraph.setPageBreak(true);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
            return;
        }
        if (original.contains("к Приказу №")) {
            replaceParagraph(paragraph, "к Приказу № " + text(data.orderNumber())
                    + " от " + formatDate(data.orderDate()) + " г.", 12, false);
            paragraph.setAlignment(ParagraphAlignment.RIGHT);
            return;
        }
        if (original.contains("Директор") && original.contains("Жданова")) {
            replaceSignatureParagraph(paragraph, data);
            return;
        }
        if (original.contains("{className}") && original.contains("ГБОУ Школа № 7")) {
            clearParagraph(paragraph);
            return;
        }
        if (original.trim().equals("{eventDate}.")) {
            clearParagraph(paragraph);
            return;
        }
        if (original.contains("{accompanyingTitle}") || original.trim().equals("Сопровождающие:")) {
            replaceParagraph(paragraph, replacements.get("{accompanyingTitle}") + ":", 14, false);
            paragraph.setAlignment(ParagraphAlignment.LEFT);
            return;
        }
        if (original.contains("{accompanying}")) {
            replaceCompanionsParagraph(paragraph, data);
            return;
        }
        if (original.trim().startsWith("Исп.:")) {
            replaceParagraph(paragraph, "Исп.: " + executorName(data.executor()), 11, false);
            return;
        }
        if (original.trim().equals("8-916-116-02-21")) {
            replaceParagraph(paragraph, data.executor() == null || text(data.executor().phone()).isBlank()
                    ? "Телефон исполнителя не указан" : data.executor().phone(), 11, false);
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
        if (!Objects.equals(original, updated)) {
            replaceParagraph(paragraph, cleanup(updated), 14, paragraphIsBold(paragraph));
        }
    }

    private void rebuildOrderBody(XWPFDocument document, DocumentData data) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int first = -1;
        int signature = -1;
        for (int i = 0; i < paragraphs.size(); i++) {
            String value = paragraphs.get(i).getText();
            if (first < 0 && value != null && value.contains("Направить {eventDate}")) {
                first = i;
            }
            if (value != null && value.contains("Директор") && value.contains("Жданова")) {
                signature = i;
                break;
            }
        }
        if (first < 0 || signature < 0 || signature - first < 11) {
            throw new IllegalStateException("В шаблоне не найден полный блок пунктов приказа");
        }

        String listStyle = paragraphs.subList(first, signature).stream()
                .map(XWPFParagraph::getStyle)
                .filter(style -> style != null && !style.isBlank() && !"a".equals(style))
                .findFirst()
                .orElse(null);
        String normalStyle = paragraphs.get(first).getStyle();

        setNumberedParagraph(paragraphs.get(first), movementParagraph(data), listStyle);
        setNumberedParagraph(paragraphs.get(first + 1), appointmentParagraph(data), listStyle);
        setNumberedParagraph(paragraphs.get(first + 2), gatheringParagraph(data), listStyle);
        setNumberedParagraph(paragraphs.get(first + 3), leaderTaskHeading(data), listStyle);
        setBulletParagraph(paragraphs.get(first + 4),
                "- провести с обучающимися, участниками выездного мероприятия, инструктажи по правилам "
                        + "безопасности, соблюдению санитарных норм, правилам поведения на объектах культуры "
                        + "и общественных местах, в общественном транспорте, а также правилам и действиям в "
                        + "чрезвычайной ситуации, с обязательной фиксацией в «Журнале инструктажа по безопасности»;",
                normalStyle);
        setBulletParagraph(paragraphs.get(first + 5),
                "- довести до сведения родителей (законных представителей) полную информацию о проведении "
                        + "выездного мероприятия;",
                normalStyle);
        setBulletParagraph(paragraphs.get(first + 6),
                "- собрать согласия (отказ) от родителей (законных представителей) на участие обучающихся "
                        + "в мероприятии через электронную систему МЭШ;",
                normalStyle);
        setBulletParagraph(paragraphs.get(first + 7), curatorReportParagraph(data), normalStyle);
        setNumberedParagraph(paragraphs.get(first + 8),
                "Специалисту по охране труда Беляковой И.В. обеспечить своевременное проведение инструктажей "
                        + "с должностными лицами, ответственными за проведение мероприятия.",
                listStyle);
        setNumberedParagraph(paragraphs.get(first + 9), safetyParagraph(data), listStyle);
        setNumberedParagraph(paragraphs.get(first + 10),
                "Контроль за исполнением настоящего Приказа оставляю за собой.", listStyle);

        for (int i = first + 11; i < signature; i++) {
            clearParagraph(paragraphs.get(i));
        }
    }

    private String movementParagraph(DocumentData data) {
        return cleanup("Направить " + formatDate(data.eventDate()) + " года обучающихся "
                + text(data.formattedClasses()) + " " + text(data.classWord())
                + " ГБОУ Школа № 7 в количестве " + data.participants().size()
                + " человек согласно списку (Приложение 1) на мероприятие в рамках проекта "
                + "«Мастерство начинается здесь» в " + text(data.venue()) + " по адресу: "
                + text(data.eventAddress()) + " к " + formatTime(data.startTime()) + ".");
    }

    private String appointmentParagraph(DocumentData data) {
        String secondary = data.secondaryCompanion() == null
                ? ""
                : ", заместителем руководителя группы " + accusativeInitials(data.secondaryCompanion());
        String additional = data.additionalCompanions().isEmpty()
                ? ""
                : ", сопровождающими " + data.additionalCompanions().stream()
                .map(this::accusativeInitials).collect(java.util.stream.Collectors.joining(", "));
        String pronoun = allCompanions(data).size() == 1 ? "него" : "них";
        return cleanup("Назначить руководителем группы " + accusativeInitials(data.primaryCompanion())
                + secondary + additional + " и возложить на " + pronoun
                + " ответственность за жизнь и здоровье несовершеннолетних участников мероприятия во время "
                + "выездного мероприятия, а также по всему маршруту следования, от места сбора группы до места "
                + "проведения мероприятия и обратно.");
    }

    private String gatheringParagraph(DocumentData data) {
        return cleanup("Сбор обучающихся назначить на " + formatTime(data.gatheringTime()) + " по адресу: "
                + text(data.gatheringPlace()) + ", возвращение к школе в " + formatTime(data.returnTime()) + ".");
    }

    private String leaderTaskHeading(DocumentData data) {
        return cleanup("Руководителю группы " + dativeInitials(data.primaryCompanion()) + ":");
    }

    private String curatorReportParagraph(DocumentData data) {
        String curator = text(data.curator());
        return "- по окончании мероприятия доложить о прибытии куратору корпуса"
                + (curator.isBlank() ? "." : " " + surnameInitials(curator) + ".");
    }

    private String safetyParagraph(DocumentData data) {
        List<String> recipients = new ArrayList<>();
        if (!dativeInitials(data.director()).isBlank()) {
            recipients.add("директору школы " + dativeInitials(data.director()));
        }
        // Ответственный за безопасность постоянный и закреплён в утверждённом тексте приказа.
        recipients.add("специалисту по безопасности Коваленко А.А.");
        if (!dativeInitials(data.deputyDirector()).isBlank()) {
            recipients.add("заместителю директора " + dativeInitials(data.deputyDirector()));
        }
        return cleanup("Руководителю группы " + dativeInitials(data.primaryCompanion())
                + " неукоснительно соблюдать требования мер безопасности при проведении мероприятия. "
                + "В случае возникновения чрезвычайных ситуаций или других непредвиденных инцидентах "
                + "немедленно сообщать " + String.join(", ", recipients) + ".");
    }

    private void replaceOrderRequisitesParagraph(XWPFParagraph paragraph, DocumentData data) {
        clearRuns(paragraph);
        configureRightTab(paragraph);
        clearParagraphMarkUnderline(paragraph);
        XWPFRun left = paragraph.createRun();
        styleRun(left, true, 14);
        left.setText("от " + formatDate(data.orderDate()) + " г.");
        left.addTab();
        XWPFRun right = paragraph.createRun();
        styleRun(right, true, 14);
        right.setText("№ " + text(data.orderNumber()));
    }

    private void clearParagraphMarkUnderline(XWPFParagraph paragraph) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (!properties.isSetRPr()) return;
        CTParaRPr runProperties = properties.getRPr();
        while (runProperties.sizeOfUArray() > 0) {
            runProperties.removeU(0);
        }
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
        ensureCells(header, 5);
        setCell(header.getCell(0), "№", ParagraphAlignment.LEFT);
        setCell(header.getCell(1), "ФИО", ParagraphAlignment.LEFT);
        setCell(header.getCell(2), "Телефон ребёнка", ParagraphAlignment.LEFT);
        setCell(header.getCell(3), "ФИО представителя", ParagraphAlignment.LEFT);
        setCell(header.getCell(4), "Телефон представителя", ParagraphAlignment.LEFT);

        while (target.getNumberOfRows() > 1) {
            target.removeRow(target.getNumberOfRows() - 1);
        }
        int index = 1;
        for (ParticipantData participant : participants) {
            XWPFTableRow row = target.createRow();
            ensureCells(row, 5);
            setCell(row.getCell(0), String.valueOf(index), ParagraphAlignment.LEFT);
            setCell(row.getCell(1), text(participant.fullName()), ParagraphAlignment.LEFT);
            setCell(row.getCell(2), dashIfBlank(participant.childPhone()), ParagraphAlignment.LEFT);
            setCell(row.getCell(3), dashIfBlank(participant.representativeName()), ParagraphAlignment.LEFT);
            setCell(row.getCell(4), dashIfBlank(participant.representativePhone()), ParagraphAlignment.LEFT);
            index++;
        }
        applyParticipantTableGeometry(target);
    }

    private void removeBlankParagraphsBeforeParticipantTable(XWPFDocument document) {
        List<IBodyElement> elements = document.getBodyElements();
        int appendixReference = -1;
        int participantTable = -1;
        for (int i = 0; i < elements.size(); i++) {
            IBodyElement element = elements.get(i);
            if (element instanceof XWPFParagraph paragraph
                    && paragraph.getText().contains("к Приказу №")) {
                appendixReference = i;
            }
            if (appendixReference >= 0 && element instanceof XWPFTable table
                    && isParticipantTable(table)) {
                participantTable = i;
                break;
            }
        }
        if (appendixReference < 0 || participantTable < 0) {
            throw new IllegalStateException("В шаблоне не найдено начало таблицы приложения");
        }
        for (int i = participantTable - 1; i > appendixReference; i--) {
            IBodyElement element = document.getBodyElements().get(i);
            if (element instanceof XWPFParagraph paragraph && paragraph.getText().isBlank()) {
                document.removeBodyElement(i);
            }
        }
    }

    private boolean isParticipantTable(XWPFTable table) {
        return !table.getRows().isEmpty() && table.getRow(0).getTableCells().stream()
                .map(XWPFTableCell::getText)
                .anyMatch(value -> value != null && value.contains("ФИО"));
    }

    private void placeExecutorOnLastOrderPage(XWPFDocument document, DocumentData data) {
        List<IBodyElement> elements = document.getBodyElements();
        int signatureIndex = -1;
        int appendixIndex = -1;
        XWPFParagraph signature = null;
        XWPFParagraph appendix = null;
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof XWPFParagraph paragraph)) continue;
            String value = paragraph.getText();
            if (signatureIndex < 0 && value.contains(text(data.signerPosition()))
                    && value.contains(surnameInitials(data.signer() == null ? "" : data.signer().fullName()))) {
                signatureIndex = i;
                signature = paragraph;
            }
            if (value.trim().equals("Приложение № 1")) {
                appendixIndex = i;
                appendix = paragraph;
                break;
            }
        }
        if (signatureIndex < 0 || appendixIndex < 0 || signature == null || appendix == null) {
            throw new IllegalStateException("В шаблоне не найден блок подписи или приложения");
        }

        for (int i = appendixIndex - 1; i > signatureIndex; i--) {
            document.removeBodyElement(i);
        }
        appendix.setPageBreak(false);

        CTBody body = document.getDocument().getBody();
        CTSectPr appendixSection = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTSectPr orderSection = (CTSectPr) appendixSection.copy();
        while (orderSection.sizeOfFooterReferenceArray() > 0) orderSection.removeFooterReference(0);
        if (orderSection.isSetType()) {
            orderSection.getType().setVal(STSectionMark.NEXT_PAGE);
        } else {
            orderSection.addNewType().setVal(STSectionMark.NEXT_PAGE);
        }
        CTPPr signatureProperties = signature.getCTP().isSetPPr()
                ? signature.getCTP().getPPr() : signature.getCTP().addNewPPr();
        signatureProperties.setSectPr(orderSection);
        orderSection = signatureProperties.getSectPr();

        XWPFHeaderFooterPolicy orderPolicy = new XWPFHeaderFooterPolicy(document, orderSection);
        XWPFFooter orderFooter = orderPolicy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        clearFooter(orderFooter);
        addLastSectionPageFooterLine(orderFooter, "Исп.: " + executorName(data.executor()));
        String phone = data.executor() == null || text(data.executor().phone()).isBlank()
                ? "Телефон исполнителя не указан" : data.executor().phone();
        addLastSectionPageFooterLine(orderFooter, phone);

        while (appendixSection.sizeOfFooterReferenceArray() > 0) appendixSection.removeFooterReference(0);
        XWPFHeaderFooterPolicy appendixPolicy = new XWPFHeaderFooterPolicy(document, appendixSection);
        XWPFFooter appendixFooter = appendixPolicy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        clearFooter(appendixFooter);
        XWPFParagraph empty = appendixFooter.createParagraph();
        empty.setSpacingBefore(0);
        empty.setSpacingAfter(0);
        document.getSettings().setUpdateFields();
    }

    private void clearFooter(XWPFFooter footer) {
        while (!footer.getParagraphs().isEmpty()) {
            footer.removeParagraph(footer.getParagraphs().get(0));
        }
        while (!footer.getTables().isEmpty()) {
            footer.removeTable(footer.getTables().get(0));
        }
    }

    private void addLastSectionPageFooterLine(XWPFFooter footer, String value) {
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0D);
        appendFieldChar(paragraph, STFldCharType.BEGIN);
        appendInstruction(paragraph, " IF ");
        appendSimpleField(paragraph, "PAGE", "1");
        appendInstruction(paragraph, " = ");
        appendSimpleField(paragraph, "SECTIONPAGES", "1");
        appendInstruction(paragraph, " \"" + text(value).replace('"', '\'') + "\" \"\" ");
        appendFieldChar(paragraph, STFldCharType.SEPARATE);
        XWPFRun result = paragraph.createRun();
        styleRun(result, false, 11);
        result.setText(value);
        appendFieldChar(paragraph, STFldCharType.END);
    }

    private void appendSimpleField(XWPFParagraph paragraph, String instruction, String resultText) {
        appendFieldChar(paragraph, STFldCharType.BEGIN);
        appendInstruction(paragraph, " " + instruction + " ");
        appendFieldChar(paragraph, STFldCharType.SEPARATE);
        XWPFRun result = paragraph.createRun();
        styleRun(result, false, 11);
        result.setText(resultText);
        appendFieldChar(paragraph, STFldCharType.END);
    }

    private void appendInstruction(XWPFParagraph paragraph, String instruction) {
        XWPFRun run = paragraph.createRun();
        styleRun(run, false, 11);
        run.getCTR().addNewInstrText().setStringValue(instruction);
    }

    private void appendFieldChar(XWPFParagraph paragraph, STFldCharType.Enum type) {
        XWPFRun run = paragraph.createRun();
        styleRun(run, false, 11);
        CTR ctr = run.getCTR();
        CTFldChar field = ctr.addNewFldChar();
        field.setFldCharType(type);
    }

    private void applyParticipantTableGeometry(XWPFTable table) {
        CTTblPr tableProperties = table.getCTTbl().getTblPr();
        if (tableProperties == null) {
            tableProperties = table.getCTTbl().addNewTblPr();
        }
        CTTblWidth tableWidth = tableProperties.isSetTblW()
                ? tableProperties.getTblW() : tableProperties.addNewTblW();
        tableWidth.setType(STTblWidth.DXA);
        tableWidth.setW(PARTICIPANT_TABLE_WIDTH);
        table.setCellMargins(80, 100, 80, 100);
        applyParticipantTableBorders(tableProperties);

        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (BigInteger columnWidth : PARTICIPANT_COLUMN_WIDTHS) {
            CTTblGridCol column = grid.addNewGridCol();
            column.setW(columnWidth);
        }

        for (XWPFTableRow row : table.getRows()) {
            ensureCells(row, PARTICIPANT_COLUMN_WIDTHS.length);
            for (int columnIndex = 0; columnIndex < PARTICIPANT_COLUMN_WIDTHS.length; columnIndex++) {
                XWPFTableCell cell = row.getCell(columnIndex);
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                        ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTblWidth cellWidth = cellProperties.isSetTcW()
                        ? cellProperties.getTcW() : cellProperties.addNewTcW();
                cellWidth.setType(STTblWidth.DXA);
                cellWidth.setW(PARTICIPANT_COLUMN_WIDTHS[columnIndex]);
            }
        }
    }

    private void applyParticipantTableBorders(CTTblPr tableProperties) {
        CTTblBorders borders = tableProperties.isSetTblBorders()
                ? tableProperties.getTblBorders() : tableProperties.addNewTblBorders();
        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private void setBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor("000000");
        border.setSpace(BigInteger.ZERO);
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

    private void replaceParagraph(XWPFParagraph paragraph, String value, int size, boolean bold) {
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

    private boolean paragraphIsBold(XWPFParagraph paragraph) {
        List<XWPFRun> textRuns = paragraph.getRuns().stream()
                .filter(run -> run.text() != null && !run.text().isBlank())
                .toList();
        return !textRuns.isEmpty() && textRuns.stream().allMatch(run -> Boolean.TRUE.equals(run.isBold()));
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

    private void setNumberedParagraph(XWPFParagraph paragraph, String value, String listStyle) {
        replaceParagraph(paragraph, value, 14, false);
        if (listStyle != null) {
            paragraph.setStyle(listStyle);
        }
        applyOrderListNumbering(paragraph);
    }

    private void setBulletParagraph(XWPFParagraph paragraph, String value, String normalStyle) {
        replaceParagraph(paragraph, value, 14, false);
        if (normalStyle != null) {
            paragraph.setStyle(normalStyle);
        }
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (properties.isSetNumPr()) {
            properties.unsetNumPr();
        }
        if (properties.isSetTabs()) {
            properties.unsetTabs();
        }
        if (properties.isSetSpacing()) {
            properties.unsetSpacing();
        }
        CTInd indentation = properties.isSetInd() ? properties.getInd() : properties.addNewInd();
        if (indentation.isSetLeft()) indentation.unsetLeft();
        if (indentation.isSetRight()) indentation.unsetRight();
        if (indentation.isSetHanging()) indentation.unsetHanging();
        indentation.setFirstLine(ORDER_FIRST_LINE);
        paragraph.setAlignment(ParagraphAlignment.BOTH);
    }

    private void applyOrderListNumbering(XWPFParagraph paragraph) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (properties.isSetTabs()) {
            properties.unsetTabs();
        }
        if (properties.isSetSpacing()) {
            properties.unsetSpacing();
        }
        clearParagraphMarkBold(properties);
        paragraph.setNumID(ORDER_NUMBERING_ID);
        paragraph.setNumILvl(BigInteger.ZERO);
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        CTInd indentation = properties.isSetInd() ? properties.getInd() : properties.addNewInd();
        indentation.setLeft(BigInteger.ZERO);
        if (indentation.isSetRight()) indentation.unsetRight();
        if (indentation.isSetHanging()) indentation.unsetHanging();
        indentation.setFirstLine(ORDER_FIRST_LINE);
    }

    private void clearParagraphMarkBold(CTPPr properties) {
        if (!properties.isSetRPr()) return;
        CTParaRPr runProperties = properties.getRPr();
        while (runProperties.sizeOfBArray() > 0) {
            runProperties.removeB(0);
        }
        while (runProperties.sizeOfBCsArray() > 0) {
            runProperties.removeBCs(0);
        }
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
            boolean markerColor = wordElement && "color".equals(child.getLocalName())
                    && child instanceof Element element
                    && TEMPLATE_MARKER_COLOR.equalsIgnoreCase(
                    element.getAttributeNS(WORD_NAMESPACE, "val"));
            if (highlight || directRunShading || markerColor) {
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

    private void clearParagraph(XWPFParagraph paragraph) {
        clearRuns(paragraph);
        CTPPr properties = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : null;
        if (properties == null) return;
        if (properties.isSetNumPr()) properties.unsetNumPr();
        if (properties.isSetTabs()) properties.unsetTabs();
    }

    private void styleRun(XWPFRun run, boolean bold, int size) {
        run.setFontFamily("Times New Roman");
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor("000000");
    }

    private void replaceCompanionsParagraph(XWPFParagraph paragraph, DocumentData data) {
        clearRuns(paragraph);
        List<PersonData> companions = allCompanions(data);
        for (int i = 0; i < companions.size(); i++) {
            PersonData person = companions.get(i);
            String phone = formatPhone(person.phone());
            XWPFRun name = paragraph.createRun();
            styleRun(name, false, 14);
            name.setText(text(person.fullName()) + (phone.isBlank() ? "" : " "));
            XWPFRun lineEnd = name;
            if (!phone.isBlank()) {
                XWPFRun phoneRun = paragraph.createRun();
                phoneRun.setFontFamily("Inter");
                phoneRun.setFontSize(11.5);
                phoneRun.setBold(false);
                phoneRun.setColor("000000");
                phoneRun.setUnderline(UnderlinePatterns.SINGLE);
                phoneRun.setText(phone);
                lineEnd = phoneRun;
            }
            if (i < companions.size() - 1) {
                lineEnd.addBreak();
            }
        }
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBetween(1.5, LineSpacingRule.AT_LEAST);
    }

    private String formatPhone(String value) {
        String original = text(value);
        String digits = original.replaceAll("\\D", "");
        if (digits.length() == 11 && (digits.startsWith("7") || digits.startsWith("8"))) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10) {
            return "+7 (" + digits.substring(0, 3) + ") " + digits.substring(3, 6)
                    + "-" + digits.substring(6, 8) + "-" + digits.substring(8);
        }
        return original;
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

    private String dativeInitials(PersonData person) {
        return person == null ? "" : casedSurnameInitials(person.dativeOrName(), person);
    }

    private String accusativeInitials(PersonData person) {
        return person == null ? "" : casedSurnameInitials(person.accusativeOrName(), person);
    }

    private String casedSurnameInitials(String casedName, PersonData person) {
        String value = text(casedName);
        if (value.isBlank()) return "";
        String surname = value.split("\\s+", 2)[0];
        String compact = text(person.initials());
        int separator = compact.indexOf(' ');
        String initials = separator >= 0 ? compact.substring(separator + 1).trim() : "";
        if (initials.isBlank()) {
            String[] parts = text(person.fullName()).split("\\s+");
            StringBuilder generated = new StringBuilder();
            for (int i = 1; i < Math.min(parts.length, 3); i++) {
                if (!parts[i].isBlank()) generated.append(parts[i].charAt(0)).append('.');
            }
            initials = generated.toString();
        }
        return initials.isBlank() ? surname : surname + " " + initials;
    }

    private String surnameInitials(String fullName) {
        String value = text(fullName);
        if (value.isBlank()) return "";
        String[] parts = value.split("\\s+");
        if (parts.length < 2) return value;
        StringBuilder result = new StringBuilder(parts[0]).append(' ');
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            if (parts[i].matches("[А-ЯA-ZЁ]\\.(?:[А-ЯA-ZЁ]\\.)?")) {
                result.append(parts[i]);
            } else if (!parts[i].isBlank()) {
                result.append(parts[i].charAt(0)).append('.');
            }
        }
        return result.toString().trim();
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

    public record ParticipantData(String fullName, String childPhone,
                                  String representativeName, String representativePhone) {
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
