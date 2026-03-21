package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CurriculumPlanServiceImpl implements CurriculumPlanService {

    private final CurriculumPlanEntryRepository repository;
    private final SchoolBuildingRepository buildingRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

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
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findSheet(workbook, List.of("учеб", "curriculum", "уп"));
            if (sheet == null) {
                throw new IllegalArgumentException("Не найден лист учебного плана");
            }

            ImportParseResult parsed = parseCurriculumWorkbook(sheet);

            List<CurriculumPlanEntry> saved = upsertBulk(parsed.requests());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("sheet", sheet.getSheetName());
            result.put("imported", saved.size());
            result.put("skipped", parsed.skipped());
            return result;
        } catch (Exception e) {
            log.error("Ошибка импорта учебного плана из Excel", e);
            throw new RuntimeException("Не удалось импортировать учебный план из Excel", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurriculumPlanEntry> findAll() {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            return buildingRepository.findByHeadUserId(userId)
                    .map(building -> repository.findAllByNumberSchoolBuilding(building.getCode()))
                    .orElse(List.of());
        }
        return repository.findAll();
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
        try {
            return parseRowBasedSheet(sheet);
        } catch (IllegalArgumentException rowBasedError) {
            log.warn("Не удалось распознать построчный импорт УП, пробую матричный формат: {}", rowBasedError.getMessage());
            return parseMatrixSheet(sheet);
        }
    }

    private ImportParseResult parseRowBasedSheet(Sheet sheet) {
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

            if (building.isBlank() && className.isBlank() && subjectName.isBlank() && hoursRaw.isBlank()) {
                continue;
            }

            CurriculumPlanEntryRequest request = buildCurriculumRequest(
                    building,
                    className,
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

    private ImportParseResult parseMatrixSheet(Sheet sheet) {
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
                CurriculumPlanEntryRequest request = buildCurriculumRequest(
                        classColumn.buildingCode(),
                        classColumn.className(),
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
}
