package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.service.CurriculumImportService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CurriculumImportServiceImpl implements CurriculumImportService {

    private final CurriculumExcelParser parser;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final ClassroomLeadershipRepository classroomRepository;
    private final ManualLoadEntryRepository manualLoadRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final SubjectCatalogRepository subjectCatalogRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;


    @Override
    public byte[] exportEditableWorkbook() throws IOException {
        List<CurriculumPlanEntry> entries = new ArrayList<>(curriculumRepository.findAll().stream().filter(e -> !e.isDeprecated()).toList());
        entries.sort(Comparator
                .comparing((CurriculumPlanEntry e) -> String.valueOf(e.getNumberSchoolBuilding()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getClassName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getCurriculumPart()))
                .thenComparing(e -> String.valueOf(e.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getEducationLevel()))
                .thenComparing(e -> String.valueOf(e.getStudyPeriod())));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CURRICULUM_VISUAL");
            List<String> classes = entries.stream()
                    .map(e -> normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName()))
                    .distinct()
                    .sorted(String::compareTo)
                    .toList();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Блок / предмет / часы");
            for (int i = 0; i < classes.size(); i++) {
                header.createCell(i + 1).setCellValue(classes.get(i));
            }

            Map<String, List<CurriculumPlanEntry>> byPartSubject = new LinkedHashMap<>();
            entries.forEach(e -> {
                String key = (e.getCurriculumPart() == null ? CurriculumPart.CORE : e.getCurriculumPart()) + "|" + normalizeSubject(e.getSubjectName());
                byPartSubject.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            });

            int rowNum = 1;
            for (CurriculumPart part : List.of(CurriculumPart.CORE, CurriculumPart.FORMABLE, CurriculumPart.EXTRACURRICULAR)) {
                Row partRow = sheet.createRow(rowNum++);
                partRow.createCell(0).setCellValue(part == CurriculumPart.CORE ? "Основная часть"
                        : (part == CurriculumPart.FORMABLE ? "Формируемая часть" : "Внеурочная деятельность"));

                List<Map.Entry<String, List<CurriculumPlanEntry>>> subjects = byPartSubject.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(part.name() + "|"))
                        .sorted(Map.Entry.comparingByKey())
                        .toList();

                for (Map.Entry<String, List<CurriculumPlanEntry>> subjectEntry : subjects) {
                    String subjectName = subjectEntry.getKey().substring(subjectEntry.getKey().indexOf('|') + 1);
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(subjectName);
                    for (int i = 0; i < classes.size(); i++) {
                        String classKey = classes.get(i);
                        List<CurriculumPlanEntry> values = subjectEntry.getValue().stream()
                                .filter(e -> (normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName())).equals(classKey))
                                .toList();
                        BigDecimal year = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.YEAR)
                                .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal h1 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H1)
                                .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal h2 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H2)
                                .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

                        String rendered = "";
                        if (year.compareTo(BigDecimal.ZERO) > 0) rendered = year.stripTrailingZeros().toPlainString();
                        else if (h1.compareTo(BigDecimal.ZERO) > 0 || h2.compareTo(BigDecimal.ZERO) > 0) {
                            rendered = (h1.compareTo(BigDecimal.ZERO) > 0 ? h1.stripTrailingZeros().toPlainString() : "")
                                    + "/" + (h2.compareTo(BigDecimal.ZERO) > 0 ? h2.stripTrailingZeros().toPlainString() : "");
                        }
                        row.createCell(i + 1).setCellValue(rendered);
                    }
                }
            }

            for (int i = 0; i <= classes.size(); i++) sheet.autoSizeColumn(i);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Override
    public CurriculumImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");

        try {
            List<EditableImportRow> editableRows = parseEditableRows(file);
            VisualParseResult visualParseResult = null;
            if (editableRows.isEmpty()) {
                visualParseResult = parseVisualRows(file);
                editableRows = visualParseResult.rows();
            }
            List<CurriculumImportRow> parsed = editableRows.isEmpty() ? normalizeImportedRows(parser.parse(file.getInputStream())) : List.of();
            int created = 0, updated = 0, classesCreated = 0, subjectsImported = 0;
            Set<Long> importedIds = new HashSet<>();
            Map<String, SubjectCatalogEntry> existingSubjects = new HashMap<>();
            subjectCatalogRepository.findAll().forEach(s -> existingSubjects.put(subjectKey(s.getSubjectName(), s.getSubjectType()), s));

            String fallbackTeacher = teacherRepository.findAll().stream().findFirst().map(TeacherDirectoryEntry::getFioTeacher).orElse("Не назначен");

            if (!editableRows.isEmpty()) {
                for (EditableImportRow row : editableRows) {
                    StudyPeriodSetting resolvedEditableRule = studyPeriodSettingService.resolveRuleForClassAndPeriod(row.className(), row.studyPeriod());
                    CurriculumPlanEntry entry = curriculumRepository
                            .findByNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                                    row.numberSchoolBuilding(),
                                    row.className(),
                                    row.subjectName(),
                                    row.educationLevel(),
                                    row.curriculumPart(),
                                    resolvedEditableRule.getStudyPeriod(),
                                    resolvedEditableRule.getId()
                            )
                            .orElseGet(CurriculumPlanEntry::new);
                    boolean isNew = entry.getId() == null;
                    entry.setAcademicYear(entry.getAcademicYear() == null ? "" : entry.getAcademicYear());
                    entry.setStage(entry.getStage() == null ? CurriculumStage.NOO : entry.getStage());
                    entry.setNumberSchoolBuilding(row.numberSchoolBuilding());
                    entry.setClassName(row.className());
                    entry.setSubjectName(row.subjectName());
                    entry.setCurriculumPart(row.curriculumPart());
                    entry.setEducationLevel(row.educationLevel());
                    entry.setStudyPeriod(resolvedEditableRule.getStudyPeriod());
                    entry.setStudyPeriodSettingId(resolvedEditableRule.getId());
                    entry.setPlannedHours(row.plannedHours());
                    entry.setSubgroupRequired(row.subgroupRequired());
                    entry.setSubgroupCount(row.subgroupRequired() ? 2 : 0);
                    entry.setSubgroup1Hours(row.subgroupRequired() ? row.subgroup1Hours() : null);
                    entry.setSubgroup2Hours(row.subgroupRequired() ? row.subgroup2Hours() : null);
                    entry.setSubgroup1EducationLevel(row.subgroupRequired() ? row.subgroup1EducationLevel() : null);
                    entry.setSubgroup2EducationLevel(row.subgroupRequired() ? row.subgroup2EducationLevel() : null);
                    entry.setDeprecated(false);

                    CurriculumPlanEntry saved = curriculumRepository.save(entry);
                    importedIds.add(saved.getId());
                    if (isNew) created++; else updated++;

                    boolean existedClass = classroomRepository.existsByNumberSchoolBuildingAndClassName(row.numberSchoolBuilding(), row.className());
                    ensureClassroom(row.numberSchoolBuilding(), row.className(), row.classDirection(), fallbackTeacher);
                    if (!existedClass) classesCreated++;

                    SubjectType subjectType = row.curriculumPart() == CurriculumPart.EXTRACURRICULAR
                            ? SubjectType.EXTRACURRICULAR
                            : SubjectType.CORE_FORMABLE;
                    String normalizedSubject = normalizeSubject(row.subjectName());
                    String subjectKey = subjectKey(normalizedSubject, subjectType);
                    if (!normalizedSubject.isBlank() && !existingSubjects.containsKey(subjectKey)) {
                        SubjectCatalogEntry subjectCatalogEntry = new SubjectCatalogEntry();
                        subjectCatalogEntry.setSubjectName(normalizedSubject);
                        subjectCatalogEntry.setSubjectType(subjectType);
                        existingSubjects.put(subjectKey, subjectCatalogRepository.save(subjectCatalogEntry));
                        subjectsImported++;
                    }
                }
            } else {
                for (CurriculumImportRow row : parsed) {
                    CurriculumPlanEntry entry = curriculumRepository
                            .findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(
                                    row.getAcademicYear(), row.getStage(), row.getClassName(), row.getSubjectName(), row.getStudyPeriod())
                            .orElseGet(CurriculumPlanEntry::new);

                    boolean isNew = entry.getId() == null;
                    entry.setAcademicYear(row.getAcademicYear());
                    entry.setStage(row.getStage());
                    entry.setClassName(row.getClassName());
                    entry.setSubjectName(row.getSubjectName());
                    StudyPeriodSetting resolvedRule = studyPeriodSettingService.resolveRuleForClassAndPeriod(row.getClassName(), row.getStudyPeriod());
                    entry.setStudyPeriod(resolvedRule.getStudyPeriod());
                    entry.setStudyPeriodSettingId(resolvedRule.getId());
                    entry.setPlannedHours(row.getPlannedHours());
                    entry.setCurriculumPart(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart());
                    entry.setDeprecated(false);
                    if (isNew) {
                        entry.setNumberSchoolBuilding("СП0");
                        entry.setEducationLevel(EducationLevel.BASIC);
                        entry.setSubgroupRequired(false);
                        entry.setSubgroupCount(0);
                    }

                    if (entry.getEducationLevel() != EducationLevel.ADVANCED) {
                        entry.setEducationLevel(EducationLevel.BASIC);
                    }
                    if ("СП0".equalsIgnoreCase(entry.getNumberSchoolBuilding()) || entry.getNumberSchoolBuilding() == null || entry.getNumberSchoolBuilding().isBlank()) {
                        entry.setNumberSchoolBuilding("СП0");
                    }

                    CurriculumPlanEntry saved = curriculumRepository.save(entry);
                    importedIds.add(saved.getId());
                    if (isNew) created++; else updated++;

                    if (!classroomRepository.existsByNumberSchoolBuildingAndClassName("СП0", row.getClassName())) {
                        ClassroomLeadershipEntry cls = new ClassroomLeadershipEntry();
                        cls.setNumberSchoolBuilding("СП0");
                        cls.setClassName(row.getClassName());
                        cls.setClassDirection(row.getClassDirection() == null || row.getClassDirection().isBlank() ? "Не указана" : row.getClassDirection());
                        cls.setFioTeacher(fallbackTeacher);
                        classroomRepository.save(cls);
                        classesCreated++;
                    }

                    SubjectType subjectType = resolveSubjectType(row);
                    String normalizedSubject = normalizeSubject(row.getSubjectName());
                    String subjectKey = subjectKey(normalizedSubject, subjectType);
                    if (!normalizedSubject.isBlank() && !existingSubjects.containsKey(subjectKey)) {
                        SubjectCatalogEntry subjectCatalogEntry = new SubjectCatalogEntry();
                        subjectCatalogEntry.setSubjectName(normalizedSubject);
                        subjectCatalogEntry.setSubjectType(subjectType);
                        existingSubjects.put(subjectKey, subjectCatalogRepository.save(subjectCatalogEntry));
                        subjectsImported++;
                    }
                }
            }

            int deprecated = 0;
            List<CurriculumPlanEntry> all = curriculumRepository.findAll();
            for (CurriculumPlanEntry e : all) {
                boolean shouldDeprecate = !importedIds.contains(e.getId());
                if (shouldDeprecate && !e.isDeprecated()) {
                    e.setDeprecated(true);
                    curriculumRepository.save(e);
                    deprecated++;
                }
            }

            Set<String> activeKeys = new HashSet<>();
            curriculumRepository.findAll().stream().filter(e -> !e.isDeprecated()).forEach(e ->
                    activeKeys.add(keyWithoutBuilding(e.getClassName(), e.getSubjectName(), e.getEducationLevel(), e.getStudyPeriod())));

            int orphaned = 0;
            List<ManualLoadEntry> loads = manualLoadRepository.findAll();
            for (ManualLoadEntry l : loads) {
                boolean isOrphan = !activeKeys.contains(keyWithoutBuilding(
                        ClassNameNormalizer.normalize(l.getClassName()),
                        l.getSubjectName(),
                        l.getEducationLevel(),
                        l.getStudyPeriod() == null ? StudyPeriod.YEAR : l.getStudyPeriod()));
                l.setOrphaned(isOrphan);
                if (isOrphan) orphaned++;
            }
            manualLoadRepository.saveAll(loads);

            List<CurriculumImportResult.SumMismatch> mismatches = visualParseResult == null
                    ? List.of()
                    : compareVisualSums(visualParseResult.expectedSums(), visualParseResult.rows());
            return new CurriculumImportResult(created, updated, deprecated, classesCreated, orphaned, subjectsImported, mismatches);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать учебный план", e);
        }
    }

    private void ensureClassroom(String building, String className, String classDirection, String fallbackTeacher) {
        if (classroomRepository.existsByNumberSchoolBuildingAndClassName(building, className)) return;
        ClassroomLeadershipEntry cls = new ClassroomLeadershipEntry();
        cls.setNumberSchoolBuilding(building);
        cls.setClassName(className);
        cls.setClassDirection(classDirection == null || classDirection.isBlank() ? "Не указана" : classDirection);
        cls.setFioTeacher(fallbackTeacher);
        classroomRepository.save(cls);
    }

    private List<CurriculumImportRow> normalizeImportedRows(List<CurriculumImportRow> rows) {
        Map<String, CurriculumImportRow> byKey = new LinkedHashMap<>();
        for (CurriculumImportRow row : rows) {
            String baseKey = String.join("|",
                    String.valueOf(row.getAcademicYear()),
                    String.valueOf(row.getStage()),
                    String.valueOf(row.getClassName()),
                    String.valueOf(row.getSubjectName()),
                    String.valueOf(row.getCurriculumPart()));
            String h1Key = baseKey + "|H1";
            String h2Key = baseKey + "|H2";
            if (row.getStudyPeriod() == StudyPeriod.H1 && byKey.containsKey(h2Key)
                    && row.getPlannedHours() != null
                    && byKey.get(h2Key).getPlannedHours() != null
                    && row.getPlannedHours().compareTo(byKey.get(h2Key).getPlannedHours()) == 0) {
                CurriculumImportRow merged = new CurriculumImportRow(
                        row.getAcademicYear(),
                        row.getStage(),
                        row.getClassName(),
                        row.getClassDirection(),
                        row.getSubjectName(),
                        row.getPlannedHours(),
                        StudyPeriod.YEAR,
                        row.getCurriculumPart()
                );
                byKey.remove(h2Key);
                byKey.put(baseKey + "|YEAR", merged);
                continue;
            }
            if (row.getStudyPeriod() == StudyPeriod.H2 && byKey.containsKey(h1Key)
                    && row.getPlannedHours() != null
                    && byKey.get(h1Key).getPlannedHours() != null
                    && row.getPlannedHours().compareTo(byKey.get(h1Key).getPlannedHours()) == 0) {
                CurriculumImportRow merged = new CurriculumImportRow(
                        row.getAcademicYear(),
                        row.getStage(),
                        row.getClassName(),
                        row.getClassDirection(),
                        row.getSubjectName(),
                        row.getPlannedHours(),
                        StudyPeriod.YEAR,
                        row.getCurriculumPart()
                );
                byKey.remove(h1Key);
                byKey.put(baseKey + "|YEAR", merged);
                continue;
            }
            byKey.put(baseKey + "|" + row.getStudyPeriod(), row);
        }
        return new ArrayList<>(byKey.values());
    }

    private VisualParseResult parseVisualRows(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("CURRICULUM_VISUAL");
            if (sheet == null) return new VisualParseResult(List.of(), Map.of());
            Row header = sheet.getRow(0);
            if (header == null) return new VisualParseResult(List.of(), Map.of());

            List<ClassHeaderMeta> classColumns = new ArrayList<>();
            for (int col = 1; col < header.getLastCellNum(); col++) {
                String raw = normalizeSubject(readCell(header.getCell(col)));
                if (raw.isBlank()) continue;
                String[] parts = raw.split("\\|", 2);
                String building = parts.length > 1 ? normalizeSubject(parts[0]) : "СП0";
                String className = ClassNameNormalizer.normalize(parts.length > 1 ? parts[1] : raw);
                if (className.isBlank()) continue;
                classColumns.add(new ClassHeaderMeta(col, building.isBlank() ? "СП0" : building, className));
            }

            if (classColumns.isEmpty()) return new VisualParseResult(List.of(), Map.of());
            List<EditableImportRow> result = new ArrayList<>();
            Map<String, Map<String, SumPair>> expectedSums = new LinkedHashMap<>();
            CurriculumPart currentPart = CurriculumPart.CORE;

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                String title = normalizeSubject(readCell(row.getCell(0)));
                if (title.isBlank()) continue;
                String lower = title.toLowerCase(Locale.ROOT);

                if (lower.contains("основная часть")) {
                    currentPart = CurriculumPart.CORE;
                    continue;
                }
                if (lower.contains("формируем")) {
                    currentPart = CurriculumPart.FORMABLE;
                    continue;
                }
                if (lower.contains("внеуроч")) {
                    currentPart = CurriculumPart.EXTRACURRICULAR;
                    continue;
                }
                if (lower.startsWith("сумма")) {
                    String label = normalizeSumLabel(lower);
                    if (label != null) {
                        Map<String, SumPair> byClass = expectedSums.computeIfAbsent(label, k -> new LinkedHashMap<>());
                        for (ClassHeaderMeta classMeta : classColumns) {
                            SumPair pair = parseSumCell(readCell(row.getCell(classMeta.colIndex)));
                            if (pair != null) {
                                byClass.put(classMeta.building + "|" + classMeta.className, pair);
                            }
                        }
                    }
                    continue;
                }

                for (ClassHeaderMeta classMeta : classColumns) {
                    String rawHours = normalizeSubject(readCell(row.getCell(classMeta.colIndex)));
                    if (rawHours.isBlank() || "0".equals(rawHours)) continue;
                    if (rawHours.contains("/")) {
                        String[] halves = rawHours.split("/", -1);
                        BigDecimal h1 = parseDecimal(halves.length > 0 ? halves[0] : "");
                        BigDecimal h2 = parseDecimal(halves.length > 1 ? halves[1] : "");
                        if (h1 != null && h1.compareTo(BigDecimal.ZERO) > 0) {
                            result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                    EducationLevel.BASIC, StudyPeriod.H1, h1, false, null, EducationLevel.BASIC, null, EducationLevel.BASIC));
                        }
                        if (h2 != null && h2.compareTo(BigDecimal.ZERO) > 0) {
                            result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                    EducationLevel.BASIC, StudyPeriod.H2, h2, false, null, EducationLevel.BASIC, null, EducationLevel.BASIC));
                        }
                        continue;
                    }
                    BigDecimal year = parseDecimal(rawHours);
                    if (year == null || year.compareTo(BigDecimal.ZERO) <= 0) continue;
                    result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                            EducationLevel.BASIC, StudyPeriod.YEAR, year, false, null, EducationLevel.BASIC, null, EducationLevel.BASIC));
                }
            }
            return new VisualParseResult(result, expectedSums);
        } catch (Exception e) {
            return new VisualParseResult(List.of(), Map.of());
        }
    }

    private List<CurriculumImportResult.SumMismatch> compareVisualSums(Map<String, Map<String, SumPair>> expected,
                                                                       List<EditableImportRow> rows) {
        if (expected == null || expected.isEmpty() || rows == null || rows.isEmpty()) return List.of();
        Map<String, Map<String, SumPair>> actual = new LinkedHashMap<>();
        for (EditableImportRow row : rows) {
            String classKey = row.numberSchoolBuilding() + "|" + row.className();
            BigDecimal hours = row.plannedHours() == null ? BigDecimal.ZERO : row.plannedHours();
            if (row.curriculumPart() == CurriculumPart.CORE || row.curriculumPart() == CurriculumPart.FORMABLE) {
                accumulateSum(actual, "sum_of", classKey, row.studyPeriod(), hours);
            }
            if (row.curriculumPart() == CurriculumPart.CORE) {
                accumulateSum(actual, "sum_core", classKey, row.studyPeriod(), hours);
            }
            if (row.curriculumPart() == CurriculumPart.FORMABLE) {
                accumulateSum(actual, "sum_formable", classKey, row.studyPeriod(), hours);
            }
            if (row.curriculumPart() == CurriculumPart.EXTRACURRICULAR) {
                accumulateSum(actual, "sum_extracurricular", classKey, row.studyPeriod(), hours);
            }
        }

        List<CurriculumImportResult.SumMismatch> mismatches = new ArrayList<>();
        for (Map.Entry<String, Map<String, SumPair>> sumEntry : expected.entrySet()) {
            String label = sumEntry.getKey();
            for (Map.Entry<String, SumPair> classEntry : sumEntry.getValue().entrySet()) {
                SumPair exp = classEntry.getValue();
                SumPair act = actual.getOrDefault(label, Map.of()).getOrDefault(classEntry.getKey(), new SumPair(BigDecimal.ZERO, BigDecimal.ZERO));
                if (exp.h1.compareTo(act.h1) != 0 || exp.h2.compareTo(act.h2) != 0) {
                    mismatches.add(new CurriculumImportResult.SumMismatch(
                            classEntry.getKey(),
                            label,
                            formatPair(exp),
                            formatPair(act)
                    ));
                }
            }
        }
        return mismatches;
    }

    private void accumulateSum(Map<String, Map<String, SumPair>> actual,
                               String label,
                               String classKey,
                               StudyPeriod period,
                               BigDecimal hours) {
        Map<String, SumPair> byClass = actual.computeIfAbsent(label, k -> new LinkedHashMap<>());
        SumPair pair = byClass.getOrDefault(classKey, new SumPair(BigDecimal.ZERO, BigDecimal.ZERO));
        if (period == StudyPeriod.H1) {
            pair = new SumPair(pair.h1.add(hours), pair.h2);
        } else if (period == StudyPeriod.H2) {
            pair = new SumPair(pair.h1, pair.h2.add(hours));
        } else {
            pair = new SumPair(pair.h1.add(hours), pair.h2.add(hours));
        }
        byClass.put(classKey, pair);
    }

    private String formatPair(SumPair pair) {
        return pair.h1.stripTrailingZeros().toPlainString() + "/" + pair.h2.stripTrailingZeros().toPlainString();
    }

    private String normalizeSumLabel(String lowerTitle) {
        if (lowerTitle.contains("о+ф") || lowerTitle.contains("о + ф")) return "sum_of";
        if (lowerTitle.contains("основ")) return "sum_core";
        if (lowerTitle.contains("формируем")) return "sum_formable";
        if (lowerTitle.contains("внеуроч")) return "sum_extracurricular";
        return null;
    }

    private SumPair parseSumCell(String rawValue) {
        String value = normalizeSubject(rawValue);
        if (value.isBlank()) return null;
        if (value.contains("/")) {
            String[] halves = value.split("/", -1);
            BigDecimal h1 = parseDecimal(halves.length > 0 ? halves[0] : "");
            BigDecimal h2 = parseDecimal(halves.length > 1 ? halves[1] : "");
            return new SumPair(h1 == null ? BigDecimal.ZERO : h1, h2 == null ? BigDecimal.ZERO : h2);
        }
        BigDecimal one = parseDecimal(value);
        if (one == null) return null;
        return new SumPair(one, one);
    }

    private List<EditableImportRow> parseEditableRows(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("CURRICULUM_EDITABLE");
            if (sheet == null) return List.of();
            List<EditableImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String building = normalizeSubject(readCell(row.getCell(0)));
                String className = ClassNameNormalizer.normalize(readCell(row.getCell(1)));
                String classDirection = normalizeSubject(readCell(row.getCell(2)));
                String partRaw = normalizeSubject(readCell(row.getCell(3)));
                String subject = normalizeSubject(readCell(row.getCell(4)));
                String levelRaw = normalizeSubject(readCell(row.getCell(5)));
                String periodRaw = normalizeSubject(readCell(row.getCell(6)));
                BigDecimal hours = parseDecimal(readCell(row.getCell(7)));
                boolean subgroupRequired = Boolean.parseBoolean(normalizeSubject(readCell(row.getCell(8))));
                Integer subgroup1Hours = parseInteger(readCell(row.getCell(9)));
                EducationLevel subgroup1Level = parseLevel(readCell(row.getCell(10)));
                Integer subgroup2Hours = parseInteger(readCell(row.getCell(11)));
                EducationLevel subgroup2Level = parseLevel(readCell(row.getCell(12)));
                if (building.isBlank() || className.isBlank() || subject.isBlank() || hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) continue;
                rows.add(new EditableImportRow(
                        building,
                        className,
                        classDirection,
                        parsePart(partRaw),
                        subject,
                        parseLevel(levelRaw),
                        parsePeriod(periodRaw, className),
                        hours,
                        subgroupRequired,
                        subgroup1Hours,
                        subgroup1Level == null ? EducationLevel.BASIC : subgroup1Level,
                        subgroup2Hours,
                        subgroup2Level == null ? EducationLevel.BASIC : subgroup2Level
                ));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String readCell(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.toString();
            default -> "";
        };
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(normalizeSubject(value).replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            String v = normalizeSubject(value);
            if (v.isBlank()) return null;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private CurriculumPart parsePart(String value) {
        try {
            return CurriculumPart.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return CurriculumPart.CORE;
        }
    }

    private EducationLevel parseLevel(String value) {
        try {
            return EducationLevel.valueOf(normalizeSubject(value).toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return EducationLevel.BASIC;
        }
    }

    private StudyPeriod parsePeriod(String value, String className) {
        try {
            StudyPeriod parsed = StudyPeriod.valueOf(normalizeSubject(value).toUpperCase(Locale.ROOT));
            Integer parallel = ClassNameNormalizer.extractParallel(className);
            if (parallel != null && parallel < 10) return StudyPeriod.YEAR;
            return parsed == StudyPeriod.H2 ? StudyPeriod.H2 : StudyPeriod.H1;
        } catch (Exception e) {
            Integer parallel = ClassNameNormalizer.extractParallel(className);
            return (parallel != null && parallel >= 10) ? StudyPeriod.H1 : StudyPeriod.YEAR;
        }
    }

    private record EditableImportRow(
            String numberSchoolBuilding,
            String className,
            String classDirection,
            CurriculumPart curriculumPart,
            String subjectName,
            EducationLevel educationLevel,
            StudyPeriod studyPeriod,
            BigDecimal plannedHours,
            boolean subgroupRequired,
            Integer subgroup1Hours,
            EducationLevel subgroup1EducationLevel,
            Integer subgroup2Hours,
            EducationLevel subgroup2EducationLevel
    ) {}

    private record ClassHeaderMeta(int colIndex, String building, String className) {}
    private record SumPair(BigDecimal h1, BigDecimal h2) {}
    private record VisualParseResult(List<EditableImportRow> rows, Map<String, Map<String, SumPair>> expectedSums) {}

    private SubjectType resolveSubjectType(CurriculumImportRow row) {
        if (row.getCurriculumPart() == CurriculumPart.EXTRACURRICULAR) {
            return SubjectType.EXTRACURRICULAR;
        }
        String value = String.valueOf(row.getSubjectName() == null ? "" : row.getSubjectName()).trim().toLowerCase(Locale.ROOT);
        if (value.contains("внеур") || value.contains("разговоры о важном")) {
            return SubjectType.EXTRACURRICULAR;
        }
        return SubjectType.CORE_FORMABLE;
    }

    private String normalizeSubject(String value) {
        return String.valueOf(value == null ? "" : value).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String subjectKey(String name, SubjectType type) {
        return normalizeSubject(name).toLowerCase(Locale.ROOT) + "|" + type.name();
    }

    private String keyWithoutBuilding(String c, String s, EducationLevel l, StudyPeriod studyPeriod) {
        return String.join("|", String.valueOf(c).trim(), String.valueOf(s).trim(), String.valueOf(l), String.valueOf(studyPeriod == null ? StudyPeriod.YEAR : studyPeriod));
    }
}
