package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ClassroomBuildingScopeUpdateRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomLeadershipService classroomLeadershipService;

    @PatchMapping("/{id}/building-scope")
    public ResponseEntity<ClassroomLeadershipEntry> updateBuildingScope(@PathVariable Long id,
                                                                        @RequestBody ClassroomBuildingScopeUpdateRequest request) {
        return ResponseEntity.ok(classroomLeadershipService.updateBuildingScope(id, request));
    }
}
