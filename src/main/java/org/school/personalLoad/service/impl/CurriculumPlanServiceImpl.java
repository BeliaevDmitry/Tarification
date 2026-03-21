package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectCatalogType;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CurriculumPlanServiceImpl implements CurriculumPlanService {

    private final CurriculumPlanEntryRepository repository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SubjectCatalogRepository subjectCatalogRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final CurriculumExcelImportParser curriculumExcelImportParser;

    @Override
    public CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        String normalizedClassName = ClassNameNormalizer.normalize(request.getClassName());

        CurriculumPlanEntry entity = repository
                .findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPart(
                        request.getNumberSchoolBuilding().trim(),
                        normalizedClassName,
                        request.getSubjectName().trim(),
                        request.getEducationLevel(),
                        curriculumPart
                )
                .orElseGet(CurriculumPlanEntry::new);
        boolean creating = entity.getId() == null;
        CurriculumPlanEntry oldValue = creating ? null : entitySnapshot(entity);
        applyEditableFields(entity, request, normalizedClassName, curriculumPart);
        CurriculumPlanEntry saved = repository.save(entity);
        auditService.log(creating ? ActionType.CREATE : ActionType.UPDATE, "Curriculum", saved.getId(), oldValue, saved, creating ? "Curriculum entry created" : "Curriculum entry updated");
        return saved;
    }

    @Override
    public List<CurriculumPlanEntry> upsertBulk(List<CurriculumPlanEntryRequest> requests) {
        List<CurriculumPlanEntry> result = new ArrayList<>();
        for (CurriculumPlanEntryRequest request : requests) {
            result.add(upsert(request));
        }
        return result;
    }

    @Override
    public CurriculumImportResult importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<CurriculumExcelImportParser.ImportedCurriculumRow> importedRows = curriculumExcelImportParser.parse(workbook);
            if (importedRows.isEmpty()) {
                throw new IllegalArgumentException("В Excel-файле учебного плана не найдено ни одной корректной строки для импорта");
            }

            Set<String> importedKeys = new HashSet<>();
            Set<String> importedAcademicYears = new HashSet<>();
            Set<String> importedStages = new HashSet<>();
            Set<String> importedSubjects = new HashSet<>();
            int created = 0;
            int updated = 0;
            int classesCreated = 0;

            for (CurriculumExcelImportParser.ImportedCurriculumRow importedRow : importedRows) {
                importedAcademicYears.add(importedRow.academicYear());
                importedStages.add(importedRow.stage());
                importedKeys.add(importKey(importedRow.academicYear(), importedRow.stage(), importedRow.className(), importedRow.subjectName(), importedRow.studyPeriod()));

                CurriculumPlanEntry entity = repository
                        .findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(
                                importedRow.academicYear(),
                                importedRow.stage(),
                                importedRow.className(),
                                importedRow.subjectName(),
                                importedRow.studyPeriod()
                        )
                        .orElseGet(CurriculumPlanEntry::new);

                boolean creating = entity.getId() == null;
                CurriculumPlanEntry oldValue = creating ? null : entitySnapshot(entity);
                entity.setAcademicYear(importedRow.academicYear());
                entity.setStage(importedRow.stage());
                entity.setStudyPeriod(importedRow.studyPeriod());
                entity.setNumberSchoolBuilding("СП0");
                entity.setClassName(importedRow.className());
                entity.setSubjectName(importedRow.subjectName());
                entity.setPlannedHours(toPlannedHours(importedRow.plannedHours()));
                entity.setEducationLevel(EducationLevel.BASIC);
                entity.setSubgroupRequired(false);
                entity.setSubgroupCount(0);
                entity.setSubgroup1Hours(null);
                entity.setSubgroup1EducationLevel(null);
                entity.setSubgroup2Hours(null);
                entity.setSubgroup2EducationLevel(null);
                entity.setCurriculumPart(importedRow.curriculumPart());
                entity.setDeprecated(false);
                CurriculumPlanEntry saved = repository.save(entity);
                auditService.log(creating ? ActionType.CREATE : ActionType.UPDATE, "Curriculum", saved.getId(), oldValue, saved, creating ? "Curriculum imported from Excel" : "Curriculum updated from Excel");
                if (creating) {
                    created++;
                } else {
                    updated++;
                }

                if (ensureClassExists(importedRow)) {
                    classesCreated++;
                }
                if (ensureSubjectExists(importedRow.subjectName(), importedRow.curriculumPart())) {
                    importedSubjects.add(importedRow.subjectName());
                }
            }

            int deprecated = deprecateMissingCurriculum(importedAcademicYears, importedStages, importedKeys);
            int orphanedLoads = recalculateOrphanedLoads();
            return new CurriculumImportResult(created, updated, deprecated, classesCreated, orphanedLoads, importedSubjects.size());
        } catch (IllegalArgumentException e) {
            log.warn("Импорт учебного плана отклонён: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка импорта учебного плана из Excel", e);
            throw new IllegalArgumentException("Не удалось импортировать учебный план из Excel: " + rootCauseMessage(e), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurriculumPlanEntry> findAll() {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            return buildingRepository.findByHeadUserId(userId)
                    .map(building -> repository.findAllByNumberSchoolBuildingAndDeprecatedFalse(building.getCode()))
                    .orElse(List.of());
        }
        return repository.findAllByDeprecatedFalse();
    }

    @Override
    public void clearAll() {
        List<CurriculumPlanEntry> oldValue = repository.findAll();
        repository.deleteAll();
        auditService.log(ActionType.DELETE, "Curriculum", null, oldValue, null, "All curriculum entries removed");
    }

    @Override
    public CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPlanEntry entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum entry not found: " + id));
        CurriculumPlanEntry oldValue = entitySnapshot(entity);
        applyEditableFields(entity, request, ClassNameNormalizer.normalize(request.getClassName()), request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart());
        CurriculumPlanEntry saved = repository.save(entity);
        auditService.log(ActionType.UPDATE, "Curriculum", saved.getId(), oldValue, saved, "Curriculum entry updated");
        return saved;
    }

    @Override
    public void deleteById(Long id) {
        CurriculumPlanEntry entry = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum entry not found: " + id));
        repository.delete(entry);
        auditService.log(ActionType.DELETE, "Curriculum", id, entry, null, "Curriculum entry deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurriculumPlanEntry> findRule(String numberSchoolBuilding, String className, String subjectName, EducationLevel educationLevel) {
        return repository.findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevel(
                numberSchoolBuilding,
                ClassNameNormalizer.normalize(className),
                subjectName,
                educationLevel
        ).filter(entry -> !entry.isDeprecated());
    }

    private void applyEditableFields(CurriculumPlanEntry entity, CurriculumPlanEntryRequest request, String normalizedClassName, CurriculumPart curriculumPart) {
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(normalizedClassName);
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? request.getSubgroupCount() : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setSubgroup1Hours(request.isSubgroupRequired() ? request.getSubgroup1Hours() : null);
        entity.setSubgroup1EducationLevel(request.isSubgroupRequired() ? request.getSubgroup1EducationLevel() : null);
        entity.setSubgroup2Hours(request.isSubgroupRequired() ? request.getSubgroup2Hours() : null);
        entity.setSubgroup2EducationLevel(request.isSubgroupRequired() ? request.getSubgroup2EducationLevel() : null);
        entity.setCurriculumPart(curriculumPart);
        entity.setDeprecated(false);
        if (entity.getStudyPeriod() == null) {
            entity.setStudyPeriod(StudyPeriod.YEAR);
        }
        if (entity.getAcademicYear() == null) {
            entity.setAcademicYear("");
        }
        if (entity.getStage() == null) {
            entity.setStage("");
        }
    }

    private CurriculumPlanEntry entitySnapshot(CurriculumPlanEntry entity) {
        CurriculumPlanEntry snapshot = new CurriculumPlanEntry();
        snapshot.setId(entity.getId());
        snapshot.setNumberSchoolBuilding(entity.getNumberSchoolBuilding());
        snapshot.setClassName(entity.getClassName());
        snapshot.setSubjectName(entity.getSubjectName());
        snapshot.setPlannedHours(entity.getPlannedHours());
        snapshot.setSubgroupRequired(entity.isSubgroupRequired());
        snapshot.setSubgroupCount(entity.getSubgroupCount());
        snapshot.setEducationLevel(entity.getEducationLevel());
        snapshot.setSubgroup1Hours(entity.getSubgroup1Hours());
        snapshot.setSubgroup1EducationLevel(entity.getSubgroup1EducationLevel());
        snapshot.setSubgroup2Hours(entity.getSubgroup2Hours());
        snapshot.setSubgroup2EducationLevel(entity.getSubgroup2EducationLevel());
        snapshot.setCurriculumPart(entity.getCurriculumPart());
        snapshot.setAcademicYear(entity.getAcademicYear());
        snapshot.setStage(entity.getStage());
        snapshot.setStudyPeriod(entity.getStudyPeriod());
        snapshot.setDeprecated(entity.isDeprecated());
        snapshot.setCreatedAt(entity.getCreatedAt());
        return snapshot;
    }

    private boolean ensureClassExists(CurriculumExcelImportParser.ImportedCurriculumRow importedRow) {
        String buildingCode = "СП0";
        String className = ClassNameNormalizer.normalize(importedRow.className());
        Optional<ClassroomLeadershipEntry> existing = classroomLeadershipRepository.findByNumberSchoolBuildingAndClassName(buildingCode, className);
        if (existing.isPresent()) {
            return false;
        }
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setNumberSchoolBuilding(buildingCode);
        entry.setClassName(className);
        entry.setClassDirection(importedRow.classDirection() == null || importedRow.classDirection().isBlank() ? "Не указана" : importedRow.classDirection());
        entry.setFioTeacher(resolveTeacher(importedRow.classTeacher()));
        classroomLeadershipRepository.save(entry);
        auditService.log(ActionType.CREATE, "ClassroomLeadership", entry.getId(), null, entry, "Classroom created during curriculum import");
        return true;
    }

    private boolean ensureSubjectExists(String subjectName, CurriculumPart curriculumPart) {
        String normalizedSubject = subjectName == null ? "" : subjectName.trim();
        if (normalizedSubject.isBlank()) {
            return false;
        }
        if (subjectCatalogRepository.findBySubjectNameIgnoreCase(normalizedSubject).isPresent()) {
            return false;
        }
        SubjectCatalogEntry entry = new SubjectCatalogEntry();
        entry.setSubjectName(normalizedSubject);
        entry.setSubjectType(curriculumPart == CurriculumPart.EXTRACURRICULAR ? SubjectCatalogType.EXTRACURRICULAR : SubjectCatalogType.CORE_FORMABLE);
        subjectCatalogRepository.save(entry);
        auditService.log(ActionType.CREATE, "SubjectCatalog", entry.getId(), null, entry, "Subject added during curriculum import");
        return true;
    }

    private int deprecateMissingCurriculum(Set<String> academicYears, Set<String> stages, Set<String> importedKeys) {
        if (academicYears.isEmpty() || stages.isEmpty()) {
            return 0;
        }
        int deprecatedCount = 0;
        List<CurriculumPlanEntry> existingEntries = repository.findAllByAcademicYearInAndStageInAndDeprecatedFalse(
                new ArrayList<>(academicYears),
                new ArrayList<>(stages)
        );
        for (CurriculumPlanEntry entry : existingEntries) {
            String key = importKey(entry.getAcademicYear(), entry.getStage(), entry.getClassName(), entry.getSubjectName(), entry.getStudyPeriod());
            if (!importedKeys.contains(key)) {
                CurriculumPlanEntry oldValue = entitySnapshot(entry);
                entry.setDeprecated(true);
                repository.save(entry);
                auditService.log(ActionType.UPDATE, "Curriculum", entry.getId(), oldValue, entry, "Curriculum deprecated after import");
                deprecatedCount++;
            }
        }
        return deprecatedCount;
    }

    private int recalculateOrphanedLoads() {
        int orphanedCount = 0;
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAll();
        for (ManualLoadEntry entry : entries) {
            boolean activeRuleExists = repository.findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevel(
                    entry.getNumberSchoolBuilding(),
                    ClassNameNormalizer.normalize(entry.getClassName()),
                    entry.getSubjectName(),
                    entry.getEducationLevel()
            ).filter(rule -> !rule.isDeprecated()).isPresent();
            entry.setOrphaned(!activeRuleExists);
            manualLoadEntryRepository.save(entry);
            if (entry.isOrphaned()) {
                orphanedCount++;
            }
        }
        return orphanedCount;
    }

    private String resolveTeacher(String importedTeacher) {
        String normalized = importedTeacher == null ? "" : importedTeacher.trim();
        if (!normalized.isBlank() && teacherDirectoryRepository.findByFioTeacher(normalized).isPresent()) {
            return normalized;
        }
        return teacherDirectoryRepository.findAll().stream()
                .map(entry -> entry.getFioTeacher())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Не назначен");
    }

    private Integer toPlannedHours(Double hours) {
        return (int) Math.round(hours == null ? 0D : hours);
    }

    private String importKey(String academicYear, String stage, String className, String subjectName, StudyPeriod studyPeriod) {
        return String.join("|",
                academicYear == null ? "" : academicYear,
                stage == null ? "" : stage,
                className == null ? "" : className,
                subjectName == null ? "" : subjectName,
                studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()
        );
        for (CurriculumPlanEntry entry : existingEntries) {
            String key = importKey(entry.getAcademicYear(), entry.getStage(), entry.getClassName(), entry.getSubjectName(), entry.getStudyPeriod());
            if (!importedKeys.contains(key)) {
                CurriculumPlanEntry oldValue = entitySnapshot(entry);
                entry.setDeprecated(true);
                repository.save(entry);
                auditService.log(ActionType.UPDATE, "Curriculum", entry.getId(), oldValue, entry, "Curriculum deprecated after import");
                deprecatedCount++;
            }
        }
        return deprecatedCount;
    }

    private int recalculateOrphanedLoads() {
        int orphanedCount = 0;
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAll();
        for (ManualLoadEntry entry : entries) {
            boolean activeRuleExists = repository.findFirstByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevel(
                    entry.getNumberSchoolBuilding(),
                    ClassNameNormalizer.normalize(entry.getClassName()),
                    entry.getSubjectName(),
                    entry.getEducationLevel()
            ).filter(rule -> !rule.isDeprecated()).isPresent();
            entry.setOrphaned(!activeRuleExists);
            manualLoadEntryRepository.save(entry);
            if (entry.isOrphaned()) {
                orphanedCount++;
            }
        }
        return orphanedCount;
    }

    private String resolveTeacher(String importedTeacher) {
        String normalized = importedTeacher == null ? "" : importedTeacher.trim();
        if (!normalized.isBlank() && teacherDirectoryRepository.findByFioTeacher(normalized).isPresent()) {
            return normalized;
        }
        return teacherDirectoryRepository.findAll().stream()
                .map(entry -> entry.getFioTeacher())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Не назначен");
    }

    private String normalizeBuildingCode(String currentValue) {
        return currentValue == null || currentValue.isBlank() ? "СП0" : currentValue.trim();
    }

    private Integer toPlannedHours(Double hours) {
        return (int) Math.round(hours == null ? 0D : hours);
    }

    private String importKey(String academicYear, String stage, String className, String subjectName, StudyPeriod studyPeriod) {
        return String.join("|",
                academicYear == null ? "" : academicYear,
                stage == null ? "" : stage,
                className == null ? "" : className,
                subjectName == null ? "" : subjectName,
                studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()
        );
    }

    private void applyEditableFields(CurriculumPlanEntry entity, CurriculumPlanEntryRequest request, String normalizedClassName, CurriculumPart curriculumPart) {
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(normalizedClassName);
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setSubgroupRequired(request.isSubgroupRequired());
        entity.setSubgroupCount(request.isSubgroupRequired() ? request.getSubgroupCount() : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setSubgroup1Hours(request.isSubgroupRequired() ? request.getSubgroup1Hours() : null);
        entity.setSubgroup1EducationLevel(request.isSubgroupRequired() ? request.getSubgroup1EducationLevel() : null);
        entity.setSubgroup2Hours(request.isSubgroupRequired() ? request.getSubgroup2Hours() : null);
        entity.setSubgroup2EducationLevel(request.isSubgroupRequired() ? request.getSubgroup2EducationLevel() : null);
        entity.setCurriculumPart(curriculumPart);
    }

    private CurriculumPlanEntry entitySnapshot(CurriculumPlanEntry entity) {
        CurriculumPlanEntry snapshot = new CurriculumPlanEntry();
        snapshot.setId(entity.getId());
        snapshot.setNumberSchoolBuilding(entity.getNumberSchoolBuilding());
        snapshot.setClassName(entity.getClassName());
        snapshot.setSubjectName(entity.getSubjectName());
        snapshot.setPlannedHours(entity.getPlannedHours());
        snapshot.setSubgroupRequired(entity.isSubgroupRequired());
        snapshot.setSubgroupCount(entity.getSubgroupCount());
        snapshot.setEducationLevel(entity.getEducationLevel());
        snapshot.setSubgroup1Hours(entity.getSubgroup1Hours());
        snapshot.setSubgroup1EducationLevel(entity.getSubgroup1EducationLevel());
        snapshot.setSubgroup2Hours(entity.getSubgroup2Hours());
        snapshot.setSubgroup2EducationLevel(entity.getSubgroup2EducationLevel());
        snapshot.setCurriculumPart(entity.getCurriculumPart());
        snapshot.setCreatedAt(entity.getCreatedAt());
        return snapshot;
    }

    private void validate(CurriculumPlanEntryRequest request) {
        if (request.getNumberSchoolBuilding() == null || request.getNumberSchoolBuilding().isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getPlannedHours() == null || request.getPlannedHours() <= 0) {
            throw new IllegalArgumentException("plannedHours must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required");
        }
        if (request.isSubgroupRequired() && (request.getSubgroupCount() == null || request.getSubgroupCount() < 2)) {
            throw new IllegalArgumentException("subgroupCount must be >= 2 when subgroupRequired=true");
        }
        if (request.isSubgroupRequired()) {
            if (request.getSubgroup1Hours() == null || request.getSubgroup1Hours() <= 0) {
                throw new IllegalArgumentException("subgroup1Hours must be > 0 when subgroupRequired=true");
            }
            if (request.getSubgroup2Hours() == null || request.getSubgroup2Hours() <= 0) {
                throw new IllegalArgumentException("subgroup2Hours must be > 0 when subgroupRequired=true");
            }
            if (request.getSubgroup1EducationLevel() == null || request.getSubgroup2EducationLevel() == null) {
                throw new IllegalArgumentException("subgroup levels are required when subgroupRequired=true");
            }
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

}
