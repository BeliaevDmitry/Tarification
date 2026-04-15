package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.SubjectArea;
import org.school.personalLoad.repository.SubjectAreaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/subject-areas")
@RequiredArgsConstructor
public class SubjectAreaController {

    private final SubjectAreaRepository repository;

    private static final List<String> DEFAULTS = Arrays.asList(
            "Русский язык и литература",
            "Иностранные языки",
            "Математика и информатика",
            "Общественно-научные предметы",
            "Основы духовно-нравственной культуры народов России",
            "Естественно-научные предметы",
            "Искусство",
            "Технология",
            "Физическая культура и основы безопасности и защиты Родины"
    );

    @GetMapping
    public ResponseEntity<List<SubjectArea>> findAll() {
        ensureDefaults();
        return ResponseEntity.ok(repository.findAll().stream().sorted((a,b)->a.getName().compareToIgnoreCase(b.getName())).toList());
    }

    @PostMapping
    public ResponseEntity<SubjectArea> upsert(@RequestBody SubjectArea request) {
        String name = request == null || request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank()) throw new IllegalArgumentException("name is required");
        SubjectArea area = repository.findByNameIgnoreCase(name).orElseGet(SubjectArea::new);
        area.setName(name);
        return ResponseEntity.ok(repository.save(area));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void ensureDefaults() {
        if (repository.count() > 0) return;
        DEFAULTS.forEach(name -> {
            SubjectArea area = new SubjectArea();
            area.setName(name);
            repository.save(area);
        });
    }
}
