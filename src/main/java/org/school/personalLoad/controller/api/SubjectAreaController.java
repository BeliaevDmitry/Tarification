package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.SubjectArea;
import org.school.personalLoad.model.SubjectAreaNames;
import org.school.personalLoad.repository.SubjectAreaRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/subject-areas")
@RequiredArgsConstructor
public class SubjectAreaController {

    private final SubjectAreaRepository repository;

    @GetMapping
    public ResponseEntity<List<SubjectArea>> findAll() {
        ensureDefaults();
        List<SubjectArea> baseAreas = SubjectAreaNames.BASE_AREAS.stream()
                .map(name -> repository.findByNameIgnoreCase(name).orElseGet(() -> createBaseArea(name)))
                .toList();
        return ResponseEntity.ok(baseAreas);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<SubjectArea> upsert(@RequestBody SubjectArea request) {
        throw new IllegalArgumentException("Предметные области фиксированы: используйте одну из 9 базовых областей");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        throw new IllegalArgumentException("Предметные области фиксированы и не удаляются");
    }

    private void ensureDefaults() {
        SubjectAreaNames.BASE_AREAS.forEach(name -> repository.findByNameIgnoreCase(name).orElseGet(() -> createBaseArea(name)));
    }

    private SubjectArea createBaseArea(String name) {
        SubjectArea area = new SubjectArea();
        area.setName(name);
        return repository.save(area);
    }
}
