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
        List<CurriculumPlanEntry> entries = curriculumRepository.findAll();
        entries.sort(Comparator
                .comparing((CurriculumPlanEntry e) -> String.valueOf(e.getNumberSchoolBuilding()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getClassName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getCurriculumPart()))
                .thenComparing(e -> String.valueOf(e.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getEducationLevel()))
                .thenComparing(e -> String.valueOf(e.getStudyPeriod())));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CURRICULUM_EDITABLE");
            Row header = sheet.createRow(0);
            String[] columns = new String[]{
                    "Корпус", "Класс", "Направленность", "Часть", "Предмет", "Уровень", "Период", "Часы",
                    "Деление", "Подгр1 часы", "Подгр1 уровень", "Подгр2 часы", "Подгр2 уровень"
            };
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            Map<String, String> classDirections = new HashMap<>();
            classroomRepository.findAll().forEach(cls -> classDirections.put(
                    ClassNameNormalizer.normalize(cls.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(cls.getClassName()),
                    String.valueOf(cls.getClassDirection() == null ? "" : cls.getClassDirection())
            ));

            int rowNum = 1;
            for (CurriculumPlanEntry entry : entries) {
                Row row = sheet.createRow(rowNum++);
                String classKey = ClassNameNormalizer.normalize(entry.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(entry.getClassName());
                row.createCell(0).setCellValue(String.valueOf(entry.getNumberSchoolBuilding()));
                row.createCell(1).setCellValue(String.valueOf(entry.getClassName()));
                row.createCell(2).setCellValue(classDirections.getOrDefault(classKey, ""));
                row.createCell(3).setCellValue(String.valueOf(entry.getCurriculumPart()));
                row.createCell(4).setCellValue(String.valueOf(entry.getSubjectName()));
                row.createCell(5).setCellValue(String.valueOf(entry.getEducationLevel()));
                row.createCell(6).setCellValue(String.valueOf(entry.getStudyPeriod()));
                row.createCell(7).setCellValue(entry.getPlannedHours() == null ? 0D : entry.getPlannedHours().doubleValue());
                row.createCell(8).setCellValue(entry.isSubgroupRequired());
                row.createCell(9).setCellValue(entry.getSubgroup1Hours() == null ? "" : String.valueOf(entry.getSubgroup1Hours()));
                row.createCell(10).setCellValue(entry.getSubgroup1EducationLevel() == null ? "" : String.valueOf(entry.getSubgroup1EducationLevel()));
                row.createCell(11).setCellValue(entry.getSubgroup2Hours() == null ? "" : String.valueOf(entry.getSubgroup2Hours()));
                row.createCell(12).setCellValue(entry.getSubgroup2EducationLevel() == null ? "" : String.valueOf(entry.getSubgroup2EducationLevel()));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Override
    public CurriculumImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");

        try {
            List<EditableImportRow> editableRows = parseEditableRows(file);
            List<CurriculumImportRow> parsed = editableRows.isEmpty() ? parser.parse(file.getInputStream()) : List.of();
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

            return new CurriculumImportResult(created, updated, deprecated, classesCreated, orphaned, subjectsImported);
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
