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
    public ResponseEntity<List<ClassroomLeadershipEntry>> replaceAll(@RequestParam(required = false) String academicYear,
                                                                     @RequestBody List<ClassroomLeadershipEntryRequest> requests) {
        return ResponseEntity.ok(classroomLeadershipService.replaceAll(academicYear, requests));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam(required = false) String academicYear,
                                                               @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(classroomLeadershipService.importFromExcel(academicYear, file));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> template(@RequestParam(required = false) String academicYear) {
        Resource template = classroomLeadershipService.buildImportTemplate(academicYear);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=classes-template.xlsx")
                .body(template);
    }

    @GetMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> findAll(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(classroomLeadershipService.findAll(academicYear));
    }

    @DeleteMapping("/one")
    public ResponseEntity<Void> deleteOne(@RequestParam(required = false) String academicYear,
                                          @RequestParam String numberSchoolBuilding,
                                          @RequestParam String className) {
        classroomLeadershipService.deleteOne(academicYear, numberSchoolBuilding, className);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@RequestParam(required = false) String academicYear) {
        classroomLeadershipService.clearAll(academicYear);
        return ResponseEntity.noContent().build();
    }
}
