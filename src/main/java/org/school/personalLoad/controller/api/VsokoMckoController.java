package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoImportService;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vsoko/mcko")
@RequiredArgsConstructor
public class VsokoMckoController {
    private final VsokoMckoImportService importService;
    private final VsokoMckoQueryService queryService;
    private final AcademicYearService academicYearService;

    @PostMapping("/imports")
    public VsokoMckoDtos.ImportResponse importFiles(@RequestParam("files") List<MultipartFile> files,
                                                    @RequestParam(required = false) String academicYear,
                                                    HttpSession session) {
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "unknown" : user.getUsername();
        return importService.importFiles(resolveYear(academicYear), files, username);
    }

    @GetMapping("/imports")
    public List<VsokoMckoDtos.FileStatusRow> importHistory() {
        return importService.importHistory();
    }

    @GetMapping("/results")
    public List<VsokoMckoDtos.ResultRow> results(@RequestParam(required = false) String academicYear,
                                                @RequestParam(required = false) String className,
                                                @RequestParam(required = false) String subject,
                                                @RequestParam(required = false) String student,
                                                @RequestParam(required = false) String linkStatus,
                                                @RequestParam(required = false) String teacher,
                                                @RequestParam(defaultValue = "5000") int limit) {
        return queryService.results(academicYear, className, subject, student, linkStatus, teacher, limit);
    }

    @GetMapping("/filters")
    public VsokoMckoDtos.FilterOptions filters() {
        return queryService.filters();
    }

    @PatchMapping("/results/{resultId}/student")
    public VsokoMckoDtos.ResultRow linkResult(@PathVariable Long resultId, @RequestBody Map<String, Long> request) {
        Long studentId = request == null ? null : request.get("studentId");
        if (studentId == null) throw new IllegalArgumentException("Не указана карточка ребёнка");
        return queryService.linkResult(resultId, studentId);
    }

    @PostMapping("/results/reconcile")
    public VsokoMckoDtos.ReconcileResponse reconcile() {
        return queryService.reconcile();
    }

    @GetMapping("/students/search")
    public List<VsokoMckoDtos.StudentSearchRow> searchStudents(@RequestParam String q,
                                                              @RequestParam(defaultValue = "30") int limit) {
        return queryService.searchStudents(q, limit);
    }

    @GetMapping("/students/{studentId}/summary")
    public VsokoMckoDtos.StudentSummary studentSummary(@PathVariable Long studentId) {
        return queryService.studentSummary(studentId);
    }

    @GetMapping("/students/{studentId}/summary/export")
    public ResponseEntity<byte[]> exportStudentSummary(@PathVariable Long studentId) throws Exception {
        return excel(queryService.exportStudentSummary(studentId), "История_результатов_ребёнка_" + studentId + ".xlsx");
    }

    @GetMapping("/classes/summary")
    public VsokoMckoDtos.ClassSummary classSummary(@RequestParam(required = false) String academicYear,
                                                   @RequestParam String className) {
        return queryService.classSummary(resolveYear(academicYear), className);
    }

    @GetMapping("/classes/summary/export")
    public ResponseEntity<byte[]> exportClassSummary(@RequestParam(required = false) String academicYear,
                                                     @RequestParam String className) throws Exception {
        String year = resolveYear(academicYear);
        return excel(queryService.exportClassSummary(year, className),
                "Свод_МЦКО_ПА_" + className.replaceAll("[^А-Яа-яA-Za-z0-9-]", "_") + "_" + year.replace('/', '-') + ".xlsx");
    }

    @GetMapping("/results/export")
    public ResponseEntity<byte[]> exportResults(@RequestParam(required = false) String academicYear,
                                                @RequestParam(required = false) String className,
                                                @RequestParam(required = false) String subject,
                                                @RequestParam(required = false) String student,
                                                @RequestParam(required = false) String linkStatus,
                                                @RequestParam(required = false) String teacher) throws Exception {
        byte[] body = queryService.exportResults(academicYear, className, subject, student, linkStatus, teacher);
        return excel(body, "ОБЩИЙ_отчет_МЦКО.xlsx");
    }

    @GetMapping("/assignments")
    public List<VsokoMckoDtos.TeacherAssignmentRow> assignments(@RequestParam(required = false) String academicYear) {
        return queryService.assignments(resolveYear(academicYear));
    }

    @PutMapping("/assignments")
    public VsokoMckoDtos.TeacherAssignmentRow saveAssignment(@RequestBody VsokoMckoDtos.TeacherAssignmentRequest request) {
        return queryService.saveAssignment(request);
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        queryService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments/autofill")
    public Map<String, Integer> autofillAssignments(@RequestParam(required = false) String academicYear) {
        return Map.of("updated", queryService.autofillAssignments(resolveYear(academicYear)));
    }

    @PostMapping("/assignments/import")
    public Map<String, Integer> importAssignments(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) String academicYear) throws Exception {
        return Map.of("imported", queryService.importAssignments(resolveYear(academicYear), file));
    }

    @GetMapping("/assignments/export")
    public ResponseEntity<byte[]> exportAssignments(@RequestParam(required = false) String academicYear) throws Exception {
        String year = resolveYear(academicYear);
        return excel(queryService.exportAssignments(year), "Закрепление_педагогов_" + year.replace('/', '-') + ".xlsx");
    }

    @PostMapping("/interview/export")
    public ResponseEntity<byte[]> interview(@RequestBody VsokoMckoDtos.InterviewRequest request) throws Exception {
        String year = resolveYear(request == null ? null : request.academicYear());
        List<Long> teacherIds = request == null ? List.of() : request.teacherIds();
        return excel(queryService.interviewWorkbook(year, teacherIds), "Собеседование_ВСОКО_" + year.replace('/', '-') + ".xlsx");
    }

    private String resolveYear(String value) {
        return academicYearService.resolveRequestedOrDefault(value);
    }

    private ResponseEntity<byte[]> excel(byte[] body, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
