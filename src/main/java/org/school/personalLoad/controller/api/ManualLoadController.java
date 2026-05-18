package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.dto.ManualLoadHealthResponse;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.service.ManualLoadService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;
    private final AcademicYearService academicYearService;

    @PostMapping
    public ResponseEntity<ManualLoadEntry> create(@RequestParam(required = false) String academicYear, @RequestBody ManualLoadEntryRequest request, HttpServletRequest httpServletRequest) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), List.of(request));
        return ResponseEntity.ok(manualLoadService.create(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestParam(required = false) String academicYear, @RequestBody List<ManualLoadEntryRequest> requests,
                                                            HttpServletRequest httpServletRequest) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        requests.forEach(req -> req.setAcademicYear(effectiveYear));
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), requests);
        return ResponseEntity.ok(manualLoadService.createBulk(requests));
    }

    @GetMapping
    public ResponseEntity<List<ManualLoadEntry>> findAll(@RequestParam(required = false) String academicYear,
                                                         @RequestParam(required = false) String building) {
        return ResponseEntity.ok(manualLoadService.findAll(
                academicYearService.resolveRequestedOrDefault(academicYear),
                building
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestParam(required = false) String academicYear,
                                      @RequestParam(required = false) String building,
                                      HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        if (building != null && !building.isBlank()) {
            if (!user.isAdmin()) {
                throw new ForbiddenException("Операция доступна только администратору");
            }
            String currentYear = academicYearService.currentByDate();
            if (!currentYear.equals(effectiveYear)) {
                throw new IllegalArgumentException("Удаление нагрузки корпуса доступно только для текущего учебного года: " + currentYear);
            }
            manualLoadService.clearByBuilding(effectiveYear, building);
            return ResponseEntity.noContent().build();
        }
        validateGlobalLoadOperation(user);
        manualLoadService.clearAll(effectiveYear);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<ManualLoadProcessResult> process(@RequestParam(required = false) String academicYear, HttpServletRequest httpServletRequest) {
        validateGlobalLoadOperation(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(manualLoadService.processCurrentManualLoad(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkbook(@RequestParam(required = false) String academicYear, HttpServletRequest httpServletRequest) throws Exception {
        validateGlobalLoadOperation(AuthSessionUtils.requiredUser(httpServletRequest));
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = manualLoadService.exportWorkbook(effectiveYear);
        String date = LocalDate.now().toString();
        String fileName = "Распределение нагрузки " + effectiveYear + " " + date + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/import")
    public ResponseEntity<List<ManualLoadEntry>> importWorkbook(@RequestParam(required = false) String academicYear,
                                                                @RequestParam("file") MultipartFile file,
                                                                HttpServletRequest httpServletRequest) {
        validateGlobalLoadOperation(AuthSessionUtils.requiredUser(httpServletRequest));
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        List<ManualLoadEntry> imported = manualLoadService.importWorkbook(effectiveYear, file);
        return ResponseEntity.ok(imported);
    }

    @GetMapping("/stats")
    public ResponseEntity<ManualLoadStatsResponse> stats(@RequestParam(required = false) String academicYear,
                                                         @RequestParam(required = false) String building,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "100") int pageSize) {
        return ResponseEntity.ok(manualLoadService.buildStats(
                academicYearService.resolveRequestedOrDefault(academicYear),
                building,
                page,
                pageSize
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<ManualLoadHealthResponse> health(@RequestParam(required = false) String academicYear,
                                                           @RequestParam(required = false) String building) {
        return ResponseEntity.ok(manualLoadService.buildHealth(
                academicYearService.resolveRequestedOrDefault(academicYear),
                building
        ));
    }

    private void validateLoadAccess(SessionUser user, List<ManualLoadEntryRequest> requests) {
        if (user.isAdmin()) {
            return;
        }
        Set<String> forbiddenBuildings = requests.stream()
                .map(ManualLoadEntryRequest::getNumberSchoolBuilding)
                .filter(buildingCode -> !user.canEditLoadBuilding(buildingCode))
                .map(buildingCode -> buildingCode == null || buildingCode.isBlank() ? "[корпус не указан]" : buildingCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!forbiddenBuildings.isEmpty()) {
            throw new ForbiddenException("Нет прав на редактирование нагрузки по корпусам: " + String.join(", ", forbiddenBuildings));
        }
    }

    private void validateGlobalLoadOperation(SessionUser user) {
        if (user.isAdmin() || user.isLoadEditAllBuildings()) {
            return;
        }
        throw new ForbiddenException("Глобальные операции с нагрузкой доступны только пользователям с правом редактировать все корпуса");
    }
}
