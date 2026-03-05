package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.service.SubjectCatalogService;
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

    @PostMapping
    public ResponseEntity<SubjectCatalogEntry> create(@RequestBody SubjectCreateRequest request) {
        return ResponseEntity.ok(subjectCatalogService.create(request));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(subjectCatalogService.importFromExcel(file));
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
