package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RestController
@RequestMapping("/api/meta-groups")
@RequiredArgsConstructor
public class MetaGroupController {

    private final MetaGroupRepository repository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    @GetMapping
    public ResponseEntity<List<MetaGroup>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<MetaGroup> create(@RequestBody CreateMetaGroupRequest request) {
        String building = normalizeBuilding(request.getNumberSchoolBuilding());
        Integer parallel = normalizeParallel(request.getParallel());
        String name = normalizeName(parallel, request.getName());
        String classType = normalizeClassType(request.getClassType());

        if (repository.existsByNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(building, parallel, name, classType)) {
            throw new IllegalArgumentException("Метагруппа уже существует");
        }
        MetaGroup entity = new MetaGroup();
        entity.setNumberSchoolBuilding(building);
        entity.setParallel(parallel);
        entity.setName(name);
        entity.setClassType(classType);
        entity.setStudyPeriodSettingId(request.getStudyPeriodSettingId());
        return ResponseEntity.ok(repository.save(entity));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<MetaGroup> update(@PathVariable Long id, @RequestBody UpdateMetaGroupRequest request) {
        MetaGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена"));
        Integer parallel = normalizeParallel(request.getParallel() == null ? existing.getParallel() : request.getParallel());
        String building = normalizeBuilding(request.getNumberSchoolBuilding() == null ? existing.getNumberSchoolBuilding() : request.getNumberSchoolBuilding());
        String classType = normalizeClassType(request.getClassType() == null ? existing.getClassType() : request.getClassType());
        String newName = normalizeName(parallel, request.getName() == null ? existing.getName() : request.getName());
        if (!existing.getName().equalsIgnoreCase(newName)
                && repository.existsByNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(building, parallel, newName, classType)) {
            throw new IllegalArgumentException("Метагруппа уже существует");
        }

        String oldClassName = asMetaGroupClassName(existing.getName());
        String newClassName = asMetaGroupClassName(newName);
        List<CurriculumPlanEntry> entries = curriculumPlanEntryRepository
                .findAllByNumberSchoolBuildingAndClassName(building, oldClassName);
        for (CurriculumPlanEntry entry : entries) {
            entry.setClassName(newClassName);
            entry.setStudyPeriodSettingId(request.getStudyPeriodSettingId() != null ? request.getStudyPeriodSettingId() : existing.getStudyPeriodSettingId());
        }
        curriculumPlanEntryRepository.saveAll(entries);

        existing.setNumberSchoolBuilding(building);
        existing.setParallel(parallel);
        existing.setName(newName);
        existing.setClassType(classType);
        if (request.getStudyPeriodSettingId() != null) existing.setStudyPeriodSettingId(request.getStudyPeriodSettingId());
        return ResponseEntity.ok(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        MetaGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена"));
        curriculumPlanEntryRepository.deleteByNumberSchoolBuildingAndClassName(existing.getNumberSchoolBuilding(), asMetaGroupClassName(existing.getName()));
        repository.delete(existing);
        return ResponseEntity.noContent().build();
    }

    private String normalizeBuilding(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        return value.trim();
    }

    private Integer normalizeParallel(Integer value) {
        if (value == null || value < 1 || value > 11) {
            throw new IllegalArgumentException("parallel must be 1..11");
        }
        return value;
    }

    private String normalizeName(Integer parallel, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String name = value.trim().replace('\u00A0', ' ').replaceAll("\\s+", " ").toUpperCase();
        String prefix = parallel + " ";
        if (name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

    private String asMetaGroupClassName(String name) {
        return "МГ:" + name;
    }

    private String normalizeClassType(String value) {
        if (value == null || value.isBlank()) return "NORMAL";
        String normalized = value.trim().toUpperCase().replace('Ё', 'Е');
        if (normalized.contains("АООП") || normalized.contains("УО") || normalized.contains("AOOP")) return "AOOP_UO";
        return "NORMAL";
    }

    @Value
    public static class CreateMetaGroupRequest {
        String numberSchoolBuilding;
        Integer parallel;
        String name;
        String classType;
        Long studyPeriodSettingId;
    }

    @Value
    public static class UpdateMetaGroupRequest {
        String numberSchoolBuilding;
        Integer parallel;
        String name;
        String classType;
        Long studyPeriodSettingId;
    }
}
