package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.service.CurriculumPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;

    @PostMapping
    public ResponseEntity<CurriculumPlanEntry> upsert(@RequestBody CurriculumPlanEntryRequest request) {
        return ResponseEntity.ok(curriculumPlanService.upsert(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<CurriculumPlanEntry>> upsertBulk(@RequestBody List<CurriculumPlanEntryRequest> requests) {
        return ResponseEntity.ok(curriculumPlanService.upsertBulk(requests));
    }

    @GetMapping
    public ResponseEntity<List<CurriculumPlanEntry>> findAll() {
        return ResponseEntity.ok(curriculumPlanService.findAll());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        curriculumPlanService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
