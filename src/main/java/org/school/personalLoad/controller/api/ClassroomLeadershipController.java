package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classroom-leadership")
@RequiredArgsConstructor
public class ClassroomLeadershipController {

    private final ClassroomLeadershipService classroomLeadershipService;

    @PutMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> replaceAll(@RequestBody List<ClassroomLeadershipEntryRequest> requests) {
        return ResponseEntity.ok(classroomLeadershipService.replaceAll(requests));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(classroomLeadershipService.importFromExcel(file));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> template() {
        Resource template = classroomLeadershipService.buildImportTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=classes-template.xlsx")
                .body(template);
    }

    @GetMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> findAll() {
        return ResponseEntity.ok(classroomLeadershipService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        classroomLeadershipService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
