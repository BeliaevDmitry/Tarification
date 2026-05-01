package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ServiceMemoSettingsDto;
import org.school.personalLoad.service.ServiceMemoSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/service-memos/settings")
@RequiredArgsConstructor
public class ServiceMemoSettingsController {
    private final ServiceMemoSettingsService service;

    @GetMapping
    public ResponseEntity<ServiceMemoSettingsDto> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<ServiceMemoSettingsDto> update(@RequestBody ServiceMemoSettingsDto dto) {
        return ResponseEntity.ok(service.update(dto));
    }
}
