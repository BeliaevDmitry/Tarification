package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.service.ManualLoadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manual-load")
@RequiredArgsConstructor
public class ManualLoadController {

    private final ManualLoadService manualLoadService;

    @PostMapping
    public ResponseEntity<ManualLoadEntry> create(@RequestBody ManualLoadEntryRequest request) {
        return ResponseEntity.ok(manualLoadService.create(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ManualLoadEntry>> createBulk(@RequestBody List<ManualLoadEntryRequest> requests) {
        return ResponseEntity.ok(manualLoadService.createBulk(requests));
    }

    @GetMapping
    public ResponseEntity<List<ManualLoadEntry>> findAll() {
        return ResponseEntity.ok(manualLoadService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        manualLoadService.clearAll();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process() {
        int processed = manualLoadService.processCurrentManualLoad();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "processed", processed
        ));
    }
}
