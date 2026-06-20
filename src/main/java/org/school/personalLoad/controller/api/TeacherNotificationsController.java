package org.school.personalLoad.controller.api;

import lombok.Builder;
import lombok.Data;
import org.school.personalLoad.config.SchoolCodeResolver;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.TeacherNotificationRecord;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherNotificationRecordRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
    private final TeacherDirectoryRepository teacherDirectoryRepository;

    @GetMapping
    public List<Row> list(@RequestParam String academicYear, @RequestParam(required = false) String loadDate) {
        LocalDate d = defaultLoadDate(academicYear, loadDate);
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
    public ResponseEntity<byte[]> downloadOne(@PathVariable String fio, @RequestParam String academicYear, @RequestParam String loadDate, @RequestParam String notificationDate, HttpServletRequest request) throws Exception {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        LocalDate targetLoadDate = defaultLoadDate(academicYear, loadDate);
        LocalDate generatedDate = LocalDate.parse(notificationDate);
        byte[] doc = generateDoc(fio, academicYear, targetLoadDate, generatedDate);
        upsert(fio, academicYear, targetLoadDate, user.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFileName(fio + ".docx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(doc);
    }

    @PostMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@RequestParam String academicYear, @RequestParam String loadDate, @RequestParam String notificationDate, HttpServletRequest request) throws Exception {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        LocalDate d = defaultLoadDate(academicYear, loadDate);
        LocalDate generatedDate = LocalDate.parse(notificationDate);
        Map<String, List<ManualLoadEntry>> byTeacher = activeRows(academicYear, d).stream().collect(Collectors.groupingBy(ManualLoadEntry::getFioTeacher));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String fio : byTeacher.keySet()) {
                byte[] doc = generateDoc(fio, academicYear, d, generatedDate);
                upsert(fio, academicYear, d, user.getUsername());
                zos.putNextEntry(new ZipEntry(("Уведомление_" + fio + ".docx").replace(' ', '_')));
                zos.write(doc);
                zos.closeEntry();
            }
        }
        String zipName = "Уведомления на " + d;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFileName(zipName + ".zip"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }

    private String contentDispositionFileName(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }

    private byte[] generateDoc(String fio, String year, LocalDate loadDate, LocalDate notificationDate) throws Exception {
        List<ManualLoadEntry> rows = activeRows(year, loadDate).stream()
                .filter(r -> fio.equalsIgnoreCase(r.getFioTeacher()))
                .sorted(Comparator.comparing(ManualLoadEntry::getClassName))
                .collect(Collectors.toList());
        String schoolCode = SchoolCodeResolver.resolve();
        String templatePath = String.format("templates/teacher-notifications/notification-%s.docx", schoolCode);

        try (InputStream in = templateOrFallback(templatePath);
             XWPFDocument doc = in != null ? new XWPFDocument(in) : new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            replacePlaceholders(doc, teacherNameForNotification(fio), notificationDate, year, rows);
            insertLoadTable(doc, rows);
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
        text = text.replace("\\${", "${");
        String totalLoad = formatNotificationTotalLoad(rows);
        String dateRu = String.format("%02d.%02d.%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String replaced = stripTemplateHourWordAfterTotalLoad(text)
                .replace(PLACEHOLDER_TEACHER, fio)
                .replace(PLACEHOLDER_DATE, String.valueOf(date))
                .replace(PLACEHOLDER_YEAR, year)
                .replace(PLACEHOLDER_TOTAL_LOAD, totalLoad);

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

    String teacherNameForNotification(String fio) {
        return teacherDirectoryRepository.findByFioTeacherIgnoreCase(fio)
                .map(TeacherDirectoryEntry::getFioTeacherDative)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(fio);
    }

    String stripTemplateHourWordAfterTotalLoad(String text) {
        return text.replace(PLACEHOLDER_TOTAL_LOAD + " часов", PLACEHOLDER_TOTAL_LOAD)
                .replace(PLACEHOLDER_TOTAL_LOAD + " часа", PLACEHOLDER_TOTAL_LOAD)
                .replace(PLACEHOLDER_TOTAL_LOAD + " час", PLACEHOLDER_TOTAL_LOAD);
    }

    String formatNotificationTotalLoad(List<ManualLoadEntry> rows) {
        int totalH1 = 0;
        int totalH2 = 0;
        for (ManualLoadEntry row : rows) {
            int hours = notificationLoadHours(row);
            if (row.getStudyPeriod() == org.school.personalLoad.model.StudyPeriod.H1) {
                totalH1 += hours;
            } else if (row.getStudyPeriod() == org.school.personalLoad.model.StudyPeriod.H2) {
                totalH2 += hours;
            } else {
                totalH1 += hours;
                totalH2 += hours;
            }
        }
        if (totalH1 == totalH2) {
            return totalH1 + " " + hourWord(totalH1);
        }
        return "в 1 полугодие: " + totalH1 + " " + hourWord(totalH1)
                + " и во 2 полугодие: " + totalH2 + " " + hourWord(totalH2);
    }

    String hourWord(int hours) {
        int abs = Math.abs(hours);
        int lastTwo = abs % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "часов";
        }
        return switch (abs % 10) {
            case 1 -> "час";
            case 2, 3, 4 -> "часа";
            default -> "часов";
        };
    }

    private void insertLoadTable(XWPFDocument doc, List<ManualLoadEntry> rows) {
        XWPFParagraph markerParagraph = null;
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            if ((paragraph.getText() != null) && paragraph.getText().replace("\\${", "${").contains(PLACEHOLDER_LOAD_TABLE)) {
                markerParagraph = paragraph;
                break;
            }
        }

        List<SubjectLoad> subjectLoads = aggregateSubjectLoads(rows);

        if (markerParagraph == null) {
            markerParagraph = doc.createParagraph();
        }
        while (markerParagraph.getRuns().size() > 0) markerParagraph.removeRun(0);

        XmlCursor cursor = markerParagraph.getCTP().newCursor();
        XWPFTable table = doc.insertNewTbl(cursor);
        styleRow(table.createRow(), Arrays.asList("Предмет", "Класс", "Часы"), true);
        table.removeRow(0);
        for (SubjectLoad row : subjectLoads) {
            styleRow(table.createRow(), Arrays.asList(row.subjectName, row.className, row.hoursDisplay), false);
        }
        styleRow(table.createRow(), Arrays.asList("Итого", "", totalDisplay(subjectLoads)), true);
        table.setTableAlignment(TableRowAlign.CENTER);
    }

    private List<SubjectLoad> aggregateSubjectLoads(List<ManualLoadEntry> rows) {
        Map<String, SubjectLoad> map = new LinkedHashMap<>();
        for (ManualLoadEntry row : rows) {
            String key = row.getSubjectName() + "|" + row.getClassName();
            SubjectLoad sl = map.computeIfAbsent(key, k -> new SubjectLoad(row.getSubjectName(), row.getClassName()));
            int hours = notificationLoadHours(row);
            if (row.getStudyPeriod() == org.school.personalLoad.model.StudyPeriod.H1) sl.h1 += hours;
            else if (row.getStudyPeriod() == org.school.personalLoad.model.StudyPeriod.H2) sl.h2 += hours;
            else { sl.h1 += hours; sl.h2 += hours; }
        }
        map.values().forEach(SubjectLoad::finalizeDisplay);
        return new ArrayList<>(map.values());
    }

    private String totalDisplay(List<SubjectLoad> rows) {
        int totalH1 = rows.stream().mapToInt(r -> r.h1).sum();
        int totalH2 = rows.stream().mapToInt(r -> r.h2).sum();
        if (totalH1 == totalH2) return String.valueOf(totalH1);
        return totalH1 + "/" + totalH2;
    }

    private int notificationLoadHours(ManualLoadEntry row) {
        if (row == null) {
            return 0;
        }
        return Optional.ofNullable(row.getGroupLoad())
                .orElseGet(() -> Optional.ofNullable(row.getLoad()).orElse(0));
    }

    private LocalDate defaultLoadDate(String academicYear, String loadDate) {
        if (loadDate != null && !loadDate.isBlank()) return LocalDate.parse(loadDate);
        String[] parts = String.valueOf(academicYear).split("/");
        int year = Integer.parseInt(parts[0]);
        return LocalDate.of(year, 9, 1);
    }

    private static class SubjectLoad {
        String subjectName;
        String className;
        int h1;
        int h2;
        int year;
        String hoursDisplay;

        SubjectLoad(String subjectName, String className) { this.subjectName = subjectName; this.className = className; }
        void finalizeDisplay() {
            if (h1 == 0 && h2 == 0) {
                hoursDisplay = String.valueOf(year);
            } else if (h1 == h2) {
                hoursDisplay = String.valueOf(h1);
            } else {
                hoursDisplay = h1 + "/" + h2;
            }
        }
    }

    private void styleRow(XWPFTableRow row, List<String> values, boolean header) {
        for (int i = 0; i < values.size(); i++) {
            XWPFTableCell cell = row.getCell(i);
            if (cell == null) {
                cell = row.addNewTableCell();
            }
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

    List<ManualLoadEntry> activeRows(String year, LocalDate d) {
        return manualLoadEntryRepository.findAllByAcademicYear(year).stream()
                .filter(r -> notificationLoadHours(r) > 0)
                .filter(r -> r.getLoadFromDate() == null || !r.getLoadFromDate().isAfter(d))
                .filter(r -> r.getLoadToDate() == null || !r.getLoadToDate().isBefore(d))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(this::notificationRowKey, r -> r, (first, second) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private String notificationRowKey(ManualLoadEntry row) {
        return String.join("|",
                normalizeNotificationValue(row.getFioTeacher()),
                normalizeNotificationValue(row.getNumberSchoolBuilding()),
                normalizeNotificationValue(row.getClassName()),
                normalizeNotificationValue(row.getSubjectName()),
                row.getCurriculumPart() == null ? "CORE" : row.getCurriculumPart().name(),
                normalizeNotificationValue(row.getGroupNameEducationalPlan()),
                row.getStudyPeriod() == null ? "" : row.getStudyPeriod().name(),
                String.valueOf(row.getLoadFromDate()),
                String.valueOf(row.getLoadToDate()),
                String.valueOf(notificationLoadHours(row))
        );
    }

    private String normalizeNotificationValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
