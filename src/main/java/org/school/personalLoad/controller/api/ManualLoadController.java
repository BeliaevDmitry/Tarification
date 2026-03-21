package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.service.ManualLoadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR','BUILDING_HEAD')")
    public ResponseEntity<ManualLoadEntry> create(@RequestBody ManualLoadEntryRequest request) {
        return ResponseEntity.ok(manualLoadService.create(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR','BUILDING_HEAD')")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestBody List<ManualLoadEntryRequest> requests) {
        return ResponseEntity.ok(manualLoadService.createBulk(requests));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ManualLoadEntry>> findAll() {
        return ResponseEntity.ok(manualLoadService.findAll());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<Void> clear() {
        manualLoadService.clearAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<ManualLoadProcessResult> process() {
        return ResponseEntity.ok(manualLoadService.processCurrentManualLoad());
    }
}
