package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final AppConfig appConfig;

    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> mode() {
        boolean legacyMode = appConfig.isLegacyModeEnabled();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "mode", legacyMode ? "legacy-file-pipeline" : "api-frontend",
                "legacyModeEnabled", legacyMode
        ));
    }
}
