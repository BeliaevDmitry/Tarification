package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.BuildingGroupCreateRequest;
import org.school.personalLoad.dto.BuildingGroupCreateResponse;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.service.BuildingGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/building-groups")
@RequiredArgsConstructor
public class BuildingGroupController {

    private final BuildingGroupService buildingGroupService;

    @GetMapping
    public ResponseEntity<List<BuildingGroup>> findAll() {
        return ResponseEntity.ok(buildingGroupService.findAll());
    }

    @PostMapping
    public ResponseEntity<BuildingGroupCreateResponse> create(@RequestBody BuildingGroupCreateRequest request) {
        return ResponseEntity.ok(buildingGroupService.createWithInitialSite(request));
    }
}
