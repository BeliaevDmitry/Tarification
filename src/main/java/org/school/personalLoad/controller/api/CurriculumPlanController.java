package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.service.CurriculumPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<CurriculumPlanEntry> upsert(@RequestBody CurriculumPlanEntryRequest request) {
        return ResponseEntity.ok(curriculumPlanService.upsert(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<List<CurriculumPlanEntry>> upsertBulk(@RequestBody List<CurriculumPlanEntryRequest> requests) {
        return ResponseEntity.ok(curriculumPlanService.upsertBulk(requests));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CurriculumPlanEntry>> findAll() {
        return ResponseEntity.ok(curriculumPlanService.findAll());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<CurriculumPlanEntry> updateById(@PathVariable Long id, @RequestBody CurriculumPlanEntryRequest request) {
        return ResponseEntity.ok(curriculumPlanService.updateById(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        curriculumPlanService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<Void> clearAll() {
        curriculumPlanService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
