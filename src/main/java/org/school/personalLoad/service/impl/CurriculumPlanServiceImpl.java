package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
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

    private Sheet findSheet(Workbook workbook, List<String> tokens) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName().toLowerCase(Locale.ROOT);
            if (tokens.stream().anyMatch(name::contains)) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private ImportParseResult parseCurriculumWorkbook(Sheet sheet) {
        List<KnownClassRef> knownClasses = loadKnownClasses();
        try {
            return parseRowBasedSheet(sheet, knownClasses);
        } catch (IllegalArgumentException rowBasedError) {
            log.warn("Не удалось распознать построчный импорт УП, пробую матричный формат: {}", rowBasedError.getMessage());
            return parseMatrixSheet(sheet, knownClasses);
        }
    }

    private ImportParseResult parseRowBasedSheet(Sheet sheet, List<KnownClassRef> knownClasses) {
        HeaderLookup headers = detectHeaders(sheet, Map.of(
                "numberSchoolBuilding", List.of("корпус", "корп.", "здание", "building", "numberschoolbuilding"),
                "className", List.of("класс", "класс/группа", "class", "classname"),
                "subjectName", List.of("предмет", "наименование предмета", "subject", "subjectname"),
                "plannedHours", List.of("часы", "часов", "кол-во часов", "количество часов", "всего часов", "недельная нагрузка", "hours", "plannedhours")
        ));

        List<CurriculumPlanEntryRequest> requests = new ArrayList<>();
        int skipped = 0;
        int headerRowIndex = headers.rowIndex();
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String building = normalize(readCell(row, headers.index("numberSchoolBuilding")));
            String className = normalize(readCell(row, headers.index("className")));
            String subjectName = normalize(readCell(row, headers.index("subjectName")));
            String hoursRaw = normalize(readCell(row, headers.index("plannedHours")));
            ClassRef classRef = resolveClassRef(building, className, knownClasses);

            if (classRef.buildingCode().isBlank() && classRef.className().isBlank() && subjectName.isBlank() && hoursRaw.isBlank()) {
                continue;
            }

            CurriculumPlanEntryRequest request = buildCurriculumRequest(
                    classRef.buildingCode(),
                    classRef.className(),
                    subjectName,
                    hoursRaw,
                    readCell(row, headers.findAny("educationLevel", "level", "уровень", "уровень обучения", "индекс")),
                    readCell(row, headers.findAny("curriculumPart", "part", "часть", "блок")),
                    readCell(row, headers.findAny("subgroupRequired", "деление", "подгруппа", "с делением")),
                    readCell(row, headers.findAny("subgroupCount", "кол-во подгрупп", "подгрупп", "subgroupcount")),
                    readCell(row, headers.findAny("subgroup1Hours", "часы 1 подгруппы", "1 подгруппа часы", "subgroup1hours")),
                    readCell(row, headers.findAny("subgroup2Hours", "часы 2 подгруппы", "2 подгруппа часы", "subgroup2hours")),
                    readCell(row, headers.findAny("subgroup1EducationLevel", "уровень 1 подгруппы", "1 подгруппа уровень", "subgroup1educationlevel")),
                    readCell(row, headers.findAny("subgroup2EducationLevel", "уровень 2 подгруппы", "2 подгруппа уровень", "subgroup2educationlevel"))
            );

            if (request == null) {
                skipped++;
                continue;
            }
            requests.add(request);
        }
        return new ImportParseResult(requests, skipped);
    }

    private ImportParseResult parseMatrixSheet(Sheet sheet, List<KnownClassRef> knownClasses) {
        MatrixHeader matrixHeader = findMatrixHeader(sheet);
        List<CurriculumPlanEntryRequest> requests = new ArrayList<>();
        int skipped = 0;

        for (int rowIndex = matrixHeader.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String subjectName = normalize(readCell(row, matrixHeader.subjectColumnIndex()));
            if (subjectName.isBlank()) {
                continue;
            }
            if (isSummaryRow(subjectName)) {
                continue;
            }

            String partRaw = readCell(row, matrixHeader.partColumnIndex());
            String levelRaw = readCell(row, matrixHeader.levelColumnIndex());

            for (MatrixClassColumn classColumn : matrixHeader.classColumns()) {
                ClassRef classRef = resolveClassRef(classColumn.buildingCode(), classColumn.className(), knownClasses);
                CurriculumPlanEntryRequest request = buildCurriculumRequest(
                        classRef.buildingCode(),
                        classRef.className(),
                        subjectName,
                        readCell(row, classColumn.columnIndex()),
                        levelRaw,
                        partRaw,
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                );
                if (request == null) {
                    skipped++;
                    continue;
                }
                requests.add(request);
            }
        }

        if (requests.isEmpty()) {
            throw new IllegalArgumentException("Не удалось извлечь записи учебного плана из матричного Excel-файла");
        }
        return new ImportParseResult(requests, skipped);
    }

    private List<KnownClassRef> loadKnownClasses() {
        return classroomLeadershipRepository.findAll().stream()
                .map(entry -> new KnownClassRef(
                        normalize(entry.getNumberSchoolBuilding()),
                        ClassNameNormalizer.normalize(entry.getClassName())
                ))
                .toList();
    }

    private ClassRef resolveClassRef(String buildingCode, String className, List<KnownClassRef> knownClasses) {
        String normalizedBuilding = normalize(buildingCode);
        String normalizedClassName = normalize(className);
        if (normalizedClassName.isBlank()) {
            return new ClassRef(normalizedBuilding, normalizedClassName);
        }

        normalizedClassName = ClassNameNormalizer.normalize(normalizedClassName);
        if (!normalizedBuilding.isBlank()) {
            return new ClassRef(normalizedBuilding, normalizedClassName);
        }

        List<KnownClassRef> matches = knownClasses.stream()
                .filter(entry -> entry.className().equalsIgnoreCase(normalizedClassName))
                .toList();
        if (matches.isEmpty()) {
            return new ClassRef("", normalizedClassName);
        }
        if (matches.size() == 1) {
            KnownClassRef match = matches.get(0);
            return new ClassRef(match.buildingCode(), match.className());
        }
        throw new IllegalArgumentException("Класс " + normalizedClassName + " найден в нескольких корпусах. Укажите корпус в Excel-файле учебного плана.");
    }

    private CurriculumPlanEntryRequest buildCurriculumRequest(String building,
                                                              String className,
                                                              String subjectName,
                                                              String hoursRaw,
                                                              String educationLevelRaw,
                                                              String curriculumPartRaw,
                                                              String subgroupRequiredRaw,
                                                              String subgroupCountRaw,
                                                              String subgroup1HoursRaw,
                                                              String subgroup2HoursRaw,
                                                              String subgroup1LevelRaw,
                                                              String subgroup2LevelRaw) {
        if (normalize(building).isBlank() && normalize(className).isBlank() && normalize(subjectName).isBlank() && normalize(hoursRaw).isBlank()) {
            return null;
        }
        if (normalize(building).isBlank() || normalize(className).isBlank() || normalize(subjectName).isBlank() || normalize(hoursRaw).isBlank()) {
            return null;
        }

        Integer plannedHours = parseInteger(hoursRaw);
        if (plannedHours == null || plannedHours <= 0) {
            return null;
        }

        CurriculumPlanEntryRequest request = new CurriculumPlanEntryRequest();
        request.setNumberSchoolBuilding(normalize(building));
        request.setClassName(normalize(className));
        request.setSubjectName(normalize(subjectName));
        request.setPlannedHours(plannedHours);
        request.setEducationLevel(parseEducationLevel(educationLevelRaw));
        request.setCurriculumPart(parseCurriculumPart(curriculumPartRaw));

        boolean subgroupRequired = parseBoolean(subgroupRequiredRaw);
        request.setSubgroupRequired(subgroupRequired);
        request.setSubgroupCount(subgroupRequired ? Math.max(parseIntegerOrDefault(subgroupCountRaw, 2), 2) : 0);
        request.setSubgroup1Hours(subgroupRequired ? parseIntegerOrDefault(subgroup1HoursRaw, plannedHours) : null);
        request.setSubgroup2Hours(subgroupRequired ? parseIntegerOrDefault(subgroup2HoursRaw, plannedHours) : null);
        request.setSubgroup1EducationLevel(subgroupRequired ? parseEducationLevel(subgroup1LevelRaw) : null);
        request.setSubgroup2EducationLevel(subgroupRequired ? parseEducationLevel(subgroup2LevelRaw) : null);

        if (subgroupRequired) {
            if (request.getSubgroup1EducationLevel() == null) {
                request.setSubgroup1EducationLevel(request.getEducationLevel());
            }
            if (request.getSubgroup2EducationLevel() == null) {
                request.setSubgroup2EducationLevel(request.getEducationLevel());
            }
        }

        return request;
    }

    private MatrixHeader findMatrixHeader(Sheet sheet) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 20); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Integer subjectColumnIndex = null;
            Integer partColumnIndex = null;
            Integer levelColumnIndex = null;
            List<MatrixClassColumn> classColumns = new ArrayList<>();

            for (Cell cell : row) {
                String value = normalize(readCell(cell)).replace("ё", "е");
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.contains("предмет")) {
                    subjectColumnIndex = cell.getColumnIndex();
                    continue;
                }
                if (lower.contains("часть") || lower.contains("блок")) {
                    partColumnIndex = cell.getColumnIndex();
                    continue;
                }
                if (lower.contains("уров") || lower.equals("б/у") || lower.contains("индекс")) {
                    levelColumnIndex = cell.getColumnIndex();
                    continue;
                }

                String className = extractClassName(value);
                if (className != null) {
                    String buildingCode = resolveBuildingCode(sheet, rowIndex, cell.getColumnIndex());
                    classColumns.add(new MatrixClassColumn(cell.getColumnIndex(), buildingCode, className));
                }
            }

            if (subjectColumnIndex != null && !classColumns.isEmpty()) {
                return new MatrixHeader(rowIndex, subjectColumnIndex, partColumnIndex, levelColumnIndex, classColumns);
            }
        }
        throw new IllegalArgumentException("Не удалось определить матричную шапку Excel-файла учебного плана");
    }

    private String resolveBuildingCode(Sheet sheet, int headerRowIndex, int columnIndex) {
        for (int rowIndex = headerRowIndex - 1; rowIndex >= 0 && rowIndex >= headerRowIndex - 3; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            for (int candidate = columnIndex; candidate >= Math.max(0, columnIndex - 2); candidate--) {
                String value = normalize(readCell(row, candidate));
                String buildingCode = extractBuildingCode(value);
                if (!buildingCode.isBlank()) {
                    return buildingCode;
                }
            }
        }
        return "";
    }

    private String extractClassName(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT).replace('–', '-').replace('—', '-');
        if (normalized.matches("^\\d{1,2}\\s*[- ]?\\s*[А-ЯA-Z]$")) {
            return ClassNameNormalizer.normalize(normalized);
        }
        return null;
    }

    private String extractBuildingCode(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT).replace("ё", "е");
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.matches("^\\d+$")) {
            return normalized;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:корпус|корп\\.?|здание)?\\s*(\\d{1,3})").matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private boolean isSummaryRow(String subjectName) {
        String normalized = normalize(subjectName).toLowerCase(Locale.ROOT).replace("ё", "е");
        return normalized.startsWith("сумма") || normalized.contains("итого") || normalized.contains("блок");
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

    private HeaderLookup detectHeaders(Sheet sheet, Map<String, List<String>> requiredAliases) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 12); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> indexes = new HashMap<>();
            for (Cell cell : row) {
                String normalizedValue = normalize(readCell(cell)).toLowerCase(Locale.ROOT).replace("ё", "е");
                if (normalizedValue.isBlank()) {
                    continue;
                }
                requiredAliases.forEach((key, aliases) -> {
                    if (indexes.containsKey(key)) {
                        return;
                    }
                    if (aliases.stream().anyMatch(normalizedValue::contains)) {
                        indexes.put(key, cell.getColumnIndex());
                    }
                });
            }

            if (indexes.keySet().containsAll(requiredAliases.keySet())) {
                return new HeaderLookup(rowIndex, indexes, row);
            }
        }
        throw new IllegalArgumentException("Не удалось определить обязательные колонки Excel-файла учебного плана");
    }

    private String readCell(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return "";
        }
        return readCell(row.getCell(columnIndex));
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }

    private Integer parseInteger(String value) {
        String normalized = normalize(value).replace(',', '.');
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(normalized));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseIntegerOrDefault(String value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean parseBoolean(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return List.of("да", "yes", "true", "1", "+", "есть").contains(normalized) || normalized.contains("делени");
    }

    private EducationLevel parseEducationLevel(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT).replace("ё", "е");
        if (normalized.isBlank()) {
            return EducationLevel.BASIC;
        }
        if (normalized.equals("у") || normalized.contains("углуб") || normalized.contains("advanced")) {
            return EducationLevel.ADVANCED;
        }
        return EducationLevel.BASIC;
    }

    private CurriculumPart parseCurriculumPart(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT).replace("ё", "е");
        if (normalized.isBlank()) {
            return CurriculumPart.CORE;
        }
        if (normalized.startsWith("2") || normalized.contains("формир") || normalized.contains("formable")) {
            return CurriculumPart.FORMABLE;
        }
        if (normalized.startsWith("3") || normalized.contains("внеур") || normalized.contains("extra")) {
            return CurriculumPart.EXTRACURRICULAR;
        }
        return CurriculumPart.CORE;
    }

    private record HeaderLookup(int rowIndex, Map<String, Integer> requiredIndexes, Row headerRow) {
        Integer index(String key) {
            return requiredIndexes.get(key);
        }

        Integer findAny(String... aliases) {
            for (Cell cell : headerRow) {
                String normalized = new DataFormatter().formatCellValue(cell).trim().toLowerCase(Locale.ROOT).replace("ё", "е");
                for (String alias : aliases) {
                    if (normalized.contains(alias.toLowerCase(Locale.ROOT).replace("ё", "е"))) {
                        return cell.getColumnIndex();
                    }
                }
            }
            return null;
        }
    }

    private record ImportParseResult(List<CurriculumPlanEntryRequest> requests, int skipped) {
    }

    private record MatrixHeader(int headerRowIndex,
                                int subjectColumnIndex,
                                Integer partColumnIndex,
                                Integer levelColumnIndex,
                                List<MatrixClassColumn> classColumns) {
    }

    private record MatrixClassColumn(int columnIndex, String buildingCode, String className) {
    }

    private record KnownClassRef(String buildingCode, String className) {
    }

    private record ClassRef(String buildingCode, String className) {
    }
}
