package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/building-groups")
@RequiredArgsConstructor
public class BuildingGroupController {

    private final BuildingGroupRepository buildingGroupRepository;

    @GetMapping
    public ResponseEntity<List<BuildingGroup>> findAll() {
        return ResponseEntity.ok(buildingGroupRepository.findAll());
    }
}
