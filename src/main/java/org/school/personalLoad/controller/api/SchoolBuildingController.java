package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.service.SchoolBuildingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class SchoolBuildingController {

    private final SchoolBuildingService schoolBuildingService;

    @PostMapping
    public ResponseEntity<SchoolBuilding> upsert(@RequestBody SchoolBuildingRequest request) {
        return ResponseEntity.ok(schoolBuildingService.upsert(request));
    }

    @GetMapping
    public ResponseEntity<List<SchoolBuilding>> findAll() {
        return ResponseEntity.ok(schoolBuildingService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        schoolBuildingService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
