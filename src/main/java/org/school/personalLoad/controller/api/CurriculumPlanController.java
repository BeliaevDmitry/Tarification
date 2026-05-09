package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.service.CurriculumImportService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;
    private final CurriculumImportService curriculumImportService;
    private final AcademicYearService academicYearService;

    @PostMapping
    public ResponseEntity<CurriculumPlanEntry> upsert(@RequestParam(required = false) String academicYear, @RequestBody CurriculumPlanEntryRequest request) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok(curriculumPlanService.upsert(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<CurriculumPlanEntry>> upsertBulk(@RequestParam(required = false) String academicYear, @RequestBody List<CurriculumPlanEntryRequest> requests) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        requests.forEach(req -> req.setAcademicYear(effectiveYear));
        return ResponseEntity.ok(curriculumPlanService.upsertBulk(requests));
    }


    @PostMapping("/import")
    public ResponseEntity<CurriculumImportResult> importCurriculum(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String academicYear) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(curriculumImportService.importFile(file, effectiveYear));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCurriculum(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = curriculumImportService.exportEditableWorkbook(effectiveYear);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String fileName = "УП ГБОУ 7 " + effectiveYear + " от " + date + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping
    public ResponseEntity<List<CurriculumPlanEntry>> findAll(@RequestParam(required = false) String academicYear,
                                                             @RequestParam(required = false) String building) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(curriculumPlanService.findAll(effectiveYear, building));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CurriculumPlanEntry> updateById(@PathVariable Long id, @RequestParam(required = false) String academicYear, @RequestBody CurriculumPlanEntryRequest request) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok(curriculumPlanService.updateById(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        curriculumPlanService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@RequestParam(required = false) String academicYear) {
        curriculumPlanService.clearAll(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.noContent().build();
    }
}
