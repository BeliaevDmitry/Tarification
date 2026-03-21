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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;

    @PostMapping
    public ResponseEntity<ManualLoadEntry> create(@RequestBody ManualLoadEntryRequest request, HttpServletRequest httpServletRequest) {
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), List.of(request));
        return ResponseEntity.ok(manualLoadService.create(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestBody List<ManualLoadEntryRequest> requests,
                                                            HttpServletRequest httpServletRequest) {
        validateLoadAccess(AuthSessionUtils.requiredUser(httpServletRequest), requests);
        return ResponseEntity.ok(manualLoadService.createBulk(requests));
    }

    @GetMapping
    public ResponseEntity<List<ManualLoadEntry>> findAll() {
        return ResponseEntity.ok(manualLoadService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        if (user.getRole() == org.school.personalLoad.auth.UserRole.BUILDING_HEAD) {
            throw new ForbiddenException("Руководитель корпуса может редактировать только нагрузку своего корпуса");
        }
        manualLoadService.clearAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<ManualLoadProcessResult> process(HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        if (user.getRole() == org.school.personalLoad.auth.UserRole.BUILDING_HEAD) {
            throw new ForbiddenException("Руководитель корпуса может редактировать только нагрузку своего корпуса");
        }
        return ResponseEntity.ok(manualLoadService.processCurrentManualLoad());
    }

    private void validateLoadAccess(SessionUser user, List<ManualLoadEntryRequest> requests) {
        if (user.isAdmin() || user.getRole() != org.school.personalLoad.auth.UserRole.BUILDING_HEAD) {
            return;
        }
        Set<String> forbiddenBuildings = requests.stream()
                .map(ManualLoadEntryRequest::getNumberSchoolBuilding)
                .filter(buildingCode -> !user.canEditLoadBuilding(buildingCode))
                .collect(Collectors.toSet());
        if (!forbiddenBuildings.isEmpty()) {
            throw new ForbiddenException("Руководитель корпуса может редактировать только нагрузку своего корпуса: " + String.join(", ", forbiddenBuildings));
        }
    }
}
