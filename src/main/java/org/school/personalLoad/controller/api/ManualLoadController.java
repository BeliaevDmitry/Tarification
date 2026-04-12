package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.service.ManualLoadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;

    @PostMapping
    public ResponseEntity<ManualLoadEntry> create(@RequestParam(required = false) String academicYear,
                                                  @RequestBody ManualLoadEntryRequest request, HttpServletRequest httpServletRequest) {
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), List.of(request));
        return ResponseEntity.ok(manualLoadService.create(academicYear, request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestParam(required = false) String academicYear,
                                                            @RequestBody List<ManualLoadEntryRequest> requests,
                                                            HttpServletRequest httpServletRequest) {
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), requests);
        return ResponseEntity.ok(manualLoadService.createBulk(academicYear, requests));
    }

    @GetMapping
    public ResponseEntity<List<ManualLoadEntry>> findAll(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(manualLoadService.findAll(academicYear));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestParam(required = false) String academicYear, HttpServletRequest httpServletRequest) {
        validateGlobalLoadOperation(AuthSessionUtils.requiredUser(httpServletRequest));
        manualLoadService.clearAll(academicYear);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<ManualLoadProcessResult> process(@RequestParam(required = false) String academicYear,
                                                           HttpServletRequest httpServletRequest) {
        validateGlobalLoadOperation(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(manualLoadService.processCurrentManualLoad(academicYear));
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
