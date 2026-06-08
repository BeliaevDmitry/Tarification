package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.dto.SubjectLevelCoefficientRequest;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.school.personalLoad.service.SubjectCatalogService;
import org.school.personalLoad.service.SubjectLevelCoefficientService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectCatalogController {

    private final SubjectCatalogService subjectCatalogService;
    private final SubjectLevelCoefficientService subjectLevelCoefficientService;

    @PostMapping
    public ResponseEntity<SubjectCatalogEntry> create(@RequestBody SubjectCreateRequest request) {
        return ResponseEntity.ok(subjectCatalogService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SubjectCatalogEntry> update(@PathVariable Long id, @RequestBody SubjectCreateRequest request) {
        return ResponseEntity.ok(subjectCatalogService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        subjectCatalogService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(subjectCatalogService.importFromExcel(file));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = subjectCatalogService.buildImportTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=subjects-template.xlsx")
                .body(resource);
    }

    @GetMapping("/coefficients")
    public ResponseEntity<List<SubjectLevelCoefficientEntry>> findAllCoefficients() {
        return ResponseEntity.ok(subjectLevelCoefficientService.findAll());
    }

    @PostMapping("/coefficients")
    public ResponseEntity<SubjectLevelCoefficientEntry> saveCoefficient(@RequestBody SubjectLevelCoefficientRequest request) {
        return ResponseEntity.ok(subjectLevelCoefficientService.save(request));
    }

    @DeleteMapping("/coefficients/{id}")
    public ResponseEntity<Void> deleteCoefficient(@PathVariable Long id) {
        subjectLevelCoefficientService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coefficients/import")
    public ResponseEntity<Map<String, Object>> importCoefficientsFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(subjectLevelCoefficientService.importFromExcel(file));
    }

    @GetMapping("/coefficients/export")
    public ResponseEntity<Resource> exportCoefficients() {
        Resource resource = subjectLevelCoefficientService.exportWorkbook();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=subject-coefficients.xlsx")
                .body(resource);
    }

    @GetMapping
    public ResponseEntity<List<SubjectCatalogEntry>> findAll() {
        return ResponseEntity.ok(subjectCatalogService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        subjectCatalogService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
