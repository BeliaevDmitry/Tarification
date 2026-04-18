package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;
    private final StudyPeriodSettingService studyPeriodSettingService;

    @GetMapping
    public ResponseEntity<List<AcademicYearConfig>> findAll() {
        return ResponseEntity.ok(academicYearService.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, String>> active(@RequestParam(required = false) String requested) {
        return ResponseEntity.ok(Map.of(
                "current", academicYearService.currentByDate(),
                "active", academicYearService.resolveRequestedOrDefault(requested)
        ));
    }

    @PostMapping
    public ResponseEntity<AcademicYearConfig> create(@RequestBody Map<String, String> payload) {
        AcademicYearConfig created = academicYearService.create(payload.get("code"));
        studyPeriodSettingService.findAll(created.getCode());
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/continuity")
    public ResponseEntity<AcademicYearConfig> markContinuity(@PathVariable String code) {
        return ResponseEntity.ok(academicYearService.markContinuityApplied(code));
    }

    @PostMapping("/continuity")
    public ResponseEntity<AcademicYearConfig> markContinuityByQuery(@RequestParam String code) {
        return ResponseEntity.ok(academicYearService.markContinuityApplied(code));
    }

    @PostMapping("/continuity/buildings")
    public ResponseEntity<AcademicYearConfig> applyBuildingContinuity(@RequestParam String code) {
        return ResponseEntity.ok(academicYearService.applyBuildingContinuity(code));
    }
}
