package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.SubjectArea;
import org.school.personalLoad.repository.SubjectAreaRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/subject-areas")
@RequiredArgsConstructor
public class SubjectAreaController {

    private final SubjectAreaRepository repository;
    private final SubjectCatalogRepository subjectCatalogRepository;

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
    @Transactional
    public ResponseEntity<SubjectArea> upsert(@RequestBody SubjectArea request) {
        String name = request == null || request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank()) throw new IllegalArgumentException("name is required");

        SubjectArea area = request.getId() == null
                ? repository.findByNameIgnoreCase(name).orElseGet(SubjectArea::new)
                : repository.findById(request.getId()).orElseThrow(() -> new IllegalArgumentException("Предметная область не найдена"));

        repository.findByNameIgnoreCase(name)
                .filter(found -> !Objects.equals(found.getId(), area.getId()))
                .ifPresent(found -> {
                    throw new IllegalArgumentException("Предметная область с таким названием уже существует");
                });

        String oldName = area.getName();
        area.setName(name);
        SubjectArea saved = repository.save(area);
        if (oldName != null && !oldName.equalsIgnoreCase(name)) {
            subjectCatalogRepository.renameSubjectAreaEverywhere(oldName, name);
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        SubjectArea area = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Предметная область не найдена"));
        if (subjectCatalogRepository.existsBySubjectAreaNameIgnoreCase(area.getName())) {
            throw new IllegalArgumentException("Нельзя удалить предметную область: она используется в предметах");
        }
        try {
            repository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Нельзя удалить предметную область: она используется в предметах");
        }
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
