package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classroom-leadership")
@RequiredArgsConstructor
public class ClassroomLeadershipController {

    private final ClassroomLeadershipService classroomLeadershipService;

    @PutMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> replaceAll(@RequestBody List<ClassroomLeadershipEntryRequest> requests) {
        return ResponseEntity.ok(classroomLeadershipService.replaceAll(requests));
    }

    @GetMapping
    public ResponseEntity<List<ClassroomLeadershipEntry>> findAll() {
        return ResponseEntity.ok(classroomLeadershipService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        classroomLeadershipService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
