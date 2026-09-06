package org.school.personalLoad.service.impl;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.school.personalLoad.model.AcademicLoadOrderType;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.StudyPeriod;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AcademicLoadOrderDocumentService {

    private static final String SCHOOL_7_CREST = "/templates/pedagogical-councils/school-7-crest.jpg";
    private static final String SCHOOL_1811_HEADER = "/templates/pedagogical-councils/school-1811-header.png";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record CurriculumPlanRow(int parallel,
                                    String building,
                                    String className,
                                    CurriculumPart curriculumPart,
                                    String subject,
                                    StudyPeriod studyPeriod,
                                    BigDecimal hours) {
    }

    public record LoadRow(String teacher, String subject, String classes, String hours, String period) {
    }

    public record DocumentData(
            AcademicLoadOrderType type,
            String schoolCode,
            String schoolName,
            String academicYear,
            String orderNumber,
            LocalDate orderDate,
            String protocolNumber,
            LocalDate protocolDate,
            LocalDate effectiveDate,
            String signerName,
            String signerPosition,
            String controlOfficerName,
            String basisText,
            List<CurriculumPlanRow> curriculumPlans,
            List<LoadRow> loadRows
    ) {
    }

    public byte[] generate(DocumentData data) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configureDocument(document);
            appendLetterhead(document, data.schoolCode(), data.schoolName());
            appendOrderHeader(document, data);
            appendOrderBody(document, data);
            appendSignature(document, data.signerPosition(), data.signerName());
            if (data.type() == AcademicLoadOrderType.CURRICULUM_APPROVAL) {
                startLandscapeAnnex(document);
                appendCurriculumAnnex(document, data);
            } else {
                document.createParagraph().createRun().addBreak(BreakType.PAGE);
                appendLoadAnnex(document, data);
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать Word-приказ", e);
        }
    }

    private void configureDocument(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(850));
        margins.setBottom(BigInteger.valueOf(850));
        margins.setLeft(BigInteger.valueOf(1417));
        margins.setRight(BigInteger.valueOf(1134));

        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph pageNumber = header.getParagraphs().isEmpty() ? header.createParagraph() : header.getParagraphs().get(0);
        pageNumber.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun begin = pageNumber.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = pageNumber.createRun();
        instruction.getCTR().addNewInstrText().setStringValue("PAGE");
        XWPFRun separate = pageNumber.createRun();
        separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        styledRun(pageNumber, "2", false, 10);
        XWPFRun end = pageNumber.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        document.createHeader(HeaderFooterType.FIRST);
    }

    private void startLandscapeAnnex(XWPFDocument document) {
        CTSectPr finalSection = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();

        XWPFParagraph sectionBreak = document.createParagraph();
        CTPPr paragraphProperties = sectionBreak.getCTP().isSetPPr()
                ? sectionBreak.getCTP().getPPr() : sectionBreak.getCTP().addNewPPr();
        CTSectPr portraitSection = paragraphProperties.addNewSectPr();
        portraitSection.set(finalSection);
        if (portraitSection.isSetType()) portraitSection.getType().setVal(STSectionMark.NEXT_PAGE);
        else portraitSection.addNewType().setVal(STSectionMark.NEXT_PAGE);

        CTPageSz size = finalSection.isSetPgSz() ? finalSection.getPgSz() : finalSection.addNewPgSz();
        size.setW(BigInteger.valueOf(16838));
        size.setH(BigInteger.valueOf(11906));
        size.setOrient(STPageOrientation.LANDSCAPE);
        CTPageMar margins = finalSection.isSetPgMar() ? finalSection.getPgMar() : finalSection.addNewPgMar();
        margins.setTop(BigInteger.valueOf(500));
        margins.setBottom(BigInteger.valueOf(500));
        margins.setLeft(BigInteger.valueOf(500));
        margins.setRight(BigInteger.valueOf(500));
        if (finalSection.isSetTitlePg()) finalSection.unsetTitlePg();
    }

    private void appendLetterhead(XWPFDocument document, String schoolCode, String schoolName) {
        if ("7".equalsIgnoreCase(schoolCode)) {
            XWPFParagraph crest = compact(document, ParagraphAlignment.CENTER);
            addPicture(crest.createRun(), SCHOOL_7_CREST, XWPFDocument.PICTURE_TYPE_JPEG,
                    "school-7-crest.jpg", 0.65, 0.90);
            letterheadLine(document, "ДЕПАРТАМЕНТ ОБРАЗОВАНИЯ И НАУКИ ГОРОДА МОСКВЫ", false, 11);
            letterheadLine(document, "ГОСУДАРСТВЕННОЕ БЮДЖЕТНОЕ ОБЩЕОБРАЗОВАТЕЛЬНОЕ УЧРЕЖДЕНИЕ", true, 10);
            letterheadLine(document, "ГОРОДА МОСКВЫ «ШКОЛА № 7»", true, 10);
            letterheadLine(document, "119331 г. Москва, улица Крупской, дом № 17", false, 9);
            letterheadLine(document, "Телефон: (499) 138-38-27    E-mail: 7@edu.mos.ru    http://sch7uz.mskobr.ru", false, 9);
            XWPFParagraph last = letterheadLine(document,
                    "ОКПО 40120398    ОГРН 1027739844384    ИНН/КПП 7736050780/773601001", false, 9);
            CTPPr ppr = last.getCTP().isSetPPr() ? last.getCTP().getPPr() : last.getCTP().addNewPPr();
            CTPBdr borders = ppr.isSetPBdr() ? ppr.getPBdr() : ppr.addNewPBdr();
            CTBorder bottom = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
            bottom.setVal(STBorder.DOUBLE);
            bottom.setSz(BigInteger.valueOf(8));
            bottom.setSpace(BigInteger.valueOf(4));
            bottom.setColor("000000");
            last.setSpacingAfter(100);
            return;
        }
        if ("1811".equalsIgnoreCase(schoolCode)) {
            XWPFParagraph header = compact(document, ParagraphAlignment.CENTER);
            header.setSpacingAfter(120);
            addPicture(header.createRun(), SCHOOL_1811_HEADER, XWPFDocument.PICTURE_TYPE_PNG,
                    "school-1811-header.png", 6.45, 1.65);
            return;
        }
        centered(document, schoolName, true, 12);
    }

    private void appendOrderHeader(XWPFDocument document, DocumentData data) {
        centered(document, "ПРИКАЗ", true, 14).setSpacingAfter(90);
        XWPFTable requisites = document.createTable(1, 2);
        requisites.setWidth("100%");
        removeBorders(requisites);
        setCell(requisites.getRow(0).getCell(0), formatDate(data.orderDate()) + " г.", false, 11, ParagraphAlignment.LEFT);
        setCell(requisites.getRow(0).getCell(1), "№ " + data.orderNumber(), false, 11, ParagraphAlignment.RIGHT);
        centered(document, title(data), true, 12).setSpacingBefore(120);
        document.createParagraph();
    }

    private void appendOrderBody(XWPFDocument document, DocumentData data) {
        String basis = trim(data.basisText());
        if (basis.isBlank()) basis = defaultBasis(data);
        body(document, basis);
        centered(document, "ПРИКАЗЫВАЮ:", true, 11).setSpacingBefore(100);
        if (data.type() == AcademicLoadOrderType.CURRICULUM_APPROVAL) {
            numbered(document, 1, "Утвердить учебные планы на " + data.academicYear()
                    + " учебный год согласно приложению 1 к настоящему приказу.");
            numbered(document, 2, "Заместителям директора и руководителям структурных подразделений обеспечить реализацию утверждённых учебных планов.");
            numbered(document, 3, "Педагогическим работникам руководствоваться утверждёнными учебными планами при реализации образовательных программ.");
            numbered(document, 4, controlText(data.controlOfficerName()));
        } else {
            String effective = data.effectiveDate() == null ? "в " + data.academicYear() + " учебном году"
                    : "с " + formatDate(data.effectiveDate()) + " г.";
            numbered(document, 1, "Утвердить учебную нагрузку педагогических работников " + effective
                    + " согласно приложению 1 к настоящему приказу.");
            numbered(document, 2, "Руководителям структурных подразделений ознакомить педагогических работников с утверждённой учебной нагрузкой.");
            numbered(document, 3, "Изменение утверждённой учебной нагрузки оформлять отдельным распорядительным документом.");
            numbered(document, 4, controlText(data.controlOfficerName()));
        }
    }

    private void appendSignature(XWPFDocument document, String position, String name) {
        document.createParagraph();
        XWPFTable signature = document.createTable(1, 3);
        signature.setWidth("100%");
        removeBorders(signature);
        setCell(signature.getRow(0).getCell(0), position, false, 11, ParagraphAlignment.LEFT);
        setCell(signature.getRow(0).getCell(1), "____________", false, 11, ParagraphAlignment.CENTER);
        setCell(signature.getRow(0).getCell(2), shortName(name), false, 11, ParagraphAlignment.RIGHT);
    }

    private void appendCurriculumAnnex(XWPFDocument document, DocumentData data) {
        List<CurriculumPlanRow> allRows = Optional.ofNullable(data.curriculumPlans()).orElseGet(List::of);
        for (int parallel = 1; parallel <= 11; parallel++) {
            appendCurriculumParallelPage(document, data, parallel, allRows);
        }
    }

    private void appendCurriculumParallelPage(XWPFDocument document,
                                              DocumentData data,
                                              int parallel,
                                              List<CurriculumPlanRow> allRows) {
        List<CurriculumPlanRow> rows = allRows.stream()
                .filter(row -> row.parallel() == parallel)
                .filter(row -> !trim(row.subject()).isBlank())
                .filter(row -> Optional.ofNullable(row.hours()).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                .toList();

        curriculumAnnexCaption(document, data, parallel);
        SortedSet<String> buildings = rows.stream()
                .map(CurriculumPlanRow::building)
                .map(this::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        String subtitle = buildings.isEmpty() ? "" : "\nКорпуса: " + String.join(", ", buildings);
        XWPFParagraph title = centered(document,
                "Учебный план " + parallel + " параллели на " + data.academicYear() + " учебный год" + subtitle,
                true, 10);
        title.setSpacingAfter(35);

        if (rows.isEmpty()) {
            centered(document, "Учебный план для параллели не заполнен", false, 9);
            return;
        }

        List<CurriculumClass> classes = curriculumClasses(rows);
        List<Map.Entry<CurriculumSubjectKey, List<CurriculumPlanRow>>> subjects = curriculumSubjects(rows);
        int fontSize = curriculumFontSize(subjects);
        int cellMargin = fontSize <= 6 ? 8 : fontSize == 7 ? 14 : 22;

        XWPFTable table = document.createTable(1, 3 + classes.size());
        table.setWidth("100%");
        List<String> headers = new ArrayList<>(List.of("№", "Часть", "Предмет"));
        headers.addAll(curriculumClassLabels(classes));
        curriculumHeaderRow(table, headers, Math.max(7, fontSize));

        int index = 1;
        for (Map.Entry<CurriculumSubjectKey, List<CurriculumPlanRow>> subject : subjects) {
            XWPFTableRow target = table.createRow();
            preventRowSplit(target);
            setCompactCell(target.getCell(0), String.valueOf(index++), false, fontSize, ParagraphAlignment.CENTER);
            setCompactCell(target.getCell(1), curriculumPartLabel(subject.getKey().curriculumPart()), false,
                    fontSize, ParagraphAlignment.LEFT);
            setCompactCell(target.getCell(2), subject.getValue().get(0).subject(), false,
                    fontSize, ParagraphAlignment.LEFT);
            for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
                setCompactCell(target.getCell(classIndex + 3),
                        curriculumHours(subject.getValue(), classes.get(classIndex)), false,
                        fontSize, ParagraphAlignment.CENTER);
            }
        }

        setFixedCurriculumWidths(table, classes.size());
        styleTable(table, cellMargin);
    }

    private void curriculumAnnexCaption(XWPFDocument document, DocumentData data, int parallel) {
        XWPFParagraph caption = compact(document, ParagraphAlignment.RIGHT);
        if (parallel > 1) caption.setPageBreak(true);
        styledRun(caption, "Приложение 1 к приказу от " + formatDate(data.orderDate())
                + " г. № " + data.orderNumber() + "\nЛист " + parallel + " из 11", false, 8);
        caption.setSpacingAfter(20);
    }

    private List<CurriculumClass> curriculumClasses(List<CurriculumPlanRow> rows) {
        Map<String, CurriculumClass> values = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing((CurriculumPlanRow row) -> trim(row.className()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> trim(row.building()), String.CASE_INSENSITIVE_ORDER))
                .forEach(row -> values.putIfAbsent(curriculumClassKey(row.building(), row.className()),
                        new CurriculumClass(curriculumClassKey(row.building(), row.className()),
                                trim(row.building()), trim(row.className()))));
        return List.copyOf(values.values());
    }

    private List<String> curriculumClassLabels(List<CurriculumClass> classes) {
        Map<String, Long> repeatedNames = classes.stream().collect(Collectors.groupingBy(
                value -> value.className().toLowerCase(Locale.ROOT), Collectors.counting()));
        return classes.stream()
                .map(value -> repeatedNames.get(value.className().toLowerCase(Locale.ROOT)) > 1
                        ? value.className() + "\n" + value.building() : value.className())
                .toList();
    }

    private List<Map.Entry<CurriculumSubjectKey, List<CurriculumPlanRow>>> curriculumSubjects(
            List<CurriculumPlanRow> rows) {
        Map<CurriculumSubjectKey, List<CurriculumPlanRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> new CurriculumSubjectKey(
                                Optional.ofNullable(row.curriculumPart()).orElse(CurriculumPart.CORE),
                                trim(row.subject()).toLowerCase(Locale.ROOT)),
                        LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<CurriculumSubjectKey, List<CurriculumPlanRow>> entry)
                                -> entry.getKey().curriculumPart().ordinal())
                        .thenComparing(entry -> entry.getKey().normalizedSubject(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int curriculumFontSize(List<Map.Entry<CurriculumSubjectKey, List<CurriculumPlanRow>>> subjects) {
        int estimatedLines = subjects.stream()
                .mapToInt(entry -> Math.max(1, (trim(entry.getValue().get(0).subject()).length() + 43) / 44))
                .sum();
        if (estimatedLines > 48 || subjects.size() > 42) return 6;
        if (estimatedLines > 38 || subjects.size() > 32) return 7;
        return 8;
    }

    private String curriculumHours(List<CurriculumPlanRow> rows, CurriculumClass targetClass) {
        Map<StudyPeriod, BigDecimal> byPeriod = new EnumMap<>(StudyPeriod.class);
        rows.stream()
                .filter(row -> curriculumClassKey(row.building(), row.className()).equals(targetClass.key()))
                .forEach(row -> byPeriod.merge(
                        Optional.ofNullable(row.studyPeriod()).orElse(StudyPeriod.YEAR),
                        Optional.ofNullable(row.hours()).orElse(BigDecimal.ZERO),
                        BigDecimal::max));
        BigDecimal year = byPeriod.getOrDefault(StudyPeriod.YEAR, BigDecimal.ZERO);
        BigDecimal firstHalf = byPeriod.getOrDefault(StudyPeriod.H1, BigDecimal.ZERO);
        BigDecimal secondHalf = byPeriod.getOrDefault(StudyPeriod.H2, BigDecimal.ZERO);
        if (year.compareTo(BigDecimal.ZERO) > 0
                && firstHalf.compareTo(BigDecimal.ZERO) == 0
                && secondHalf.compareTo(BigDecimal.ZERO) == 0) {
            return formatHours(year);
        }
        firstHalf = firstHalf.add(year);
        secondHalf = secondHalf.add(year);
        if (firstHalf.compareTo(BigDecimal.ZERO) == 0 && secondHalf.compareTo(BigDecimal.ZERO) == 0) return "";
        return formatHours(firstHalf) + "/" + formatHours(secondHalf);
    }

    private String curriculumPartLabel(CurriculumPart part) {
        return switch (Optional.ofNullable(part).orElse(CurriculumPart.CORE)) {
            case CORE -> "Основная";
            case FORMABLE -> "Формируемая";
            case EXTRACURRICULAR -> "Внеурочная";
            case CORRECTIONAL -> "Коррекционная";
        };
    }

    private String curriculumClassKey(String building, String className) {
        return trim(building).toLowerCase(Locale.ROOT) + "|" + trim(className).toLowerCase(Locale.ROOT);
    }

    private void appendLoadAnnex(XWPFDocument document, DocumentData data) {
        annexCaption(document, data);
        centered(document, "Учебная нагрузка педагогических работников", true, 12);
        XWPFTable table = document.createTable(1, 6);
        table.setWidth("100%");
        headerRow(table, List.of("№", "ФИО", "Предмет", "Классы и группы", "Часы в неделю", "Период"));
        int index = 1;
        for (LoadRow row : data.loadRows()) {
            XWPFTableRow target = table.createRow();
            setCell(target.getCell(0), String.valueOf(index++), false, 8, ParagraphAlignment.CENTER);
            setCell(target.getCell(1), row.teacher(), false, 8, ParagraphAlignment.LEFT);
            setCell(target.getCell(2), row.subject(), false, 8, ParagraphAlignment.LEFT);
            setCell(target.getCell(3), row.classes(), false, 8, ParagraphAlignment.LEFT);
            setCell(target.getCell(4), row.hours(), false, 8, ParagraphAlignment.CENTER);
            setCell(target.getCell(5), row.period(), false, 8, ParagraphAlignment.CENTER);
        }
        if (data.loadRows().isEmpty()) {
            XWPFTableRow empty = table.createRow();
            setCell(empty.getCell(0), "—", false, 9, ParagraphAlignment.CENTER);
            setCell(empty.getCell(1), "Учебная нагрузка в системе не распределена", false, 9, ParagraphAlignment.LEFT);
            mergeCells(empty, 1, 5);
        }
        setTableWidths(table, List.of(400, 2100, 1550, 2300, 1150, 1500));
        styleTable(table);
    }

    private void annexCaption(XWPFDocument document, DocumentData data) {
        XWPFParagraph caption = compact(document, ParagraphAlignment.RIGHT);
        styledRun(caption, "Приложение 1\nк приказу от " + formatDate(data.orderDate())
                + " г. № " + data.orderNumber(), false, 10);
        caption.setSpacingAfter(160);
    }

    private String title(DocumentData data) {
        return data.type() == AcademicLoadOrderType.CURRICULUM_APPROVAL
                ? "Об утверждении учебных планов\nна " + data.academicYear() + " учебный год"
                : "Об утверждении учебной нагрузки\nна " + data.academicYear() + " учебный год";
    }

    private String defaultBasis(DocumentData data) {
        if (data.type() == AcademicLoadOrderType.CURRICULUM_APPROVAL) {
            String protocol = trim(data.protocolNumber()).isBlank() ? ""
                    : ", на основании решения педагогического совета"
                    + (data.protocolDate() == null ? "" : " от " + formatDate(data.protocolDate()) + " г.")
                    + " № " + data.protocolNumber();
            return "В целях организации образовательного процесса, в соответствии с Федеральным законом от 29.12.2012 № 273-ФЗ «Об образовании в Российской Федерации», федеральными государственными образовательными стандартами и федеральными образовательными программами"
                    + protocol + ".";
        }
        return "В целях организации образовательного процесса, на основании утверждённых учебных планов и с учётом распределения педагогической нагрузки на "
                + data.academicYear() + " учебный год.";
    }

    private String controlText(String officer) {
        String name = trim(officer);
        return name.isBlank() ? "Контроль за исполнением настоящего приказа оставляю за собой."
                : "Контроль за исполнением настоящего приказа возложить на " + name + ".";
    }

    private void numbered(XWPFDocument document, int number, String text) {
        XWPFParagraph paragraph = body(document, number + ". " + text);
        paragraph.setIndentationLeft(360);
        paragraph.setIndentationHanging(360);
    }

    private XWPFParagraph body(XWPFDocument document, String text) {
        XWPFParagraph paragraph = compact(document, ParagraphAlignment.BOTH);
        paragraph.setIndentationFirstLine(709);
        paragraph.setSpacingAfter(80);
        paragraph.setSpacingBetween(1.15);
        styledRun(paragraph, text, false, 11);
        return paragraph;
    }

    private XWPFParagraph centered(XWPFDocument document, String text, boolean bold, int size) {
        XWPFParagraph paragraph = compact(document, ParagraphAlignment.CENTER);
        styledRun(paragraph, text, bold, size);
        paragraph.setSpacingAfter(60);
        return paragraph;
    }

    private XWPFParagraph compact(XWPFDocument document, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        return paragraph;
    }

    private XWPFParagraph letterheadLine(XWPFDocument document, String text, boolean bold, int size) {
        XWPFParagraph paragraph = compact(document, ParagraphAlignment.CENTER);
        styledRun(paragraph, text, bold, size);
        return paragraph;
    }

    private XWPFRun styledRun(XWPFParagraph paragraph, String text, boolean bold, int size) {
        XWPFRun run = paragraph.createRun();
        String[] lines = Optional.ofNullable(text).orElse("").split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(size);
        return run;
    }

    private void addPicture(XWPFRun run, String resource, int type, String filename, double width, double height) {
        try (InputStream input = AcademicLoadOrderDocumentService.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Не найден школьный бланк: " + resource);
            run.addPicture(input, type, filename, Units.toEMU(width * 72), Units.toEMU(height * 72));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось добавить школьный бланк", e);
        }
    }

    private void headerRow(XWPFTable table, List<String> values) {
        XWPFTableRow row = table.getRow(0);
        row.getCtRow().addNewTrPr().addNewTblHeader().setVal(true);
        for (int i = 0; i < values.size(); i++) {
            setCell(row.getCell(i), values.get(i), true, 8, ParagraphAlignment.CENTER);
            CTShd shading = row.getCell(i).getCTTc().addNewTcPr().addNewShd();
            shading.setFill("D9EAF7");
        }
    }

    private void curriculumHeaderRow(XWPFTable table, List<String> values, int fontSize) {
        XWPFTableRow row = table.getRow(0);
        row.getCtRow().addNewTrPr().addNewTblHeader().setVal(true);
        preventRowSplit(row);
        for (int i = 0; i < values.size(); i++) {
            setCompactCell(row.getCell(i), values.get(i), true, fontSize, ParagraphAlignment.CENTER);
            CTTcPr properties = row.getCell(i).getCTTc().isSetTcPr()
                    ? row.getCell(i).getCTTc().getTcPr() : row.getCell(i).getCTTc().addNewTcPr();
            CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
            shading.setFill("D9EAF7");
        }
    }

    private void setCell(XWPFTableCell cell, String value, boolean bold, int size, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(30);
        paragraph.setSpacingAfter(30);
        styledRun(paragraph, Optional.ofNullable(value).orElse(""), bold, size);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private void setCompactCell(XWPFTableCell cell,
                                String value,
                                boolean bold,
                                int size,
                                ParagraphAlignment alignment) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(0.85);
        styledRun(paragraph, Optional.ofNullable(value).orElse(""), bold, size);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private void preventRowSplit(XWPFTableRow row) {
        CTTrPr properties = row.getCtRow().isSetTrPr() ? row.getCtRow().getTrPr() : row.getCtRow().addNewTrPr();
        properties.addNewCantSplit().setVal(true);
    }

    private void styleTable(XWPFTable table) {
        styleTable(table, 70);
    }

    private void styleTable(XWPFTable table, int cellMargin) {
        CTTblPr props = table.getCTTbl().getTblPr();
        CTTblBorders borders = props.isSetTblBorders() ? props.getTblBorders() : props.addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE);
            border.setColor("D9D9D9");
            border.setSz(BigInteger.valueOf(4));
        }
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
                setMargin(mar.addNewTop(), cellMargin);
                setMargin(mar.addNewBottom(), cellMargin);
                setMargin(mar.addNewLeft(), Math.max(12, cellMargin));
                setMargin(mar.addNewRight(), Math.max(12, cellMargin));
            }
        }
    }

    private void setMargin(CTTblWidth width, int value) {
        width.setW(BigInteger.valueOf(value));
        width.setType(STTblWidth.DXA);
    }

    private void setTableWidths(XWPFTable table, List<Integer> widths) {
        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < row.getTableCells().size() && i < widths.size(); i++) {
                row.getCell(i).setWidth(String.valueOf(widths.get(i)));
            }
        }
    }

    private void setFixedCurriculumWidths(XWPFTable table, int classCount) {
        int totalWidth = 15838;
        int numberWidth = 380;
        int partWidth = classCount >= 14 ? 1200 : 1550;
        int subjectWidth = classCount >= 14 ? 4100 : 5200;
        int remaining = totalWidth - numberWidth - partWidth - subjectWidth;
        int classWidth = Math.max(1, remaining / Math.max(1, classCount));

        List<Integer> widths = new ArrayList<>(List.of(numberWidth, partWidth, subjectWidth));
        for (int index = 0; index < classCount; index++) widths.add(classWidth);
        if (classCount > 0) {
            int used = widths.stream().mapToInt(Integer::intValue).sum();
            widths.set(widths.size() - 1, widths.get(widths.size() - 1) + (totalWidth - used));
        }
        setFixedTableWidths(table, widths);
    }

    private void setFixedTableWidths(XWPFTable table, List<Integer> widths) {
        int total = widths.stream().mapToInt(Integer::intValue).sum();
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblWidth tableWidth = properties.isSetTblW() ? properties.getTblW() : properties.addNewTblW();
        tableWidth.setType(STTblWidth.DXA);
        tableWidth.setW(BigInteger.valueOf(total));
        CTTblLayoutType layout = properties.isSetTblLayout()
                ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);

        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (Integer width : widths) grid.addNewGridCol().setW(BigInteger.valueOf(width));

        for (XWPFTableRow row : table.getRows()) {
            for (int index = 0; index < row.getTableCells().size() && index < widths.size(); index++) {
                XWPFTableCell cell = row.getCell(index);
                CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                        ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTblWidth cellWidth = cellProperties.isSetTcW()
                        ? cellProperties.getTcW() : cellProperties.addNewTcW();
                cellWidth.setType(STTblWidth.DXA);
                cellWidth.setW(BigInteger.valueOf(widths.get(index)));
            }
        }
    }

    private void removeBorders(XWPFTable table) {
        CTTblBorders borders = table.getCTTbl().getTblPr().addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.NIL);
        }
    }

    private void mergeCells(XWPFTableRow row, int from, int to) {
        CTTcPr first = row.getCell(from).getCTTc().isSetTcPr()
                ? row.getCell(from).getCTTc().getTcPr() : row.getCell(from).getCTTc().addNewTcPr();
        first.addNewGridSpan().setVal(BigInteger.valueOf(to - from + 1L));
        for (int index = to; index > from; index--) row.removeCell(index);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    private String formatHours(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO).stripTrailingZeros().toPlainString();
    }

    private String trim(String value) {
        return Optional.ofNullable(value).orElse("").trim();
    }

    private String shortName(String name) {
        String normalized = trim(name).replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        if (parts.length < 2) return normalized;
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length && i < 3; i++) {
            if (!parts[i].isBlank()) result.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return result.toString();
    }

    private record CurriculumClass(String key, String building, String className) {
    }

    private record CurriculumSubjectKey(CurriculumPart curriculumPart, String normalizedSubject) {
    }
}
