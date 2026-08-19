package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.ContingentService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/contingent")
@RequiredArgsConstructor
public class ContingentController {

    private final ContingentService contingentService;
    private final AcademicYearService academicYearService;

    @PostMapping("/import")
    public ResponseEntity<ContingentDtos.ImportResponse> importFile(@RequestParam("file") MultipartFile file,
                                                                     @RequestParam(required = false) String academicYear,
                                                                     HttpServletRequest httpServletRequest) {
        validateContingentEdit(AuthSessionUtils.requiredUser(httpServletRequest));
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


    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> exportStats(@RequestParam(required = false) String academicYear,
                                              @RequestParam(required = false) String snapshotDate) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (snapshotDate == null || snapshotDate.isBlank()) ? null : LocalDate.parse(snapshotDate);
        byte[] body = contingentService.exportStats(effectiveYear, date);
        String suffix = date == null ? "последние" : date.toString();
        String fileName = "Контингент_" + effectiveYear + "_" + suffix + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
    @GetMapping("/problems")
    public ResponseEntity<List<ContingentDtos.ImportProblem>> problems(@RequestParam(required = false) String academicYear,
                                                                        @RequestParam(required = false) Long snapshotId) {
        return ResponseEntity.ok(contingentService.getProblems(academicYearService.resolveRequestedOrDefault(academicYear), snapshotId));
    }

    @GetMapping("/class-students")
    public ResponseEntity<List<ContingentDtos.ClassStudentView>> classStudents(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String snapshotDate,
            @RequestParam String className) {
        LocalDate date = (snapshotDate == null || snapshotDate.isBlank()) ? null : LocalDate.parse(snapshotDate);
        return ResponseEntity.ok(contingentService.getClassStudents(
                academicYearService.resolveRequestedOrDefault(academicYear), date, className));
    }

    @GetMapping("/class-students/export")
    public ResponseEntity<byte[]> exportClassStudents(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String snapshotDate,
            @RequestParam String className) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        LocalDate date = (snapshotDate == null || snapshotDate.isBlank()) ? null : LocalDate.parse(snapshotDate);
        byte[] body = contingentService.exportClassStudents(effectiveYear, date, className);
        String suffix = date == null ? "последние" : date.toString();
        return workbookResponse(body, "Список класса " + className + " " + suffix + ".xlsx");
    }

    @GetMapping("/import-mismatches")
    public ResponseEntity<ContingentDtos.ImportMismatchResponse> importMismatches(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Long snapshotId) {
        return ResponseEntity.ok(contingentService.getImportMismatches(
                academicYearService.resolveRequestedOrDefault(academicYear), snapshotId
        ));
    }

    @PostMapping("/import-mismatches/resolve")
    public ResponseEntity<ContingentDtos.ImportMismatchResponse> resolveImportMismatch(
            @RequestParam(required = false) String academicYear,
            @RequestBody ContingentDtos.ResolveImportMismatchRequest request,
            HttpServletRequest httpServletRequest) {
        validateContingentEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(contingentService.resolveImportMismatch(
                academicYearService.resolveRequestedOrDefault(academicYear), request
        ));
    }

    @GetMapping("/manual-class-sizes")
    public ResponseEntity<ContingentDtos.ManualClassSizeResponse> manualClassSizes(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(contingentService.getManualClassSizes(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @PutMapping("/manual-class-sizes")
    public ResponseEntity<ContingentDtos.ManualClassSizeResponse> saveManualClassSizes(@RequestParam(required = false) String academicYear,
                                                                                       @RequestBody ContingentDtos.ManualClassSizeSaveRequest request,
                                                                                       HttpServletRequest httpServletRequest) {
        validateContingentEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(contingentService.saveManualClassSizes(academicYearService.resolveRequestedOrDefault(academicYear), request));
    }

    @PostMapping("/manual-class-sizes/import")
    public ResponseEntity<ContingentDtos.ManualClassSizeResponse> importManualClassSizes(@RequestParam("file") MultipartFile file,
                                                                                         @RequestParam(required = false) String academicYear,
                                                                                         HttpServletRequest httpServletRequest) {
        validateContingentEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(contingentService.importManualClassSizes(academicYearService.resolveRequestedOrDefault(academicYear), file));
    }

    @GetMapping("/manual-class-sizes/export")
    public ResponseEntity<byte[]> exportManualClassSizes(@RequestParam(required = false) String academicYear) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = contingentService.exportManualClassSizes(effectiveYear);
        return workbookResponse(body, "Ручная численность классов " + effectiveYear + ".xlsx");
    }

    @PutMapping("/class-size-source")
    public ResponseEntity<ContingentDtos.ManualClassSizeResponse> setClassSizeSource(@RequestParam(required = false) String academicYear,
                                                                                     @RequestBody ContingentDtos.ClassSizeSourceRequest request,
                                                                                     HttpServletRequest httpServletRequest) {
        validateContingentEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(contingentService.setClassSizeSource(
                academicYearService.resolveRequestedOrDefault(academicYear),
                request == null ? null : request.getSource()
        ));
    }

    private ResponseEntity<byte[]> workbookResponse(byte[] body, String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private void validateContingentEdit(SessionUser user) {
        if (!user.canEditTab(AppTab.CONTINGENT_IMPORT) && !user.canEditTab(AppTab.CONTINGENT_STATS)) {
            throw new AuthExceptions.ForbiddenException("Нет прав на редактирование контингента");
        }
    }
}
