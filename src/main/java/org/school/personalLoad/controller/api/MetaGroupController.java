package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.service.AcademicYearService;
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
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<List<MetaGroup>> findAll(@RequestParam(required = false) String academicYear) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(repository.findAllByAcademicYearOrderByNumberSchoolBuildingAscParallelAscNameAsc(effectiveYear));
    }

    @PostMapping
    public ResponseEntity<MetaGroup> create(@RequestParam(required = false) String academicYear,
                                            @RequestBody CreateMetaGroupRequest request) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        String building = normalizeBuilding(request.getNumberSchoolBuilding());
        Integer parallel = normalizeParallel(request.getParallel());
        String name = normalizeName(parallel, request.getName());
        String classType = normalizeClassType(request.getClassType());

        if (repository.existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(effectiveYear, building, parallel, name, classType)) {
            throw new IllegalArgumentException("Метагруппа уже существует");
        }
        MetaGroup entity = new MetaGroup();
        entity.setAcademicYear(effectiveYear);
        entity.setNumberSchoolBuilding(building);
        entity.setParallel(parallel);
        entity.setName(name);
        entity.setClassType(classType);
        entity.setStudyPeriodSettingId(request.getStudyPeriodSettingId());
        entity.setSchoolBuilding(resolveRequiredSchoolBuilding(request.getSchoolBuildingId()));
        return ResponseEntity.ok(repository.save(entity));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<MetaGroup> update(@PathVariable Long id,
                                            @RequestParam(required = false) String academicYear,
                                            @RequestBody UpdateMetaGroupRequest request) {
        MetaGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена"));
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        if (!effectiveYear.equals(existing.getAcademicYear())) {
            throw new IllegalArgumentException("Метагруппа относится к другому учебному году");
        }
        Integer parallel = normalizeParallel(request.getParallel() == null ? existing.getParallel() : request.getParallel());
        String building = normalizeBuilding(request.getNumberSchoolBuilding() == null ? existing.getNumberSchoolBuilding() : request.getNumberSchoolBuilding());
        String classType = normalizeClassType(request.getClassType() == null ? existing.getClassType() : request.getClassType());
        String newName = normalizeName(parallel, request.getName() == null ? existing.getName() : request.getName());
        boolean scopeChanged = !existing.getName().equalsIgnoreCase(newName)
                || !existing.getNumberSchoolBuilding().equals(building)
                || !existing.getParallel().equals(parallel)
                || !existing.getClassType().equals(classType);
        if (scopeChanged
                && repository.existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(effectiveYear, building, parallel, newName, classType)) {
            throw new IllegalArgumentException("Метагруппа уже существует");
        }

        List<CurriculumPlanEntry> entries = curriculumPlanEntryRepository.findAllByMetaGroupId(existing.getId())
                .stream()
                .filter(entry -> effectiveYear.equals(entry.getAcademicYear()))
                .toList();
        for (CurriculumPlanEntry entry : entries) {
            entry.setStudyPeriodSettingId(request.getStudyPeriodSettingId() != null ? request.getStudyPeriodSettingId() : existing.getStudyPeriodSettingId());
        }
        curriculumPlanEntryRepository.saveAll(entries);

        existing.setNumberSchoolBuilding(building);
        existing.setParallel(parallel);
        existing.setName(newName);
        existing.setClassType(classType);
        if (request.getStudyPeriodSettingId() != null) existing.setStudyPeriodSettingId(request.getStudyPeriodSettingId());
        if (request.getSchoolBuildingId() != null) {
            existing.setSchoolBuilding(resolveRequiredSchoolBuilding(request.getSchoolBuildingId()));
        } else if (existing.getSchoolBuildingId() == null) {
            throw new IllegalArgumentException("schoolBuildingId is required for meta group");
        }
        return ResponseEntity.ok(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String academicYear) {
        MetaGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена"));
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        if (!effectiveYear.equals(existing.getAcademicYear())) {
            throw new IllegalArgumentException("Метагруппа относится к другому учебному году");
        }
        boolean crossYearCurriculumExists = curriculumPlanEntryRepository.findAllByMetaGroupId(existing.getId())
                .stream()
                .anyMatch(row -> !effectiveYear.equals(row.getAcademicYear()));
        boolean crossYearManualLoadExists = manualLoadEntryRepository.findAllByMetaGroupId(existing.getId())
                .stream()
                .anyMatch(row -> !effectiveYear.equals(row.getAcademicYear()));
        if (crossYearCurriculumExists || crossYearManualLoadExists) {
            throw new IllegalStateException("Метагруппа ошибочно используется в другом учебном году");
        }
        curriculumPlanEntryRepository.deleteByMetaGroupId(existing.getId());
        manualLoadEntryRepository.deleteByMetaGroupId(existing.getId());
        repository.delete(existing);
        return ResponseEntity.noContent().build();
    }

    private SchoolBuilding resolveRequiredSchoolBuilding(Long schoolBuildingId) {
        if (schoolBuildingId == null) {
            throw new IllegalArgumentException("schoolBuildingId is required for meta group");
        }
        return schoolBuildingRepository.findById(schoolBuildingId)
                .orElseThrow(() -> new IllegalArgumentException("Физическая площадка метагруппы не найдена"));
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
        Long schoolBuildingId;
    }

    @Value
    public static class UpdateMetaGroupRequest {
        String numberSchoolBuilding;
        Integer parallel;
        String name;
        String classType;
        Long studyPeriodSettingId;
        Long schoolBuildingId;
    }
}
