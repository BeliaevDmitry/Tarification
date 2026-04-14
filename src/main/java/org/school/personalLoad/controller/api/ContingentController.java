package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.service.AcademicYearService;
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
    private final AcademicYearService academicYearService;

    @PostMapping("/import")
    public ResponseEntity<ContingentDtos.ImportResponse> importFile(@RequestParam("file") MultipartFile file,
                                                                     @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(contingentService.importSnapshot(academicYearService.resolveRequestedOrDefault(academicYear), file));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<ContingentDtos.SnapshotListItem>> snapshots(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(contingentService.listSnapshots(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ContingentDtos.StatsResponse> stats(@RequestParam(required = false) String academicYear,
                                                               @RequestParam(required = false) String snapshotDate) {
        LocalDate date = (snapshotDate == null || snapshotDate.isBlank()) ? null : LocalDate.parse(snapshotDate);
        return ResponseEntity.ok(contingentService.getStats(academicYearService.resolveRequestedOrDefault(academicYear), date));
    }

    @GetMapping("/problems")
    public ResponseEntity<List<ContingentDtos.ImportProblem>> problems(@RequestParam(required = false) String academicYear,
                                                                        @RequestParam(required = false) Long snapshotId) {
        return ResponseEntity.ok(contingentService.getProblems(academicYearService.resolveRequestedOrDefault(academicYear), snapshotId));
    }
}
