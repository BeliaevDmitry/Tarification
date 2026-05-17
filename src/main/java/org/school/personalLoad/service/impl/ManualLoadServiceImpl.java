package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadPlanFactSummary;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ContinuityStatus;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.ManualLoadService;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualLoadServiceImpl implements ManualLoadService {

    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final DatabaseService databaseService;
    private final CurriculumPlanService curriculumPlanService;
    private final StudyPeriodSettingService studyPeriodSettingService;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SubjectCatalogRepository subjectCatalogRepository;

    @Override
    public ManualLoadEntry create(ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(request);
        return manualLoadEntryRepository.save(entity);
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) {
        java.util.Set<String> explicitAcademicYears = requests.stream()
                .filter(java.util.Objects::nonNull)
                .map(ManualLoadEntryRequest::getAcademicYear)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(year -> !year.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        List<ManualLoadEntry> entries = requests.stream().map(this::toEntity).toList();
        java.util.Set<String> buildingCodes = entries.stream()
                .map(ManualLoadEntry::getNumberSchoolBuilding)
                .filter(java.util.Objects::nonNull)
                .map(code -> code.trim().toLowerCase())
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (!buildingCodes.isEmpty()) {
            if (explicitAcademicYears.size() == 1) {
                String academicYear = explicitAcademicYears.iterator().next();
                manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(academicYear, buildingCodes);
            } else {
                manualLoadEntryRepository.deleteByBuildingCodes(buildingCodes);
            }
        }
        return manualLoadEntryRepository.saveAll(entries);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear) {
        return manualLoadEntryRepository.findAllByAcademicYear(academicYear);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding) {
        if (numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            return findAll(academicYear);
        }
        return manualLoadEntryRepository.findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(
                academicYear,
                numberSchoolBuilding.trim()
        );
    }

    @Override
    @Transactional
    public void clearAll(String academicYear) {
        manualLoadEntryRepository.deleteAllByAcademicYear(academicYear);
    }

    @Override
    @Transactional
    public void clearByBuilding(String academicYear, String numberSchoolBuilding) {
        if (numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            throw new IllegalArgumentException("building is required");
        }
        manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(
                academicYear,
                java.util.List.of(numberSchoolBuilding.trim().toLowerCase(java.util.Locale.ROOT))
        );
    }

    @Override
    public ManualLoadProcessResult processCurrentManualLoad(String academicYear) {
        List<ManualLoadEntry> entries = manualLoadEntryRepository.findAllByAcademicYear(academicYear);
        List<TarifficationPerson> tarifficationList = new ArrayList<>();
        List<SubjectWithGroup> groupList = new ArrayList<>();
        Map<RuleKey, SummaryAccumulator> summaryByRule = new HashMap<>();

        for (ManualLoadEntry entry : entries) {
            CurriculumPlanEntry rule = validateAgainstCurriculum(entry);
            int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();

            RuleKey key = new RuleKey(rule.getClassName(), rule.getSubjectName(), rule.getEducationLevel(), rule.getStudyPeriod());
            summaryByRule.computeIfAbsent(key, k -> new SummaryAccumulator(rule.getPlannedHours()))
                    .addActualHours(effectiveLoad);

            TarifficationPerson person = new TarifficationPerson(
                    entry.getFioTeacher(),
                    entry.getNumberSchoolBuilding(),
                    entry.getSubjectName(),
                    entry.getClassName(),
                    entry.getLoad()
            );
            person.setGroupNameEducationalPlan(entry.getGroupNameEducationalPlan() != null
                    ? entry.getGroupNameEducationalPlan() : "");
            person.setGroupLoad(effectiveLoad);
            tarifficationList.add(person);
        }

        tarifficationList = tarifficationProcessingService.addingGroup(tarifficationList, groupList);
        tarifficationProcessingService.sortByFIO(tarifficationList);
        databaseService.compareAndSave(tarifficationList);

        List<ManualLoadPlanFactSummary> summaries = summaryByRule.entrySet().stream()
                .map(entry -> {
                    RuleKey key = entry.getKey();
                    SummaryAccumulator summary = entry.getValue();
                    return new ManualLoadPlanFactSummary(
                            key.className,
                            key.subjectName,
                            key.educationLevel,
                            summary.plannedHours,
                            summary.actualHours,
                            summary.plannedHours.subtract(summary.actualHours)
                    );
                })
                .sorted((a, b) -> {
                    int classCompare = a.getClassName().compareToIgnoreCase(b.getClassName());
                    if (classCompare != 0) {
                        return classCompare;
                    }
                    int subjectCompare = a.getSubjectName().compareToIgnoreCase(b.getSubjectName());
                    if (subjectCompare != 0) {
                        return subjectCompare;
                    }
                    return a.getEducationLevel().name().compareToIgnoreCase(b.getEducationLevel().name());
                })
                .toList();

        log.info("Ручная нагрузка обработана. Записей: {}, сводок: {}", tarifficationList.size(), summaries.size());
        return new ManualLoadProcessResult("ok", tarifficationList.size(), summaries);
    }

    @Override
    public byte[] exportWorkbook(String academicYear) throws IOException {
        List<ManualLoadTemplateRow> templateRows = buildTemplateRows(academicYear);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("LOAD_EDITABLE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Учебный год");
            header.createCell(1).setCellValue("Корпус");
            header.createCell(2).setCellValue("Класс");
            header.createCell(3).setCellValue("Предмет");
            header.createCell(4).setCellValue("Группа");
            header.createCell(5).setCellValue("Период");
            header.createCell(6).setCellValue("С");
            header.createCell(7).setCellValue("По");
            header.createCell(8).setCellValue("Часы");
            header.createCell(9).setCellValue("Уровень");
            header.createCell(10).setCellValue("ФИО педагога");
            header.createCell(11).setCellValue("ROW_KEY");

            int rowNum = 1;
            for (ManualLoadTemplateRow row : templateRows) {
                Row excelRow = sheet.createRow(rowNum++);
                excelRow.createCell(0).setCellValue(row.academicYear());
                excelRow.createCell(1).setCellValue(row.numberSchoolBuilding());
                excelRow.createCell(2).setCellValue(row.className());
                excelRow.createCell(3).setCellValue(row.subjectName());
                excelRow.createCell(4).setCellValue(row.groupNameEducationalPlan() == null ? "" : row.groupNameEducationalPlan());
                excelRow.createCell(5).setCellValue(row.studyPeriod().name());
                excelRow.createCell(6).setCellValue(row.loadFromDate().toString());
                excelRow.createCell(7).setCellValue(row.loadToDate().toString());
                excelRow.createCell(8).setCellValue(row.load());
                excelRow.createCell(9).setCellValue(row.educationLevel().name());
                excelRow.createCell(10).setCellValue(row.fioTeacher() == null ? "" : row.fioTeacher());
                excelRow.createCell(11).setCellValue(row.rowKey());
            }

            for (int i = 0; i <= 11; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> importWorkbook(String academicYear, MultipartFile file) {
        List<ManualLoadEntryRequest> requests = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        LocalDate today = LocalDate.now();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("LOAD_EDITABLE");
            if (sheet == null) {
                throw new IllegalArgumentException("В файле отсутствует лист LOAD_EDITABLE");
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String rowKey = readCell(row, 11);
                if (rowKey.isBlank()) {
                    errors.add("Строка " + (i + 1) + ": отсутствует ROW_KEY");
                    continue;
                }
                String rowYear = readCell(row, 0);
                if (!rowYear.isBlank() && !academicYear.equals(rowYear.trim())) {
                    errors.add("Строка " + (i + 1) + ": учебный год " + rowYear + " не совпадает с выбранным " + academicYear);
                    continue;
                }
                String fio = readCell(row, 10).trim();
                if (fio.isBlank()) {
                    fio = "Вакансия";
                } else if (!fio.toLowerCase(Locale.ROOT).contains("вакан")) {
                    TeacherDirectoryEntry teacher = teacherDirectoryRepository.findByFioTeacherIgnoreCase(fio).orElse(null);
                    if (teacher == null) {
                        errors.add("Строка " + (i + 1) + ": педагог не найден в справочнике — " + fio);
                        continue;
                    }
                    LocalDate dismissalDate = teacher.getDismissalDate();
                    LocalDate from = parseDate(readCell(row, 6));
                    LocalDate to = parseDate(readCell(row, 7));
                    if (dismissalDate != null && from != null && to != null) {
                        boolean currentPeriod = !today.isBefore(from) && !today.isAfter(to);
                        if (currentPeriod && !dismissalDate.isAfter(today)) {
                            errors.add("Строка " + (i + 1) + ": педагог уволен и не может быть назначен на текущий период — " + fio);
                            continue;
                        }
                    }
                }

                ManualLoadEntryRequest request = new ManualLoadEntryRequest();
                request.setAcademicYear(academicYear);
                request.setNumberSchoolBuilding(readCell(row, 1));
                request.setClassName(readCell(row, 2));
                request.setSubjectName(readCell(row, 3));
                request.setGroupNameEducationalPlan(emptyToNull(readCell(row, 4)));
                request.setStudyPeriod(parseStudyPeriod(readCell(row, 5)));
                request.setLoadFromDate(parseDate(readCell(row, 6)));
                request.setLoadToDate(parseDate(readCell(row, 7)));
                Integer load = parseInteger(readCell(row, 8));
                request.setLoad(load);
                request.setGroupLoad(request.getGroupNameEducationalPlan() == null ? null : load);
                request.setEducationLevel(parseEducationLevel(readCell(row, 9)));
                request.setFioTeacher(fio);
                try {
                    validate(request);
                } catch (Exception e) {
                    errors.add("Строка " + (i + 1) + ": " + e.getMessage());
                    continue;
                }
                requests.add(request);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать файл импорта нагрузки");
        }
        if (requests.isEmpty()) {
            String details = errors.isEmpty() ? "" : ("\n" + String.join("\n", errors));
            throw new IllegalArgumentException("Импорт отклонён: в файле нет строк для загрузки" + details);
        }
        if (!errors.isEmpty()) {
            log.warn("Импорт нагрузки выполнен частично: пропущено {} строк(и): {}", errors.size(), String.join(" | ", errors));
        }
        return createBulk(requests);
    }

    @Override
    public ManualLoadStatsResponse buildStats(String academicYear, String numberSchoolBuilding, int page, int pageSize) {
        List<CurriculumPlanEntry> curriculum = curriculumPlanService.findAll(academicYear, numberSchoolBuilding);
        List<ManualLoadEntry> manual = findAll(academicYear, numberSchoolBuilding);

        Map<String, String> subjectAreaByName = new HashMap<>();
        subjectCatalogRepository.findAll().forEach(s ->
                subjectAreaByName.put(normalizeToken(s.getSubjectName()), normalizeAreaName(s.getSubjectAreaName()))
        );

        Map<String, Integer> assignedByKey = new HashMap<>();
        for (ManualLoadEntry row : manual) {
            if (row.getFioTeacher() == null || row.getFioTeacher().isBlank()) continue;
            String key = statsKey(row.getClassName(), row.getSubjectName(), row.getStudyPeriod(), row.getEducationLevel(), row.getGroupNameEducationalPlan());
            assignedByKey.merge(key, Math.max(row.getGroupLoad() == null ? row.getLoad() : row.getGroupLoad(), 0), Integer::sum);
        }

        Map<String, ManualLoadStatsResponse.SubjectStat> bySubject = new HashMap<>();
        for (CurriculumPlanEntry row : curriculum) {
            List<CurriculumPlanEntry> expanded = expandForStats(row);
            for (CurriculumPlanEntry item : expanded) {
                String subjectName = normalizeValue(item.getSubjectName());
                if (subjectName.isBlank()) continue;
                String normalizedSubject = normalizeToken(subjectName);
                String area = subjectAreaByName.getOrDefault(normalizedSubject, "Без области");
                ManualLoadStatsResponse.SubjectStat stat = bySubject.computeIfAbsent(normalizedSubject,
                        k -> new ManualLoadStatsResponse.SubjectStat(area, subjectName, 0, 0, 0));
                int planned = Math.max(item.getPlannedHours() == null ? 0 : item.getPlannedHours().intValue(), 0);
                String key = statsKey(item.getClassName(), item.getSubjectName(), item.getStudyPeriod(), item.getEducationLevel(), groupNameForStats(item));
                int assigned = Math.min(planned, assignedByKey.getOrDefault(key, 0));
                stat.setPlanned(stat.getPlanned() + planned);
                stat.setAssigned(stat.getAssigned() + assigned);
            }
        }

        List<ManualLoadStatsResponse.SubjectStat> rows = bySubject.values().stream()
                .peek(r -> r.setUnassigned(Math.max(r.getPlanned() - r.getAssigned(), 0)))
                .sorted(Comparator.comparing(ManualLoadStatsResponse.SubjectStat::getSubjectArea)
                        .thenComparing(ManualLoadStatsResponse.SubjectStat::getSubjectName))
                .toList();

        int safePage = Math.max(page, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), 500);
        int totalRows = rows.size();
        int from = Math.min(safePage * safePageSize, totalRows);
        int to = Math.min(from + safePageSize, totalRows);
        List<ManualLoadStatsResponse.SubjectStat> pagedRows = rows.subList(from, to);

        int totalPlanned = rows.stream().mapToInt(ManualLoadStatsResponse.SubjectStat::getPlanned).sum();
        int totalAssigned = rows.stream().mapToInt(ManualLoadStatsResponse.SubjectStat::getAssigned).sum();
        return new ManualLoadStatsResponse(rows.size(), totalPlanned, totalAssigned, Math.max(totalPlanned - totalAssigned, 0), safePage, safePageSize, totalRows, pagedRows);
    }

    private String normalizeAreaName(String value) {
        String normalized = normalizeValue(value);
        return normalized.isBlank() ? "Без области" : normalized;
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String statsKey(String className, String subjectName, StudyPeriod period, EducationLevel level, String groupName) {
        return String.join("|",
                normalizeToken(ClassNameNormalizer.normalize(className)),
                normalizeToken(subjectName),
                String.valueOf(period == null ? StudyPeriod.YEAR : period),
                String.valueOf(level == null ? EducationLevel.BASIC : level),
                normalizeToken(groupName)
        );
    }

    private String groupNameForStats(CurriculumPlanEntry row) {
        if (row.isSubgroupRequired() && row.getSubgroupCount() != null && row.getSubgroupCount() > 0) {
            return "Группа 1";
        }
        return "";
    }

    private List<CurriculumPlanEntry> expandForStats(CurriculumPlanEntry row) {
        if (!Boolean.TRUE.equals(row.isSubgroupRequired()) || row.getSubgroupCount() == null || row.getSubgroupCount() < 2) {
            return List.of(row);
        }
        List<CurriculumPlanEntry> result = new ArrayList<>();
        CurriculumPlanEntry first = new CurriculumPlanEntry();
        copyForStats(row, first);
        first.setPlannedHours(row.getSubgroup1Hours() != null ? java.math.BigDecimal.valueOf(row.getSubgroup1Hours()) : row.getPlannedHours());
        first.setEducationLevel(row.getSubgroup1EducationLevel() != null ? row.getSubgroup1EducationLevel() : row.getEducationLevel());
        result.add(first);
        CurriculumPlanEntry second = new CurriculumPlanEntry();
        copyForStats(row, second);
        second.setPlannedHours(row.getSubgroup2Hours() != null ? java.math.BigDecimal.valueOf(row.getSubgroup2Hours()) : row.getPlannedHours());
        second.setEducationLevel(row.getSubgroup2EducationLevel() != null ? row.getSubgroup2EducationLevel() : row.getEducationLevel());
        second.setClassName(row.getClassName() + "#G2");
        result.add(second);
        return result;
    }

    private void copyForStats(CurriculumPlanEntry from, CurriculumPlanEntry to) {
        to.setClassName(from.getClassName());
        to.setSubjectName(from.getSubjectName());
        to.setStudyPeriod(from.getStudyPeriod());
        to.setEducationLevel(from.getEducationLevel());
        to.setPlannedHours(from.getPlannedHours());
        to.setSubgroupRequired(from.isSubgroupRequired());
    }

    private List<ManualLoadTemplateRow> buildTemplateRows(String academicYear) {
        List<CurriculumPlanEntry> curriculum = curriculumPlanService.findAll(academicYear).stream()
                .filter(row -> !row.isDeprecated())
                .toList();
        Map<String, ManualLoadEntry> existingByKey = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .collect(java.util.stream.Collectors.toMap(this::manualRowKey, java.util.function.Function.identity(), (a, b) -> a));
        Map<String, ManualLoadEntry> existingBySoftKey = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .collect(java.util.stream.Collectors.toMap(this::manualRowSoftKey, java.util.function.Function.identity(), (a, b) -> a));
        List<ManualLoadTemplateRow> result = new ArrayList<>();
        for (CurriculumPlanEntry row : curriculum) {
            if (row.isSubgroupRequired()) {
                result.add(toTemplateRow(academicYear, row, 1, existingByKey, existingBySoftKey));
                result.add(toTemplateRow(academicYear, row, 2, existingByKey, existingBySoftKey));
            } else {
                result.add(toTemplateRow(academicYear, row, null, existingByKey, existingBySoftKey));
            }
        }
        result.sort(Comparator.comparing(ManualLoadTemplateRow::numberSchoolBuilding)
                .thenComparing(ManualLoadTemplateRow::className)
                .thenComparing(ManualLoadTemplateRow::subjectName)
                .thenComparing(row -> row.groupNameEducationalPlan() == null ? "" : row.groupNameEducationalPlan()));
        return result;
    }

    private ManualLoadTemplateRow toTemplateRow(String academicYear,
                                                CurriculumPlanEntry curriculum,
                                                Integer groupIndex,
                                                Map<String, ManualLoadEntry> existingByKey,
                                                Map<String, ManualLoadEntry> existingBySoftKey) {
        StudyPeriod studyPeriod = curriculum.getStudyPeriod() == null ? StudyPeriod.YEAR : curriculum.getStudyPeriod();
        StudyPeriodSettingService.DateRange range = studyPeriodSettingService.resolveDateRange(academicYear, curriculum.getClassName(), studyPeriod);
        String groupName = groupIndex == null ? null : ("Группа " + groupIndex);
        int loadHours = curriculum.getPlannedHours() == null ? 0 : curriculum.getPlannedHours().intValue();
        EducationLevel level = curriculum.getEducationLevel();
        if (groupIndex != null) {
            loadHours = groupIndex == 1
                    ? (curriculum.getSubgroup1Hours() == null ? loadHours : curriculum.getSubgroup1Hours())
                    : (curriculum.getSubgroup2Hours() == null ? loadHours : curriculum.getSubgroup2Hours());
            level = groupIndex == 1
                    ? (curriculum.getSubgroup1EducationLevel() == null ? level : curriculum.getSubgroup1EducationLevel())
                    : (curriculum.getSubgroup2EducationLevel() == null ? level : curriculum.getSubgroup2EducationLevel());
        }
        ManualLoadTemplateRow template = new ManualLoadTemplateRow(
                academicYear,
                curriculum.getNumberSchoolBuilding(),
                ClassNameNormalizer.normalize(curriculum.getClassName()),
                curriculum.getSubjectName(),
                groupName,
                studyPeriod,
                range.startDate(),
                range.endDate(),
                loadHours,
                level,
                "",
                exportRowKey(academicYear, curriculum.getNumberSchoolBuilding(), curriculum.getClassName(), curriculum.getSubjectName(), groupName, studyPeriod, range.startDate(), range.endDate(), level)
        );
        ManualLoadEntry existing = existingByKey.get(template.rowKey());
        if (existing == null) {
            existing = existingBySoftKey.get(exportRowSoftKey(
                    template.academicYear(),
                    template.numberSchoolBuilding(),
                    template.className(),
                    template.subjectName(),
                    template.groupNameEducationalPlan(),
                    template.studyPeriod(),
                    template.educationLevel()
            ));
        }
        if (existing != null) {
            return new ManualLoadTemplateRow(
                    template.academicYear(),
                    template.numberSchoolBuilding(),
                    template.className(),
                    template.subjectName(),
                    template.groupNameEducationalPlan(),
                    template.studyPeriod(),
                    template.loadFromDate(),
                    template.loadToDate(),
                    template.load(),
                    template.educationLevel(),
                    existing.getFioTeacher(),
                    template.rowKey()
            );
        }
        return template;
    }

    private String manualRowKey(ManualLoadEntry row) {
        return exportRowKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                row.getGroupNameEducationalPlan(),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getLoadFromDate(),
                row.getLoadToDate(),
                row.getEducationLevel()
        );
    }

    private String manualRowSoftKey(ManualLoadEntry row) {
        return exportRowSoftKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                row.getGroupNameEducationalPlan(),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getEducationLevel()
        );
    }

    private String exportRowSoftKey(String year,
                                    String building,
                                    String className,
                                    String subject,
                                    String group,
                                    StudyPeriod studyPeriod,
                                    EducationLevel level) {
        return String.join("|",
                normalizeToken(year),
                normalizeToken(building),
                normalizeToken(ClassNameNormalizer.normalize(className)),
                normalizeToken(subject),
                normalizeToken(group),
                normalizeToken(studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()),
                normalizeToken(level == null ? EducationLevel.BASIC.name() : level.name()));
    }

    private String exportRowKey(String year,
                                String building,
                                String className,
                                String subject,
                                String group,
                                StudyPeriod studyPeriod,
                                LocalDate from,
                                LocalDate to,
                                EducationLevel level) {
        return String.join("|",
                normalizeToken(year),
                normalizeToken(building),
                normalizeToken(ClassNameNormalizer.normalize(className)),
                normalizeToken(subject),
                normalizeToken(group),
                normalizeToken(studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()),
                normalizeToken(from == null ? "" : from.toString()),
                normalizeToken(to == null ? "" : to.toString()),
                normalizeToken(level == null ? EducationLevel.BASIC.name() : level.name()));
    }

    private String normalizeToken(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String readCell(Row row, int index) {
        if (row.getCell(index) == null) return "";
        return switch (row.getCell(index).getCellType()) {
            case STRING -> row.getCell(index).getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(row.getCell(index).getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(row.getCell(index).getBooleanCellValue());
            case FORMULA -> row.getCell(index).toString();
            default -> "";
        };
    }

    private LocalDate parseDate(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        if (value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    private Integer parseInteger(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        if (value.isBlank()) return null;
        return Integer.parseInt(value);
    }

    private StudyPeriod parseStudyPeriod(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || "ГОД".equals(value)) return StudyPeriod.YEAR;
        if ("1П".equals(value)) return StudyPeriod.H1;
        if ("2П".equals(value)) return StudyPeriod.H2;
        return StudyPeriod.valueOf(value);
    }

    private EducationLevel parseEducationLevel(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) return EducationLevel.BASIC;
        if ("БАЗОВЫЙ".equals(value)) return EducationLevel.BASIC;
        if ("УГЛУБЛЁННЫЙ".equals(value) || "УГЛУБЛЕННЫЙ".equals(value)) return EducationLevel.ADVANCED;
        return EducationLevel.valueOf(value);
    }

    private String emptyToNull(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private ManualLoadEntry toEntity(ManualLoadEntryRequest request) {
        validate(request);
        String effectiveAcademicYear = resolveAcademicYearOrDefault(request.getAcademicYear());
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setAcademicYear(effectiveAcademicYear);
        entity.setFioTeacher(request.getFioTeacher().trim());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setSubjectName(request.getSubjectName().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setLoad(request.getLoad());
        entity.setGroupNameEducationalPlan(request.getGroupNameEducationalPlan());
        entity.setGroupLoad(request.getGroupLoad());
        entity.setEducationLevel(request.getEducationLevel());
        entity.setStudyPeriod(resolveStudyPeriod(effectiveAcademicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate()));
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        entity.setContinuityStatus(request.getContinuityStatus() == null ? ContinuityStatus.UNKNOWN : request.getContinuityStatus());
        return entity;
    }

    private CurriculumPlanEntry validateAgainstCurriculum(ManualLoadEntry entry) {
        StudyPeriod effectiveStudyPeriod = resolveStudyPeriod(entry.getAcademicYear(), entry.getClassName(), entry.getStudyPeriod(), entry.getLoadFromDate(), entry.getLoadToDate());
        CurriculumPlanEntry rule = findRuleWithFallback(
                entry.getAcademicYear(),
                entry.getNumberSchoolBuilding().trim(),
                entry.getClassName(),
                entry.getSubjectName(),
                entry.getEducationLevel(),
                effectiveStudyPeriod
        ).orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel() + ", period=" + effectiveStudyPeriod));

        int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();
        if (BigDecimal.valueOf(effectiveLoad).compareTo(rule.getPlannedHours()) > 0) {
            throw new IllegalArgumentException("Load exceeds planned hours for curriculum rule");
        }

        if (rule.isSubgroupRequired()) {
            if (entry.getGroupNameEducationalPlan() == null || entry.getGroupNameEducationalPlan().isBlank()) {
                throw new IllegalArgumentException("groupNameEducationalPlan is required because subgroupRequired=true in curriculum");
            }
        }

        return rule;
    }


    private java.util.Optional<CurriculumPlanEntry> findRuleWithFallback(String academicYear,
                                                                         String numberSchoolBuilding,
                                                                         String className,
                                                                         String subjectName,
                                                                         org.school.personalLoad.model.EducationLevel educationLevel,
                                                                         StudyPeriod effectiveStudyPeriod) {
        java.util.List<StudyPeriod> candidates = new java.util.ArrayList<>();
        candidates.add(effectiveStudyPeriod == null ? StudyPeriod.YEAR : effectiveStudyPeriod);
        candidates.add(StudyPeriod.YEAR);
        candidates.add(StudyPeriod.H1);
        candidates.add(StudyPeriod.H2);
        return candidates.stream()
                .distinct()
                .map(period -> curriculumPlanService.findRule(academicYear, numberSchoolBuilding,
                        ClassNameNormalizer.normalize(className),
                        subjectName.trim(),
                        educationLevel,
                        period))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }

    private StudyPeriod resolveStudyPeriod(String academicYear,
                                           String className,
                                           StudyPeriod explicitStudyPeriod,
                                           java.time.LocalDate loadFromDate,
                                           java.time.LocalDate loadToDate) {
        if (explicitStudyPeriod != null) {
            return explicitStudyPeriod;
        }
        return studyPeriodSettingService.inferStudyPeriod(academicYear, className, loadFromDate, loadToDate);
    }

    private static class SummaryAccumulator {
        private final BigDecimal plannedHours;
        private BigDecimal actualHours;

        private SummaryAccumulator(BigDecimal plannedHours) {
            this.plannedHours = plannedHours == null ? BigDecimal.ZERO : plannedHours;
            this.actualHours = BigDecimal.ZERO;
        }

        private void addActualHours(int hours) {
            this.actualHours = this.actualHours.add(BigDecimal.valueOf(hours));
        }
    }

    private static class RuleKey {
        private final String className;
        private final String subjectName;
        private final org.school.personalLoad.model.EducationLevel educationLevel;
        private final StudyPeriod studyPeriod;

        private RuleKey(String className, String subjectName, org.school.personalLoad.model.EducationLevel educationLevel, StudyPeriod studyPeriod) {
            this.className = className;
            this.subjectName = subjectName;
            this.educationLevel = educationLevel;
            this.studyPeriod = studyPeriod;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RuleKey ruleKey = (RuleKey) o;
            return className.equals(ruleKey.className)
                    && subjectName.equals(ruleKey.subjectName)
                    && educationLevel == ruleKey.educationLevel
                    && studyPeriod == ruleKey.studyPeriod;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(className, subjectName, educationLevel, studyPeriod);
        }
    }

    private record ManualLoadTemplateRow(String academicYear,
                                         String numberSchoolBuilding,
                                         String className,
                                         String subjectName,
                                         String groupNameEducationalPlan,
                                         StudyPeriod studyPeriod,
                                         LocalDate loadFromDate,
                                         LocalDate loadToDate,
                                         Integer load,
                                         EducationLevel educationLevel,
                                         String fioTeacher,
                                         String rowKey) {}

    private void validate(ManualLoadEntryRequest request) {
        if (request.getAcademicYear() == null || request.getAcademicYear().isBlank()) {
            throw new IllegalArgumentException("academicYear is required");
        }
        if (request.getFioTeacher() == null || request.getFioTeacher().isBlank()) {
            throw new IllegalArgumentException("fioTeacher is required");
        }
        if (request.getNumberSchoolBuilding() == null || request.getNumberSchoolBuilding().isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        if (request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (request.getLoad() == null || request.getLoad() <= 0) {
            throw new IllegalArgumentException("load must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required (BASIC or ADVANCED)");
        }
        if (request.getLoadFromDate() == null || request.getLoadToDate() == null) {
            throw new IllegalArgumentException("load period is required: loadFromDate and loadToDate");
        }
        if (request.getLoadFromDate().isAfter(request.getLoadToDate())) {
            throw new IllegalArgumentException("loadFromDate must be before or equal to loadToDate");
        }
    }

    private String resolveAcademicYearOrDefault(String value) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        java.time.LocalDate now = java.time.LocalDate.now();
        int start = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return start + "/" + (start + 1);
    }
}
