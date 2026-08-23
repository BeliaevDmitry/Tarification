package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.CorrectionDistributionDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.CorrectionDistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ovz/specialist-distribution")
@RequiredArgsConstructor
public class CorrectionDistributionController {

    private final AcademicYearService academicYearService;
    private final CorrectionDistributionService service;

    @GetMapping("/overview")
    public CorrectionDistributionDtos.Overview overview(@RequestParam(required = false) String academicYear) {
        return service.overview(year(academicYear));
    }

    @GetMapping("/directory")
    public CorrectionDistributionDtos.Directory directory(@RequestParam(required = false) String academicYear) {
        return service.directory(year(academicYear));
    }

    @PutMapping("/directory")
    public CorrectionDistributionDtos.StaffSummary saveStaff(
            @RequestParam(required = false) String academicYear,
            @RequestBody CorrectionDistributionDtos.StaffSaveRequest request) {
        return service.saveStaff(year(academicYear), request);
    }

    @GetMapping("/schedule")
    public CorrectionDistributionDtos.Schedule schedule(
            @RequestParam(required = false) String academicYear,
            @RequestParam Long staffId) {
        return service.schedule(year(academicYear), staffId);
    }

    @PutMapping("/groups")
    public CorrectionDistributionDtos.GroupView saveGroup(
            @RequestParam(required = false) String academicYear,
            @RequestBody CorrectionDistributionDtos.GroupSaveRequest request) {
        return service.saveGroup(year(academicYear), request);
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long groupId) {
        service.deleteGroup(year(academicYear), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/{studentId}")
    public CorrectionDistributionDtos.StudentDistribution student(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long studentId) {
        return service.studentDistribution(year(academicYear), studentId);
    }

    private String year(String requested) {
        return academicYearService.resolveRequestedOrDefault(requested);
    }
}
