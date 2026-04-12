package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ContingentDtos;
import org.school.personalLoad.service.ContingentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/contingent")
@RequiredArgsConstructor
public class ContingentController {

    private final ContingentService contingentService;

    @PostMapping("/import")
    public ResponseEntity<ContingentDtos.ImportResultResponse> importSnapshot(@RequestParam(required = false) String academicYear,
                                                                              @RequestParam("file") MultipartFile file,
                                                                              @RequestParam(required = false) String snapshotDate) {
        LocalDate fallback = (snapshotDate == null || snapshotDate.isBlank()) ? null : LocalDate.parse(snapshotDate);
        return ResponseEntity.ok(contingentService.importSnapshot(academicYear, file, fallback));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<ContingentDtos.SnapshotResponse>> snapshots(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(contingentService.listSnapshots(academicYear));
    }

    @GetMapping("/students")
    public ResponseEntity<List<ContingentDtos.StudentResponse>> students(@RequestParam Long snapshotId,
                                                                         @RequestParam(required = false) String buildingCode,
                                                                         @RequestParam(required = false) Integer parallel,
                                                                         @RequestParam(required = false) String className,
                                                                         @RequestParam(required = false) String query) {
        return ResponseEntity.ok(contingentService.listStudents(snapshotId, buildingCode, parallel, className, query));
    }

    @GetMapping("/summary/classes")
    public ResponseEntity<List<ContingentDtos.ClassSummaryResponse>> classSummary(@RequestParam Long snapshotId) {
        return ResponseEntity.ok(contingentService.classSummary(snapshotId));
    }

    @GetMapping("/summary/parallels")
    public ResponseEntity<List<ContingentDtos.ParallelSummaryResponse>> parallelSummary(@RequestParam Long snapshotId) {
        return ResponseEntity.ok(contingentService.parallelSummary(snapshotId));
    }

    @GetMapping("/summary/buildings")
    public ResponseEntity<List<ContingentDtos.BuildingSummaryResponse>> buildingSummary(@RequestParam Long snapshotId) {
        return ResponseEntity.ok(contingentService.buildingSummary(snapshotId));
    }

    @GetMapping("/warnings")
    public ResponseEntity<List<ContingentDtos.WarningResponse>> warnings(@RequestParam Long snapshotId) {
        return ResponseEntity.ok(contingentService.warnings(snapshotId));
    }

    @PostMapping("/{snapshotId}/recalculate-warnings")
    public ResponseEntity<List<ContingentDtos.WarningResponse>> recalculateWarnings(@PathVariable Long snapshotId) {
        return ResponseEntity.ok(contingentService.recalculateWarnings(snapshotId));
    }
}

