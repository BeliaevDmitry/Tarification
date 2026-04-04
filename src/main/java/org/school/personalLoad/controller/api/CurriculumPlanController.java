package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.service.CurriculumImportService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;
    private final CurriculumImportService curriculumImportService;

    @PostMapping
    public ResponseEntity<CurriculumPlanEntry> upsert(@RequestBody CurriculumPlanEntryRequest request) {
        return ResponseEntity.ok(curriculumPlanService.upsert(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<CurriculumPlanEntry>> upsertBulk(@RequestBody List<CurriculumPlanEntryRequest> requests) {
        return ResponseEntity.ok(curriculumPlanService.upsertBulk(requests));
    }


    @PostMapping("/import")
    public ResponseEntity<CurriculumImportResult> importCurriculum(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(curriculumImportService.importFile(file));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCurriculum() throws Exception {
        byte[] body = curriculumImportService.exportEditableWorkbook();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"curriculum-editable.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping
    public ResponseEntity<List<CurriculumPlanEntry>> findAll() {
        return ResponseEntity.ok(curriculumPlanService.findAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CurriculumPlanEntry> updateById(@PathVariable Long id, @RequestBody CurriculumPlanEntryRequest request) {
        return ResponseEntity.ok(curriculumPlanService.updateById(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        curriculumPlanService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        curriculumPlanService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
