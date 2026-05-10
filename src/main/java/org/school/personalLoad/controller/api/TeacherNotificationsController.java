package org.school.personalLoad.controller.api;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.TeacherNotificationRecord;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherNotificationRecordRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;

import org.apache.xmlbeans.XmlCursor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/teachers-notification")
@RequiredArgsConstructor
public class TeacherNotificationsController {
    private static final String DEFAULT_TEMPLATE_DOCX = "templates/teacher-notifications/notification-demo.docx";
    private static final String PLACEHOLDER_TEACHER = "${TEACHER}";
    private static final String PLACEHOLDER_DATE = "${DATE}";
    private static final String PLACEHOLDER_YEAR = "${ACADEMIC_YEAR}";
    private static final String PLACEHOLDER_TOTAL_LOAD = "${TOTAL_LOAD}";
    private static final String PLACEHOLDER_DATE_CITY_LINE = "${DATE_CITY_LINE}";
    private static final String PLACEHOLDER_LOAD_TABLE = "${LOAD_TABLE}";

    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TeacherNotificationRecordRepository recordRepository;

    @GetMapping
    public List<Row> list(@RequestParam String academicYear, @RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.of(2026, 9, 1) : LocalDate.parse(date);
        Map<String, List<ManualLoadEntry>> byTeacher = activeRows(academicYear, d).stream().collect(Collectors.groupingBy(ManualLoadEntry::getFioTeacher));
        Map<String, TeacherNotificationRecord> records = recordRepository.findAllByAcademicYear(academicYear).stream().collect(Collectors.toMap(TeacherNotificationRecord::getFioTeacher, r -> r, (a, b) -> a));
        return byTeacher.keySet().stream().sorted().map(fio -> {
            String hash = hashOf(byTeacher.get(fio));
            TeacherNotificationRecord rec = records.get(fio);
            boolean generated = rec != null;
            boolean changed = generated && !Objects.equals(rec.getDataHash(), hash);
            return new Row(fio, generated, changed);
        }).collect(Collectors.toList());
    }

