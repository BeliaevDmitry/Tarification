package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.school.personalLoad.pa.service.PaService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/pa")
@RequiredArgsConstructor
public class PaController {

    public record ParticipationRequest(String subjectName,
                                       PaScopeType scopeType,
                                       String scopeValue,
                                       PaLevel level,
                                       Boolean participates) {
    }
    public record ClassLevelAssignmentsRequest(List<PaDtos.ClassLevelAssignmentRow> rows) {}

    private final PaService paService;
    private final AcademicYearService academicYearService;

    @PostMapping("/specifications/import")
    public ResponseEntity<List<PaDtos.ImportResult>> importSpecifications(@RequestParam("files") List<MultipartFile> files,
                                                                          @RequestParam(required = false) String academicYear,
                                                                          HttpSession session) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SessionUser user = (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "unknown" : user.getUsername();
        return ResponseEntity.ok(paService.importSpecifications(year, files, username));
    }

    @GetMapping("/specifications/import-log")
    public ResponseEntity<List<PaDtos.ImportLogRow>> specificationImportLog(@RequestParam(required = false) String academicYear,
                                                                             HttpSession session) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SessionUser user = (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "unknown" : user.getUsername();
        boolean admin = user != null && user.isAdmin();
        return ResponseEntity.ok(paService.specificationImportLog(year, username, admin));
    }

    @GetMapping("/specifications/import-log/{importLogId}/download")
    public ResponseEntity<byte[]> downloadSpecificationImportFile(@PathVariable Long importLogId,
                                                                  @RequestParam(required = false) String academicYear) throws Exception {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = paService.loadSpecificationImportLogFile(year, importLogId);
        String fileName = paService.specificationImportLogFileName(year, importLogId);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping("/specifications/import-file/download")
    public ResponseEntity<byte[]> downloadSpecificationImportFileByName(@RequestParam String fileName,
                                                                        @RequestParam(required = false) String academicYear) throws Exception {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = paService.loadSpecificationImportFileByName(year, fileName);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping("/specifications")
    public ResponseEntity<List<PaDtos.SpecificationRow>> specifications(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.specifications(year));
    }

    @GetMapping("/specifications/{specificationId}/tasks")
    public ResponseEntity<List<PaDtos.SpecificationTaskRow>> tasks(@PathVariable Long specificationId) {
        return ResponseEntity.ok(paService.specificationTasks(specificationId));
    }

    @GetMapping("/specifications/{specificationId}/download")
    public ResponseEntity<byte[]> downloadSpecification(@PathVariable Long specificationId,
                                                        @RequestParam(required = false) String academicYear) throws Exception {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = paService.loadSpecificationFile(year, specificationId);
        String fileName = paService.specificationFileName(year, specificationId);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @DeleteMapping("/specifications/{specificationId}")
    public ResponseEntity<Void> deleteSpecification(@PathVariable Long specificationId,
                                                    @RequestParam(required = false) String academicYear) throws Exception {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        paService.deleteSpecification(year, specificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/specifications/summary")
    public ResponseEntity<PaDtos.SummaryResponse> summary(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.summary(year));
    }

    @GetMapping("/specifications/class-level-assignments")
    public ResponseEntity<List<PaDtos.ClassLevelAssignmentRow>> classLevelAssignments(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.classLevelAssignments(year));
    }

    @PutMapping("/specifications/class-level-assignments")
    public ResponseEntity<Void> saveClassLevelAssignments(@RequestParam(required = false) String academicYear,
                                                          @RequestBody ClassLevelAssignmentsRequest request) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        paService.saveClassLevelAssignments(year, request == null ? List.of() : request.rows());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/versions")
    public ResponseEntity<List<PaDtos.ReportVersionRow>> reportVersions(@RequestParam(required = false) String academicYear,
                                                                        @RequestParam String subjectName,
                                                                        @RequestParam PaScopeType scopeType,
                                                                        @RequestParam String scopeValue,
                                                                        @RequestParam PaLevel level,
                                                                        @RequestParam PaWorkType workType,
                                                                        @RequestParam(required = false) String workDate,
                                                                    @RequestParam(defaultValue = "false") boolean force) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.reportVersions(year, subjectName, scopeType, scopeValue, level, workType, date));
    }

