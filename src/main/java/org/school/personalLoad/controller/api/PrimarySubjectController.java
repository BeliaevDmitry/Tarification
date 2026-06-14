package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.PrimarySubjectDtos;
import org.school.personalLoad.model.PrimarySubjectRule;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.PrimarySubjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/primary-subjects")
@RequiredArgsConstructor
public class PrimarySubjectController {

    private final PrimarySubjectService service;
    private final AcademicYearService academicYearService;

    @GetMapping("/teachers")
    public ResponseEntity<List<PrimarySubjectDtos.TeacherPrimarySubjectRow>> teachers(
            @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(service.getTeacherAssignments(resolveYear(academicYear)));
    }

    @PostMapping("/determine")
    public ResponseEntity<PrimarySubjectDtos.DetermineResult> determine(
            @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(service.determine(resolveYear(academicYear)));
    }

    @PutMapping("/teachers/{teacherId}")
    public ResponseEntity<PrimarySubjectDtos.TeacherPrimarySubjectRow> setManual(
            @PathVariable Long teacherId,
            @RequestParam(required = false) String academicYear,
            @RequestBody PrimarySubjectDtos.AssignmentRequest request) {
        return ResponseEntity.ok(service.setManual(resolveYear(academicYear), teacherId, request.primarySubject()));
    }

    @DeleteMapping("/teachers/{teacherId}")
    public ResponseEntity<Void> clear(
            @PathVariable Long teacherId,
            @RequestParam(required = false) String academicYear) {
        service.clearAssignment(resolveYear(academicYear), teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rules")
    public ResponseEntity<List<PrimarySubjectRule>> rules() {
        return ResponseEntity.ok(service.getRules());
    }

    @PostMapping("/rules")
    public ResponseEntity<PrimarySubjectRule> createRule(@RequestBody PrimarySubjectDtos.RuleRequest request) {
        return ResponseEntity.ok(service.saveRule(null, request));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<PrimarySubjectRule> updateRule(@PathVariable Long id,
                                                         @RequestBody PrimarySubjectDtos.RuleRequest request) {
        return ResponseEntity.ok(service.saveRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveYear(String academicYear) {
        return academicYearService.resolveRequestedOrDefault(academicYear);
    }
}