    @PostMapping("/download/{fio}")
    public ResponseEntity<byte[]> downloadOne(@PathVariable String fio, @RequestParam String academicYear, @RequestParam String date, HttpServletRequest request) throws Exception {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        LocalDate d = LocalDate.parse(date);
        byte[] doc = generateDoc(fio, academicYear, d);
        upsert(fio, academicYear, d, user.getUsername());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fio + ".docx").contentType(MediaType.APPLICATION_OCTET_STREAM).body(doc);
    }

    @PostMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@RequestParam String academicYear, @RequestParam String date, HttpServletRequest request) throws Exception {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        LocalDate d = LocalDate.parse(date);
        Map<String, List<ManualLoadEntry>> byTeacher = activeRows(academicYear, d).stream().collect(Collectors.groupingBy(ManualLoadEntry::getFioTeacher));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String fio : byTeacher.keySet()) {
                byte[] doc = generateDoc(fio, academicYear, d);
                upsert(fio, academicYear, d, user.getUsername());
                zos.putNextEntry(new ZipEntry((fio + "_" + academicYear + ".docx").replace(' ', '_')));
                zos.write(doc);
                zos.closeEntry();
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=notifications.zip").contentType(MediaType.APPLICATION_OCTET_STREAM).body(bos.toByteArray());
    }

    private byte[] generateDoc(String fio, String year, LocalDate d) throws Exception {
        List<ManualLoadEntry> rows = activeRows(year, d).stream()
                .filter(r -> fio.equalsIgnoreCase(r.getFioTeacher()))
                .sorted(Comparator.comparing(ManualLoadEntry::getClassName))
                .collect(Collectors.toList());
        String schoolCode = System.getenv().getOrDefault("SCHOOL_CODE", "demo").toLowerCase(Locale.ROOT);
        String templatePath = String.format("templates/teacher-notifications/notification-%s.docx", schoolCode);

        try (InputStream in = templateOrFallback(templatePath);
             XWPFDocument doc = in != null ? new XWPFDocument(in) : new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            replacePlaceholders(doc, fio, d, year, rows);
            insertLoadTable(doc, fio, rows);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private InputStream templateOrFallback(String templatePath) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(templatePath);
        if (in != null) return in;
        return getClass().getClassLoader().getResourceAsStream(DEFAULT_TEMPLATE_DOCX);
    }

    private void replacePlaceholders(XWPFDocument doc, String fio, LocalDate date, String year, List<ManualLoadEntry> rows) {
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            replaceInParagraph(doc, paragraph, fio, date, year, rows);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceInParagraph(doc, paragraph, fio, date, year, rows);
                    }
                }
            }
        }
    }

    private void replaceInParagraph(XWPFDocument doc, XWPFParagraph p, String fio, LocalDate date, String year, List<ManualLoadEntry> rows) {
        String text = p.getText();
        if (text == null || text.isBlank()) return;
        int totalLoad = rows.stream().map(ManualLoadEntry::getLoad).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        String dateRu = String.format("%02d.%02d.%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String replaced = text.replace(PLACEHOLDER_TEACHER, fio)
                .replace(PLACEHOLDER_DATE, String.valueOf(date))
                .replace(PLACEHOLDER_YEAR, year)
                .replace(PLACEHOLDER_TOTAL_LOAD, String.valueOf(totalLoad));

        if (replaced.contains(PLACEHOLDER_DATE_CITY_LINE)) {
            while (p.getRuns().size() > 0) p.removeRun(0);
            CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
            tabs.addNewTab().setVal(STTabJc.RIGHT);
            tabs.getTabArray(tabs.sizeOfTabArray() - 1).setPos(BigInteger.valueOf(9000));
            p.setAlignment(ParagraphAlignment.BOTH);
            XWPFRun run = p.createRun();
            run.setFontFamily("Times New Roman");
            run.setFontSize(12);
            run.setText(dateRu);
            run.addTab();
            run.setText("г. Москва");
            return;
        }

        if (!replaced.equals(text)) {
            while (p.getRuns().size() > 0) p.removeRun(0);
            XWPFRun run = p.createRun();
            run.setText(replaced);
            run.setFontFamily("Times New Roman");
            run.setFontSize(12);
        }
    }

    private void insertLoadTable(XWPFDocument doc, String fio, List<ManualLoadEntry> rows) {
        XWPFParagraph markerParagraph = null;
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            if ((paragraph.getText() != null) && paragraph.getText().contains(PLACEHOLDER_LOAD_TABLE)) {
                markerParagraph = paragraph;
                break;
            }
        }

        int totalLoad = rows.stream().map(ManualLoadEntry::getLoad).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();

        if (markerParagraph == null) {
            markerParagraph = doc.createParagraph();
        }
        while (markerParagraph.getRuns().size() > 0) markerParagraph.removeRun(0);

        XmlCursor cursor = markerParagraph.getCTP().newCursor();
        XWPFTable table = doc.insertNewTbl(cursor);
        styleRow(table.createRow(), Arrays.asList("ФИО", "Класс", "Часы"), true);
        table.removeRow(0);
        for (ManualLoadEntry row : rows) {
            styleRow(table.createRow(), Arrays.asList(fio, row.getClassName(), String.valueOf(row.getLoad())), false);
        }
        styleRow(table.createRow(), Arrays.asList("Итого", "", String.valueOf(totalLoad)), true);
        table.setTableAlignment(TableRowAlign.CENTER);
    }

    private void styleRow(XWPFTableRow row, List<String> values, boolean header) {
        for (int i = 0; i < values.size(); i++) {
            XWPFTableCell cell = row.getCell(i);
            CTTc cttc = cell.getCTTc();
            CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();
            CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
            vAlign.setVal(STVerticalJc.CENTER);
            cell.removeParagraph(0);
            XWPFParagraph p = cell.addParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = p.createRun();
            run.setFontFamily("Times New Roman");
            run.setFontSize(12);
            run.setBold(header);
            run.setText(values.get(i));
        }
    }

    private List<ManualLoadEntry> activeRows(String year, LocalDate d) {
        return manualLoadEntryRepository.findAllByAcademicYear(year).stream().filter(r -> r.getLoad() != null && r.getLoad() > 0)
                .filter(r -> r.getLoadFromDate() == null || !r.getLoadFromDate().isAfter(d))
                .filter(r -> r.getLoadToDate() == null || !r.getLoadToDate().isBefore(d)).collect(Collectors.toList());
    }

    private void upsert(String fio, String year, LocalDate d, String user) {
        List<ManualLoadEntry> rows = activeRows(year, d).stream().filter(r -> fio.equalsIgnoreCase(r.getFioTeacher())).collect(Collectors.toList());
        String hash = hashOf(rows);
        TeacherNotificationRecord rec = recordRepository.findByAcademicYearAndFioTeacherIgnoreCase(year, fio).orElseGet(TeacherNotificationRecord::new);
        rec.setAcademicYear(year);
        rec.setFioTeacher(fio);
        rec.setNotificationDate(d);
        rec.setDataHash(hash);
        rec.setGeneratedBy(user);
        rec.setGeneratedAt(LocalDateTime.now());
        rec.setDownloadedAt(LocalDateTime.now());
        rec.setDownloadedBy(user);
        recordRepository.save(rec);
    }

    private String hashOf(List<ManualLoadEntry> rows) {
        return Integer.toHexString(rows.stream().map(r -> (r.getSubjectName() + "|" + r.getClassName() + "|" + r.getLoad() + "|" + r.getLoadFromDate() + "|" + r.getLoadToDate())).sorted().collect(Collectors.joining(";")).hashCode());
    }

    @Data
    @Builder
    private static class Row {
        String fio;
        boolean generated;
        boolean changed;
    }
}
