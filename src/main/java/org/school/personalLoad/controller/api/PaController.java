package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.school.personalLoad.pa.service.PaService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pa")
@RequiredArgsConstructor
public class PaController {

    private final PaService paService;
    private final AcademicYearService academicYearService;

    @PostMapping("/specifications/import")
    public ResponseEntity<List<PaDtos.ImportResult>> importSpecifications(@RequestParam("files") List<MultipartFile> files,
                                                                          @RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.importSpecifications(year, files));
    }

    @GetMapping("/specifications")
    public ResponseEntity<List<PaDtos.SpecificationRow>> specifications(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.specifications(year));
    }

    @GetMapping("/specifications/{specificationId}/tasks")
    public ResponseEntity<List<PaDtos.SpecificationTaskRow>> tasks(@PathVariable Long specificationId) {
        return ResponseEntity.ok(paService.specificationTasks(specificationId));
    }

    @GetMapping("/specifications/summary")
    public ResponseEntity<PaDtos.SummaryResponse> summary(@RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.summary(year));
    }

    @GetMapping("/reports/versions")
    public ResponseEntity<List<PaDtos.ReportVersionRow>> reportVersions(@RequestParam(required = false) String academicYear,
                                                                        @RequestParam String subjectName,
                                                                        @RequestParam PaScopeType scopeType,
                                                                        @RequestParam String scopeValue,
                                                                        @RequestParam PaLevel level,
                                                                        @RequestParam PaWorkType workType,
                                                                        @RequestParam(required = false) String workDate) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (workDate == null || workDate.isBlank()) ? null : LocalDate.parse(workDate);
        return ResponseEntity.ok(paService.reportVersions(year, subjectName, scopeType, scopeValue, level, workType, date));
    }

    @PostMapping("/reports/upload")
    public ResponseEntity<List<PaDtos.ReportUploadResult>> uploadReports(@RequestParam("files") List<MultipartFile> files,
                                                                         @RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(paService.uploadReports(year, files));
    }
}
