package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.school.personalLoad.service.AcademicYearService;
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
    private final AcademicYearService academicYearService;

    @PutMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> replaceAll(@RequestParam(required = false) String academicYear, @RequestBody List<ClassroomLeadershipEntryRequest> requests) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        requests.forEach(req -> req.setAcademicYear(effectiveYear));
        return ResponseEntity.ok(classroomLeadershipService.replaceAll(requests));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(classroomLeadershipService.importFromExcel(academicYearService.resolveRequestedOrDefault(academicYear), file));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> template(@RequestParam(required = false) String academicYear) {
        Resource template = classroomLeadershipService.buildImportTemplate(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=classes-template.xlsx")
                .body(template);
    }

    @GetMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> findAll(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(classroomLeadershipService.findAll(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @DeleteMapping("/one")
    public ResponseEntity<Void> deleteOne(@RequestParam String numberSchoolBuilding,
                                          @RequestParam String className,
                                          @RequestParam(required = false) String academicYear) {
        classroomLeadershipService.deleteOne(academicYearService.resolveRequestedOrDefault(academicYear), numberSchoolBuilding, className);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@RequestParam(required = false) String academicYear) {
        classroomLeadershipService.clearAll(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.noContent().build();
    }
}
