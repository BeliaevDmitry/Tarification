package org.school.personalLoad.controller.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ManualLoadBulkRequest;
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
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;
    private final AcademicYearService academicYearService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ManualLoadEntry> create(@RequestParam(required = false) String academicYear, @RequestBody ManualLoadEntryRequest request, HttpServletRequest httpServletRequest) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), List.of(request));
        return ResponseEntity.ok(manualLoadService.create(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestParam(required = false) String academicYear,
                                                            @RequestBody JsonNode body,
                                                            HttpServletRequest httpServletRequest) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        ManualLoadBulkRequest bulkRequest = parseBulkRequest(body);
        bulkRequest.setAcademicYear(effectiveYear);
        List<ManualLoadEntryRequest> requests = bulkRequest.getRows() == null ? List.of() : bulkRequest.getRows();
        requests.forEach(req -> req.setAcademicYear(effectiveYear));
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), bulkRequest, requests);
        return ResponseEntity.ok(manualLoadService.createBulk(bulkRequest));
    }

    @GetMapping
    public ResponseEntity<List<ManualLoadEntry>> findAll(@RequestParam(required = false) String academicYear,
                                                         @RequestParam(required = false) String building,
                                                         @RequestParam(required = false) String numberSchoolBuilding,
                                                         @RequestParam(required = false) String campusAddress) {
        String effectiveBuilding = firstNonBlank(numberSchoolBuilding, building);
        return ResponseEntity.ok(manualLoadService.findAll(
                academicYearService.resolveRequestedOrDefault(academicYear),
                effectiveBuilding,
                campusAddress
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestParam(required = false) String academicYear,
                                      @RequestParam(required = false) String building,
                                      @RequestParam(required = false) String numberSchoolBuilding,
                                      @RequestParam(required = false) String campusAddress,
                                      @RequestParam(required = false) String scopeType,
                                      HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        String effectiveBuilding = firstNonBlank(numberSchoolBuilding, building);
        if (effectiveBuilding != null && !effectiveBuilding.isBlank()) {
            if (!user.isAdmin()) {
                throw new ForbiddenException("Операция доступна только администратору");
            }
            if (campusAddress != null && !campusAddress.isBlank()) {
                manualLoadService.clearByBuildingAddress(effectiveYear, effectiveBuilding, campusAddress);
            } else {
                manualLoadService.clearByBuilding(effectiveYear, effectiveBuilding, scopeType);
            }
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

    @GetMapping("/export-full")
    public ResponseEntity<byte[]> exportFullWorkbook(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = manualLoadService.exportFullWorkbook(effectiveYear);
        return workbookResponse(body, "Полная нагрузка " + effectiveYear + " " + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/export-full-salary")
    public ResponseEntity<byte[]> exportFullWorkbookWithSalary(@RequestParam(required = false) String academicYear,
                                                               HttpServletRequest httpServletRequest) throws Exception {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        if (!user.canExportSalary()) {
            throw new ForbiddenException("Нет прав на экспорт полной нагрузки с расчётом зарплаты");
        }
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = manualLoadService.exportFullWorkbookWithSalary(effectiveYear);
        return workbookResponse(body, "Полная нагрузка с ЗП " + effectiveYear + " " + LocalDate.now() + ".xlsx");
    }

    private ResponseEntity<byte[]> workbookResponse(byte[] body, String fileName) {
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

    private ManualLoadBulkRequest parseBulkRequest(JsonNode body) {
        if (body == null || body.isNull()) {
            return new ManualLoadBulkRequest();
        }
        if (body.isArray()) {
            ManualLoadBulkRequest request = new ManualLoadBulkRequest();
            request.setRows(objectMapper.convertValue(body, new TypeReference<List<ManualLoadEntryRequest>>() {}));
            return request;
        }
        return objectMapper.convertValue(body, ManualLoadBulkRequest.class);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private void validateLoadAccess(SessionUser user, List<ManualLoadEntryRequest> requests) {
        validateLoadAccess(user, null, requests);
    }

    private void validateLoadAccess(SessionUser user, ManualLoadBulkRequest bulkRequest, List<ManualLoadEntryRequest> requests) {
        if (user.isAdmin()) {
            return;
        }
        Stream<String> rowBuildings = (requests == null ? List.<ManualLoadEntryRequest>of() : requests).stream()
                .map(ManualLoadEntryRequest::getNumberSchoolBuilding);
        Stream<String> scopeBuilding = bulkRequest == null ? Stream.empty() : Stream.of(bulkRequest.getNumberSchoolBuilding());
        Set<String> forbiddenBuildings = Stream.concat(rowBuildings, scopeBuilding)
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
