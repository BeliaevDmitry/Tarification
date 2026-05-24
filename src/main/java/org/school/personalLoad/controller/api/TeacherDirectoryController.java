package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherDismissRequest;
import org.school.personalLoad.dto.TeacherPlannedDismissRequest;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
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

    @GetMapping({"/template", "/export"})
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = teacherDirectoryService.buildImportTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teachers-template.xlsx")
                .body(resource);
    }

    @PostMapping
    public ResponseEntity<TeacherDirectoryEntry> create(@RequestBody TeacherCreateRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.create(request));
    }

    @PatchMapping("/{teacherId}")
    public ResponseEntity<TeacherDirectoryEntry> update(@PathVariable Long teacherId,
                                                        @RequestBody TeacherUpdateRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.update(teacherId, request));
    }

    @PatchMapping("/{teacherId}/dismiss")
    public ResponseEntity<TeacherDirectoryEntry> markForDismissal(@PathVariable Long teacherId,
                                                                  @RequestBody TeacherDismissRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.markForDismissal(teacherId, request.getDismissalDate()));
    }

    @PatchMapping("/{teacherId}/plan-dismiss")
    public ResponseEntity<TeacherDirectoryEntry> markPlannedDismissal(@PathVariable Long teacherId,
                                                                       @RequestBody TeacherPlannedDismissRequest request,
                                                                       HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        return ResponseEntity.ok(teacherDirectoryService.markPlannedDismissal(
                teacherId,
                request.getPlannedDismissalDate(),
                request.getComment(),
                user.getFullName()
        ));
    }


    @PatchMapping("/{teacherId}/restore")
    public ResponseEntity<TeacherDirectoryEntry> restore(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherDirectoryService.restore(teacherId));
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