    @GetMapping("/reports/workflow-summary")
    public ResponseEntity<List<PaDtos.ReportWorkflowSummaryItem>> reportWorkflowSummary(@RequestParam(required = false) String academicYear,
                                                                                         @RequestParam PaLevel level,
                                                                                         @RequestParam PaWorkType workType,
                                                                                         @RequestParam(required = false) String subjectName) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.reportWorkflowSummary(year, level, workType, subjectName));
    }

    @GetMapping("/reports/folders")
    public ResponseEntity<List<PaDtos.ReportFolderItem>> reportFolders(@RequestParam(required = false) String academicYear,
                                                                       @RequestParam PaWorkType workType) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.reportFolderItems(year, workType));
    }

    @PostMapping("/reports/upload")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> uploadReports(@RequestParam("files") List<MultipartFile> files,
                                                                         @RequestParam(required = false) String academicYear,
                                                                         HttpSession session) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "anonymous" : user.getUsername();
        String fullName = user == null || user.getFullName() == null || user.getFullName().isBlank() ? "Аноним" : user.getFullName();
        return ResponseEntity.ok(paService.uploadReports(year, files, username, fullName));
    }

    @GetMapping("/reports/upload-log")
    public ResponseEntity<List<PaDtos.ReportUploadLogRow>> reportUploadLog(@RequestParam(required = false) String academicYear,
                                                                            HttpSession session) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "anonymous" : user.getUsername();
        boolean admin = user != null && user.isAdmin();
        return ResponseEntity.ok(paService.reportUploadLog(year, username, admin));
    }

    @GetMapping("/reports/upload-log/download")
    public ResponseEntity<byte[]> downloadReportUploadLog(@RequestParam(required = false) String academicYear,
                                                          HttpSession session) throws Exception {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "anonymous" : user.getUsername();
        boolean admin = user != null && user.isAdmin();
        byte[] body = paService.downloadReportUploadLogExcel(year, username, admin);
        String fileName = "Сдача_ПА_история_" + year.replace("/", "-") + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @PatchMapping("/participation")
    public ResponseEntity<Void> setParticipation(@RequestParam(required = false) String academicYear,
                                                 @RequestBody ParticipationRequest request) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        if (request == null || request.subjectName == null || request.scopeType == null || request.scopeValue == null || request.level == null || request.participates == null) {
            throw new IllegalArgumentException("Не переданы обязательные поля участия");
        }
        paService.setParticipation(year, request.subjectName, request.scopeType, request.scopeValue, request.level, request.participates);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/generate")
    public ResponseEntity<PaDtos.ReportUploadResult> generateReport(@RequestParam(required = false) String academicYear,
                                                                    @RequestParam String subjectName,
                                                                    @RequestParam String className,
                                                                    @RequestParam PaLevel level,
                                                                    @RequestParam PaWorkType workType,
                                                                    @RequestParam(required = false) String workDate,
                                                                                       @RequestParam(defaultValue = "false") boolean force) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateReportTemplate(year, subjectName, className, level, workType, date, force));
    }

    @PostMapping("/reports/generate/parallel")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> generateReportsForParallel(@RequestParam(required = false) String academicYear,
                                                                                       @RequestParam String subjectName,
                                                                                       @RequestParam String parallel,
                                                                                       @RequestParam PaLevel level,
                                                                                       @RequestParam PaWorkType workType,
                                                                                       @RequestParam(required = false) String workDate,
                                                                                  @RequestParam(defaultValue = "false") boolean force) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateReportTemplatesByParallel(year, subjectName, parallel, level, workType, date, force));
    }

    @PostMapping("/reports/generate/all")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> generateReportsForAll(@RequestParam(required = false) String academicYear,
                                                                                  @RequestParam String subjectName,
                                                                                  @RequestParam PaLevel level,
                                                                                  @RequestParam PaWorkType workType,
                                                                                  @RequestParam(required = false) String workDate,
                                                                                  @RequestParam(defaultValue = "false") boolean force) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateAllReportTemplates(year, subjectName, level, workType, date, force));
    }



    @DeleteMapping("/reports/generated")
    public ResponseEntity<java.util.Map<String, Object>> deleteGeneratedReports(@RequestParam(required = false) String academicYear,
                                                                                 @RequestParam String subjectName,
                                                                                 @RequestParam String scopeValue,
                                                                                 @RequestParam(defaultValue = "false") boolean byParallel,
                                                                                 @RequestParam PaLevel level,
                                                                                 @RequestParam PaWorkType workType,
                                                                                 @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        int removed = paService.deleteGeneratedReports(year, subjectName, scopeValue, byParallel, level, workType, date);
        return ResponseEntity.ok(java.util.Map.of("deleted", removed));
    }

    @GetMapping("/reports/{reportVersionId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long reportVersionId) throws Exception {
        byte[] body = paService.loadReportFile(reportVersionId);
        String fileName = paService.reportFileName(reportVersionId);
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
