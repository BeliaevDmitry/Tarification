package org.school.personalLoad.controller.api;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TeacherNotificationRecordRepository recordRepository;

    @GetMapping
    public List<Row> list(@RequestParam String academicYear, @RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.of(2026, 9, 1) : LocalDate.parse(date);
        Map<String, List<ManualLoadEntry>> byTeacher = activeRows(academicYear, d).stream().collect(Collectors.groupingBy(ManualLoadEntry::getFioTeacher));
        Map<String, TeacherNotificationRecord> records = recordRepository.findAllByAcademicYear(academicYear).stream().collect(Collectors.toMap(TeacherNotificationRecord::getFioTeacher, r -> r, (a,b)->a));
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

    private List<ManualLoadEntry> activeRows(String year, LocalDate d) {
        return manualLoadEntryRepository.findAllByAcademicYear(year).stream().filter(r -> r.getLoad()!=null && r.getLoad() > 0)
                .filter(r -> r.getLoadFromDate() == null || !r.getLoadFromDate().isAfter(d))
                .filter(r -> r.getLoadToDate() == null || !r.getLoadToDate().isBefore(d)).collect(Collectors.toList());
    }

    private void upsert(String fio, String year, LocalDate d, String user) {
        List<ManualLoadEntry> rows = activeRows(year, d).stream().filter(r -> fio.equalsIgnoreCase(r.getFioTeacher())).collect(Collectors.toList());
        String hash = hashOf(rows);
        TeacherNotificationRecord rec = recordRepository.findByAcademicYearAndFioTeacherIgnoreCase(year, fio).orElseGet(TeacherNotificationRecord::new);
        rec.setAcademicYear(year); rec.setFioTeacher(fio); rec.setNotificationDate(d); rec.setDataHash(hash); rec.setGeneratedBy(user); rec.setGeneratedAt(LocalDateTime.now()); rec.setDownloadedAt(LocalDateTime.now()); rec.setDownloadedBy(user);
        recordRepository.save(rec);
    }

    private byte[] generateDoc(String fio, String year, LocalDate d) throws Exception {
        List<ManualLoadEntry> rows = activeRows(year, d).stream().filter(r -> fio.equalsIgnoreCase(r.getFioTeacher())).sorted(Comparator.comparing(ManualLoadEntry::getClassName)).collect(Collectors.toList());
        String schoolCode = System.getenv().getOrDefault("SCHOOL_CODE", "demo").toLowerCase(Locale.ROOT);
        List<String> headerLines = templateLines(schoolCode, fio, d, year);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : headerLines) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            doc.createParagraph().createRun().setText(" ");
            for (ManualLoadEntry r: rows) { doc.createParagraph().createRun().setText(r.getSubjectName()+" | "+r.getClassName()+" | "+r.getLoad()); }
            doc.write(out); return out.toByteArray();
        }
    }


    private List<String> templateLines(String schoolCode, String fio, LocalDate date, String year) {
        String fileName = String.format("templates/teacher-notifications/notification-%s.txt", schoolCode);
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(fileName)) {
            java.io.InputStream src = in != null ? in : getClass().getClassLoader().getResourceAsStream("templates/teacher-notifications/notification-demo.txt");
            if (src == null) {
                return List.of("Уведомление о нагрузке", "Дата: " + date, "Педагог: " + fio, "Учебный год: " + year);
            }
            String raw = new String(src.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String rendered = raw.replace("${DATE}", String.valueOf(date)).replace("${TEACHER}", fio).replace("${ACADEMIC_YEAR}", year);
            return Arrays.stream(rendered.split("\\R")).collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of("Уведомление о нагрузке", "Дата: " + date, "Педагог: " + fio, "Учебный год: " + year);
        }
    }

    private String hashOf(List<ManualLoadEntry> rows) {
        return Integer.toHexString(rows.stream().map(r -> (r.getSubjectName()+"|"+r.getClassName()+"|"+r.getLoad()+"|"+r.getLoadFromDate()+"|"+r.getLoadToDate())).sorted().collect(Collectors.joining(";")).hashCode());
    }

    @Data @Builder
    private static class Row { String fio; boolean generated; boolean changed; }
}
