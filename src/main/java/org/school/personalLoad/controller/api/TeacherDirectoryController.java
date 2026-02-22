package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherDismissRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherDirectoryController {

    private final TeacherDirectoryService teacherDirectoryService;

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(teacherDirectoryService.importFromExcel(file));
    }

    @PostMapping
    public ResponseEntity<TeacherDirectoryEntry> create(@RequestBody TeacherCreateRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.create(request));
    }

    @PatchMapping("/{teacherId}/dismiss")
    public ResponseEntity<TeacherDirectoryEntry> markForDismissal(@PathVariable Long teacherId,
                                                                  @RequestBody TeacherDismissRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.markForDismissal(teacherId, request.getDismissalDate()));
    }

    @DeleteMapping("/{teacherId}")
    public ResponseEntity<Void> deleteById(@PathVariable Long teacherId) {
        teacherDirectoryService.deleteById(teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<TeacherDirectoryEntry>> findAll() {
        return ResponseEntity.ok(teacherDirectoryService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        teacherDirectoryService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
