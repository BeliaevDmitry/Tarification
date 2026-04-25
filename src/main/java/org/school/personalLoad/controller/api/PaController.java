package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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

    private final PaService paService;
    private final AcademicYearService academicYearService;

    @PostMapping("/specifications/import")
    public ResponseEntity<List<PaDtos.ImportResult>> importSpecifications(@RequestParam("files") List<MultipartFile> files,
                                                                          @RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.importSpecifications(year, files));
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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pa-specification-" + specificationId + ".xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping("/specifications/summary")
    public ResponseEntity<PaDtos.SummaryResponse> summary(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.summary(year));
    }

    @GetMapping("/reports/versions")
    public ResponseEntity<List<PaDtos.ReportVersionRow>> reportVersions(@RequestParam(required = false) String academicYear,
                                                                        @RequestParam String subjectName,
                                                                        @RequestParam PaScopeType scopeType,
                                                                        @RequestParam String scopeValue,
                                                                        @RequestParam PaLevel level,
                                                                        @RequestParam PaWorkType workType,
                                                                        @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.reportVersions(year, subjectName, scopeType, scopeValue, level, workType, date));
    }

    @GetMapping("/reports/folders")
    public ResponseEntity<List<PaDtos.ReportFolderItem>> reportFolders(@RequestParam(required = false) String academicYear,
                                                                       @RequestParam PaWorkType workType) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.reportFolderItems(year, workType));
    }

    @PostMapping("/reports/upload")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> uploadReports(@RequestParam("files") List<MultipartFile> files,
                                                                         @RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.uploadReports(year, files));
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
                                                                    @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateReportTemplate(year, subjectName, className, level, workType, date));
    }

    @PostMapping("/reports/generate/parallel")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> generateReportsForParallel(@RequestParam(required = false) String academicYear,
                                                                                       @RequestParam String subjectName,
                                                                                       @RequestParam String parallel,
                                                                                       @RequestParam PaLevel level,
                                                                                       @RequestParam PaWorkType workType,
                                                                                       @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateReportTemplatesByParallel(year, subjectName, parallel, level, workType, date));
    }

    @PostMapping("/reports/generate/all")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> generateReportsForAll(@RequestParam(required = false) String academicYear,
                                                                                  @RequestParam String subjectName,
                                                                                  @RequestParam PaLevel level,
                                                                                  @RequestParam PaWorkType workType,
                                                                                  @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.generateAllReportTemplates(year, subjectName, level, workType, date));
    }

    @GetMapping("/reports/{reportVersionId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long reportVersionId) throws Exception {
        byte[] body = paService.loadReportFile(reportVersionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pa-report-" + reportVersionId + ".xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
