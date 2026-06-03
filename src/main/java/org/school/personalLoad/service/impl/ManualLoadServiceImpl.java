package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.ManualLoadBulkRequest;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadHealthResponse;
import org.school.personalLoad.dto.ManualLoadPlanFactSummary;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.SubjectAreaNames;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ContinuityStatus;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualLoadServiceImpl implements ManualLoadService {

    private static final BigDecimal STUDENT_HOUR_MULTIPLIER = new BigDecimal("2.8333333");
    private static final BigDecimal GROUP_BASE_SIZE = BigDecimal.valueOf(25);
    private static final BigDecimal CLASS_LEADERSHIP_PER_STUDENT = BigDecimal.valueOf(500);
    private static final BigDecimal CLASS_LEADERSHIP_BASE = BigDecimal.valueOf(5000);

    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final DatabaseService databaseService;
    private final CurriculumPlanService curriculumPlanService;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SubjectCatalogRepository subjectCatalogRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final SalarySettingsRepository salarySettingsRepository;

    @Override
    public ManualLoadEntry create(ManualLoadEntryRequest request) {
        ManualLoadEntry entity = toEntity(request);
        return manualLoadEntryRepository.save(entity);
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) {
        ManualLoadBulkRequest bulkRequest = new ManualLoadBulkRequest();
        bulkRequest.setRows(requests == null ? List.of() : requests);
        return createBulk(bulkRequest);
    }

    @Override
    @Transactional
    public List<ManualLoadEntry> createBulk(ManualLoadBulkRequest request) {
        List<ManualLoadEntryRequest> requests = request == null || request.getRows() == null ? List.of() : request.getRows();
        java.util.Set<String> explicitAcademicYears = requests.stream()
                .filter(java.util.Objects::nonNull)
                .map(ManualLoadEntryRequest::getAcademicYear)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(year -> !year.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (request != null && request.getAcademicYear() != null && !request.getAcademicYear().isBlank()) {
            explicitAcademicYears.add(request.getAcademicYear().trim());
        }
        List<ManualLoadEntry> entries = requests.stream().map(this::toEntity).toList();
        java.util.Set<String> buildingCodes = entries.stream()
                .map(ManualLoadEntry::getNumberSchoolBuilding)
                .filter(java.util.Objects::nonNull)
                .map(code -> code.trim().toLowerCase())
                .filter(code -> !code.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        String requestBuilding = request == null ? null : trimToNull(request.getNumberSchoolBuilding());
        if (requestBuilding != null) {
            buildingCodes.add(requestBuilding.toLowerCase(java.util.Locale.ROOT));
        }
        String scopeType = normalizeScopeType(request == null ? null : request.getScopeType());
        String campusAddress = request == null ? null : trimToNull(request.getCampusAddress());
        Long schoolBuildingId = request == null ? null : request.getSchoolBuildingId();
        boolean addressScope = "BUILDING_ADDRESS".equals(scopeType) || schoolBuildingId != null || campusAddress != null;
        boolean explicitBuildingGroup = "BUILDING_GROUP".equals(scopeType);

        if (explicitAcademicYears.size() == 1 && addressScope) {
            String academicYear = explicitAcademicYears.iterator().next();
            Long resolvedSchoolBuildingId = resolveSchoolBuildingIdForAddressScope(schoolBuildingId, campusAddress);
            java.util.Set<Long> classIds = scopedClassIds(request, entries);
            validateAddressScopeClassIds(academicYear, resolvedSchoolBuildingId, classIds);
            manualLoadEntryRepository.deleteByAcademicYearAndClassIds(academicYear, classIds);
        } else {
            validateBuildingGroupBulkScope(explicitAcademicYears, buildingCodes, explicitBuildingGroup);
            if (!buildingCodes.isEmpty()) {
                if (explicitAcademicYears.size() == 1) {
                    String academicYear = explicitAcademicYears.iterator().next();
                    manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(academicYear, buildingCodes);
                } else {
                    manualLoadEntryRepository.deleteByBuildingCodes(buildingCodes);
                }
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
        return findAll(academicYear, numberSchoolBuilding, null);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding, String campusAddress) {
        return findAll(academicYear, numberSchoolBuilding, campusAddress, null);
    }

    @Override
    public List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding, String campusAddress, Long schoolBuildingId) {
        Long resolvedSchoolBuildingId = resolveOptionalSchoolBuildingIdForAddressScope(schoolBuildingId, campusAddress);
        if (resolvedSchoolBuildingId != null) {
            return manualLoadEntryRepository.findAllByAcademicYearAndSchoolBuildingId(academicYear, resolvedSchoolBuildingId);
        }
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
        clearByBuilding(academicYear, numberSchoolBuilding, null);
    }

    @Override
    @Transactional
    public void clearByBuilding(String academicYear, String numberSchoolBuilding, String scopeType) {
        if (numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            throw new IllegalArgumentException("building is required");
        }
        String normalizedScopeType = normalizeScopeType(scopeType);
        boolean explicitBuildingGroup = "BUILDING_GROUP".equals(normalizedScopeType);
        validateBuildingGroupDeleteScope(academicYear, numberSchoolBuilding.trim(), explicitBuildingGroup);
        manualLoadEntryRepository.deleteByAcademicYearAndBuildingCodes(
                academicYear,
                java.util.List.of(numberSchoolBuilding.trim().toLowerCase(java.util.Locale.ROOT))
        );
    }

    @Override
    @Transactional
    public void clearByBuildingAddress(String academicYear, String numberSchoolBuilding, String campusAddress) {
        Long schoolBuildingId = resolveSchoolBuildingIdForAddressScope(null, campusAddress);
        clearBySchoolBuilding(academicYear, schoolBuildingId);
    }

    @Override
    @Transactional
    public void clearBySchoolBuilding(String academicYear, Long schoolBuildingId) {
        if (schoolBuildingId == null) {
            throw new IllegalArgumentException("schoolBuildingId is required for BUILDING_ADDRESS scope");
        }
        manualLoadEntryRepository.deleteByAcademicYearAndSchoolBuildingId(academicYear, schoolBuildingId);
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
            header.createCell(12).setCellValue("CLASS_ID");
            header.createCell(13).setCellValue("META_GROUP_ID");

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
                if (row.classId() != null) excelRow.createCell(12).setCellValue(row.classId());
                if (row.metaGroupId() != null) excelRow.createCell(13).setCellValue(row.metaGroupId());
            }

            for (int i = 0; i <= 13; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public byte[] exportFullWorkbook(String academicYear) throws IOException {
        return exportFullWorkbook(academicYear, false);
    }

    @Override
    public byte[] exportFullWorkbookWithSalary(String academicYear) throws IOException {
        return exportFullWorkbook(academicYear, true);
    }

    private byte[] exportFullWorkbook(String academicYear, boolean includeSalary) throws IOException {
        List<ManualLoadEntry> rows = manualLoadEntryRepository.findAllByAcademicYear(academicYear);
        Map<String, TeacherDirectoryEntry> teacherByFio = teacherDirectoryRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> String.valueOf(t.getFioTeacher()).trim().toLowerCase(Locale.ROOT),
                        t -> t,
                        (a, b) -> a
                ));
        Map<String, BigDecimal> subjectCoefficientByName = subjectCatalogRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        s -> normalizeToken(s.getSubjectName()),
                        s -> resolvePositiveCoefficient(s.getSubjectCoefficient()),
                        (a, b) -> a
                ));
        List<ClassroomLeadershipEntry> classEntries = classroomLeadershipRepository.findAllByAcademicYear(academicYear);
        Map<String, List<String>> classLeadershipByTeacher = new HashMap<>();
        Map<String, String> addressByClass = new HashMap<>();
        classEntries.forEach(c -> {
            String key = String.valueOf(c.getFioTeacher()).trim().toLowerCase(Locale.ROOT);
            classLeadershipByTeacher.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getClassName());
            String address = normalizeDisplayValue(c.getCampusAddress());
            if (!address.isBlank()) {
                addressByClass.putIfAbsent(classAddressKey(c.getNumberSchoolBuilding(), c.getClassName()), address);
            }
        });
        Map<String, List<String>> addressesByBuilding = schoolBuildingRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        b -> normalizeToken(b.getCode()),
                        java.util.stream.Collectors.mapping(
                                SchoolBuilding::getAddress,
                                java.util.stream.Collectors.collectingAndThen(
                                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                        ArrayList::new
                                )
                        )
                ));
        Map<String, List<ManualLoadEntry>> rowsByTeacher = rows.stream().collect(java.util.stream.Collectors.groupingBy(
                r -> String.valueOf(r.getFioTeacher()).trim().toLowerCase(Locale.ROOT)
        ));
        Map<String, Integer> classSizeByClass = contingentSnapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .map(snapshot -> contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                s -> normalizeToken(s.getClassName()),
                                java.util.stream.Collectors.summingInt(x -> 1)
                        )))
                .orElseGet(HashMap::new);

        BigDecimal studentHourRate = includeSalary ? resolveStudentHourRate() : SalarySettings.DEFAULT_STUDENT_HOUR_RATE;
        SalarySummary salarySummary = includeSalary
                ? calculateSalarySummary(rows, classEntries, classSizeByClass, subjectCoefficientByName, studentHourRate)
                : SalarySummary.empty();

        Map<String, List<ManualLoadEntry>> byBuilding = rows.stream().collect(java.util.stream.Collectors.groupingBy(
                r -> r.getNumberSchoolBuilding() == null || r.getNumberSchoolBuilding().isBlank() ? "Не закреплены" : r.getNumberSchoolBuilding(),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()
        ));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont(); bold.setBold(true); header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);
            wrap.setAlignment(HorizontalAlignment.CENTER);
            CellStyle money = workbook.createCellStyle();
            money.cloneStyleFrom(wrap);
            money.setDataFormat(workbook.createDataFormat().getFormat("# ##0.00"));

            List<String> sheetOrder = new ArrayList<>(byBuilding.keySet());
            sheetOrder.sort(String::compareToIgnoreCase);
            if (sheetOrder.isEmpty()) {
                Sheet sheet = workbook.createSheet("Нет данных");
                Row emptyHeader = sheet.createRow(0);
                emptyHeader.createCell(0).setCellValue("Нет данных по полной нагрузке за " + academicYear);
                emptyHeader.getCell(0).setCellStyle(header);
                sheet.setColumnWidth(0, 45 * 256);
            }
            if (!rows.isEmpty()) {
                sheetOrder.add("Все педагоги");
            }
            for (String sheetScope : sheetOrder) {
                boolean allTeachersSheet = "Все педагоги".equals(sheetScope);
                String building = sheetScope;
                Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, sheetScope));
                sheet.getPrintSetup().setLandscape(true);
                sheet.setFitToPage(true);
                sheet.getPrintSetup().setFitWidth((short) 1);
                sheet.getPrintSetup().setFitHeight((short) 0);

                Row h = sheet.createRow(0);
                List<String> cols = new ArrayList<>(List.of("ФИО", "Предмет", "Класс", "Группа", "Количество детей", "Часы по предмету", "Период нагрузки", "Часы в корпусе/всего", "Корпус", "Классное руководство"));
                if (includeSalary) {
                    cols.add("За часы");
                    cols.add("Классное руководство, руб.");
                    cols.add("Итого, руб.");
                }
                for (int i = 0; i < cols.size(); i++) { h.createCell(i).setCellValue(cols.get(i)); h.getCell(i).setCellStyle(header); }
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, cols.size() - 1));

                Map<String, int[]> totalsByTeacher = new HashMap<>();
                rows.forEach(r -> {
                    String k = String.valueOf(r.getFioTeacher()).trim().toLowerCase(Locale.ROOT);
                    int[] t = totalsByTeacher.computeIfAbsent(k, x -> new int[]{0, 0, 0, 0}); // scopedH1,scopedH2,totalH1,totalH2
                    int load = r.getLoad() == null ? 0 : r.getLoad();
                    boolean isScoped = allTeachersSheet || building.equals(r.getNumberSchoolBuilding());
                    StudyPeriod period = r.getStudyPeriod() == null ? StudyPeriod.YEAR : r.getStudyPeriod();
                    if (period == StudyPeriod.H1) {
                        t[2] += load;
                        if (isScoped) t[0] += load;
                    } else if (period == StudyPeriod.H2) {
                        t[3] += load;
                        if (isScoped) t[1] += load;
                    } else {
                        t[2] += load;
                        t[3] += load;
                        if (isScoped) {
                            t[0] += load;
                            t[1] += load;
                        }
                    }
                });

                List<ManualLoadEntry> scopeRows = allTeachersSheet ? rows : byBuilding.getOrDefault(building, List.of());
                Set<String> teacherKeysInScope = scopeRows.stream()
                        .map(row -> String.valueOf(row.getFioTeacher()).trim().toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                List<ManualLoadEntry> buildingRows = rows.stream()
                        .filter(row -> teacherKeysInScope.contains(String.valueOf(row.getFioTeacher()).trim().toLowerCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                buildingRows.sort(
                        Comparator.comparing(ManualLoadEntry::getFioTeacher, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(row -> allTeachersSheet || building.equals(row.getNumberSchoolBuilding()) ? 0 : 1)
                                .thenComparing(ManualLoadEntry::getSubjectName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(ManualLoadEntry::getClassName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                );
                int rowNum = 1;
                int teacherStart = rowNum;
                String currentTeacher = null;
                for (int i = 0; i < buildingRows.size(); i++) {
                    ManualLoadEntry e = buildingRows.get(i);
                    String fio = String.valueOf(e.getFioTeacher() == null ? "" : e.getFioTeacher()).trim();
                    String key = fio.toLowerCase(Locale.ROOT);
                    int[] t = totalsByTeacher.getOrDefault(key, new int[]{0,0,0,0});
                    String classLeadership = String.join(", ", classLeadershipByTeacher.getOrDefault(key, List.of()));

                    if (!Objects.equals(currentTeacher, key)) {
                        if (currentTeacher != null && rowNum - 1 > teacherStart) {
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 0, 0));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 7, 7));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 9, 9));
                            if (includeSalary) {
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 10, 10));
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 11, 11));
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 12, 12));
                            }
                        }
                        currentTeacher = key;
                        teacherStart = rowNum;
                    }

                    int subjectHours = e.getGroupLoad() != null ? e.getGroupLoad() : (e.getLoad() == null ? 0 : e.getLoad());
                    String periodLabel = e.getStudyPeriod() == StudyPeriod.H1 ? "1П"
                            : e.getStudyPeriod() == StudyPeriod.H2 ? "2П" : "ГОД";
                    String hoursSummary = formatScopedTotalHours(t[0], t[1], t[2], t[3]);
                    int classSize = classSizeByClass.getOrDefault(normalizeToken(e.getClassName()), 30);
                    String group = String.valueOf(e.getGroupNameEducationalPlan() == null ? "" : e.getGroupNameEducationalPlan()).toLowerCase(Locale.ROOT);
                    int firstGroupSize = (classSize + 1) / 2;
                    int secondGroupSize = classSize - firstGroupSize;
                    int childrenCount = classSize;
                    if (!group.isBlank()) {
                        if (group.contains("2")) {
                            childrenCount = secondGroupSize;
                        } else if (group.contains("1")) {
                            childrenCount = firstGroupSize;
                        }
                    }

                    Row r = sheet.createRow(rowNum++);
                    r.createCell(0).setCellValue(fio);
                    r.createCell(1).setCellValue(String.valueOf(e.getSubjectName() == null ? "" : e.getSubjectName()));
                    r.createCell(2).setCellValue(String.valueOf(e.getClassName() == null ? "" : e.getClassName()));
                    r.createCell(3).setCellValue(String.valueOf(e.getGroupNameEducationalPlan() == null ? "" : e.getGroupNameEducationalPlan()));
                    r.createCell(4).setCellValue(childrenCount);
                    r.createCell(5).setCellValue(subjectHours);
                    r.createCell(6).setCellValue(periodLabel);
                    r.createCell(7).setCellValue(hoursSummary);
                    String rowAddress = resolveRowAddress(e, addressByClass, addressesByBuilding);
                    String rowBuilding = normalizeDisplayValue(e.getNumberSchoolBuilding());
                    r.createCell(8).setCellValue(rowBuilding + (rowAddress.isBlank() ? "" : "\n" + rowAddress));
                    r.createCell(9).setCellValue(classLeadership);
                    if (includeSalary) {
                        SalaryTotals salary = salarySummary.byTeacher().getOrDefault(key, SalaryTotals.empty());
                        r.createCell(10).setCellValue(moneyValue(salary.hourSalary()));
                        r.createCell(11).setCellValue(moneyValue(salary.classLeadershipSalary()));
                        r.createCell(12).setCellValue(moneyValue(salary.total()));
                    }
                    for (int c = 0; c <= (includeSalary ? 12 : 9); c++) r.getCell(c).setCellStyle(c >= 10 ? money : wrap);
                    if (i == buildingRows.size() - 1 && rowNum - 1 > teacherStart) {
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 0, 0));
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 7, 7));
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 9, 9));
                        if (includeSalary) {
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 10, 10));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 11, 11));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 12, 12));
                        }
                    }
                }
                sheet.setColumnWidth(0, 13 * 256);
                sheet.setColumnWidth(1, 20 * 256);
                sheet.setColumnWidth(2, 10 * 256);
                sheet.setColumnWidth(3, 12 * 256);
                sheet.setColumnWidth(4, 11 * 256);
                sheet.setColumnWidth(5, 11 * 256);
                sheet.setColumnWidth(6, 11 * 256);
                sheet.setColumnWidth(7, 18 * 256);
                sheet.setColumnWidth(8, 26 * 256);
                sheet.setColumnWidth(9, 16 * 256);
                if (includeSalary) {
                    sheet.setColumnWidth(10, 12 * 256);
                    sheet.setColumnWidth(11, 18 * 256);
                    sheet.setColumnWidth(12, 12 * 256);
                }
            }
            if (includeSalary) {
                createSalarySummarySheet(workbook, salarySummary, header, money);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String formatScopedTotalHours(int scopedH1, int scopedH2, int totalH1, int totalH2) {
        if (scopedH1 == totalH1 && scopedH2 == totalH2) {
            return formatHalfHours(totalH1, totalH2);
        }
        if (scopedH1 == scopedH2 && totalH1 == totalH2) {
            return scopedH1 + "/" + totalH1;
        }
        return "1П: " + scopedH1 + "/" + totalH1 + "\n"
                + "2П: " + scopedH2 + "/" + totalH2;
    }

    private String formatHalfHours(int h1, int h2) {
        return h1 == h2 ? String.valueOf(h1) : h1 + "/" + h2;
    }

    private String teacherAddresses(List<ManualLoadEntry> teacherRows,
                                    Map<String, String> addressByClass,
                                    Map<String, List<String>> addressesByBuilding) {
        return teacherRows.stream()
                .map(row -> resolveRowAddress(row, addressByClass, addressesByBuilding))
                .filter(address -> !address.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String resolveRowAddress(ManualLoadEntry row,
                                     Map<String, String> addressByClass,
                                     Map<String, List<String>> addressesByBuilding) {
        String address = addressByClass.getOrDefault(classAddressKey(row.getNumberSchoolBuilding(), row.getClassName()), "");
        if (!address.isBlank()) {
            return address;
        }
        List<String> buildingAddresses = addressesByBuilding.getOrDefault(normalizeToken(row.getNumberSchoolBuilding()), List.of()).stream()
                .map(this::normalizeDisplayValue)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return buildingAddresses.size() == 1 ? buildingAddresses.get(0) : "";
    }

    private String classAddressKey(String building, String className) {
        return normalizeToken(building) + "|" + normalizeToken(ClassNameNormalizer.normalize(className));
    }

    private String normalizeDisplayValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private SalarySummary calculateSalarySummary(List<ManualLoadEntry> rows,
                                                 List<ClassroomLeadershipEntry> classEntries,
                                                 Map<String, Integer> classSizeByClass,
                                                 Map<String, BigDecimal> subjectCoefficientByName,
                                                 BigDecimal studentHourRate) {
        Map<String, SalaryTotals> byTeacher = new HashMap<>();
        Map<String, SalaryTotals> byBuilding = new HashMap<>();
        SalaryTotals complex = new SalaryTotals();

        for (ManualLoadEntry row : rows) {
            if (!isFirstHalfSalaryRow(row)) {
                continue;
            }
            String building = buildingKey(row.getNumberSchoolBuilding());
            String teacher = String.valueOf(row.getFioTeacher()).trim().toLowerCase(Locale.ROOT);
            BigDecimal hourSalary = calculateHourSalary(row, classSizeByClass, subjectCoefficientByName, studentHourRate);
            byTeacher.computeIfAbsent(teacher, key -> new SalaryTotals()).addHourSalary(hourSalary);
            byBuilding.computeIfAbsent(building, key -> new SalaryTotals()).addHourSalary(hourSalary);
            complex.addHourSalary(hourSalary);
        }

        for (ClassroomLeadershipEntry entry : classEntries) {
            String teacher = String.valueOf(entry.getFioTeacher()).trim().toLowerCase(Locale.ROOT);
            if (teacher.isBlank()) {
                continue;
            }
            String building = buildingKey(entry.getNumberSchoolBuilding());
            int classSize = classSizeByClass.getOrDefault(normalizeToken(entry.getClassName()), 30);
            BigDecimal leadershipSalary = CLASS_LEADERSHIP_PER_STUDENT
                    .multiply(BigDecimal.valueOf(classSize))
                    .add(CLASS_LEADERSHIP_BASE);
            byTeacher.computeIfAbsent(teacher, key -> new SalaryTotals()).addClassLeadershipSalary(leadershipSalary);
            byBuilding.computeIfAbsent(building, key -> new SalaryTotals()).addClassLeadershipSalary(leadershipSalary);
            complex.addClassLeadershipSalary(leadershipSalary);
        }

        return new SalarySummary(byTeacher, byBuilding, complex);
    }

    private boolean isFirstHalfSalaryRow(ManualLoadEntry row) {
        return row.getStudyPeriod() != StudyPeriod.H2;
    }

    private BigDecimal calculateHourSalary(ManualLoadEntry row,
                                           Map<String, Integer> classSizeByClass,
                                           Map<String, BigDecimal> subjectCoefficientByName,
                                           BigDecimal studentHourRate) {
        int classSize = classSizeByClass.getOrDefault(normalizeToken(row.getClassName()), 30);
        String group = String.valueOf(row.getGroupNameEducationalPlan() == null ? "" : row.getGroupNameEducationalPlan()).toLowerCase(Locale.ROOT);
        int firstGroupSize = (classSize + 1) / 2;
        int secondGroupSize = classSize - firstGroupSize;
        int childrenCount = classSize;
        if (!group.isBlank()) {
            if (group.contains("2")) {
                childrenCount = secondGroupSize;
            } else if (group.contains("1")) {
                childrenCount = firstGroupSize;
            }
        }
        int safeChildrenCount = Math.max(childrenCount, 1);
        int subjectHours = row.getGroupLoad() != null ? row.getGroupLoad() : (row.getLoad() == null ? 0 : row.getLoad());
        BigDecimal coefficient = subjectCoefficientByName.getOrDefault(normalizeToken(row.getSubjectName()), BigDecimal.ONE);
        BigDecimal result = studentHourRate
                .multiply(BigDecimal.valueOf(safeChildrenCount))
                .multiply(BigDecimal.valueOf(Math.max(subjectHours, 0)))
                .multiply(STUDENT_HOUR_MULTIPLIER)
                .multiply(coefficient);
        if (!group.isBlank()) {
            result = result.multiply(GROUP_BASE_SIZE).divide(BigDecimal.valueOf(safeChildrenCount), 10, RoundingMode.HALF_UP);
        }
        return result;
    }

    private BigDecimal resolveStudentHourRate() {
        return salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)
                .map(SalarySettings::getStudentHourRate)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .orElse(SalarySettings.DEFAULT_STUDENT_HOUR_RATE);
    }

    private BigDecimal resolvePositiveCoefficient(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    private String buildingKey(String building) {
        String normalized = String.valueOf(building == null ? "" : building).trim();
        return normalized.isBlank() ? "Не закреплены" : normalized;
    }

    private double moneyValue(BigDecimal value) {
        return value == null ? 0D : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void createSalarySummarySheet(Workbook workbook, SalarySummary salarySummary, CellStyle header, CellStyle money) {
        Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, "Свод ЗП"));
        Row h = sheet.createRow(0);
        List<String> cols = List.of("Корпус", "За часы", "Классное руководство", "Итого");
        for (int i = 0; i < cols.size(); i++) {
            h.createCell(i).setCellValue(cols.get(i));
            h.getCell(i).setCellStyle(header);
        }
        int rowNum = 1;
        List<String> buildings = new ArrayList<>(salarySummary.byBuilding().keySet());
        buildings.sort(String::compareToIgnoreCase);
        for (String building : buildings) {
            SalaryTotals totals = salarySummary.byBuilding().getOrDefault(building, SalaryTotals.empty());
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(building);
            row.createCell(1).setCellValue(moneyValue(totals.hourSalary()));
            row.createCell(2).setCellValue(moneyValue(totals.classLeadershipSalary()));
            row.createCell(3).setCellValue(moneyValue(totals.total()));
            for (int c = 1; c <= 3; c++) row.getCell(c).setCellStyle(money);
        }
        Row total = sheet.createRow(rowNum);
        total.createCell(0).setCellValue("Итого по комплексу");
        total.getCell(0).setCellStyle(header);
        total.createCell(1).setCellValue(moneyValue(salarySummary.complex().hourSalary()));
        total.createCell(2).setCellValue(moneyValue(salarySummary.complex().classLeadershipSalary()));
        total.createCell(3).setCellValue(moneyValue(salarySummary.complex().total()));
        for (int c = 1; c <= 3; c++) total.getCell(c).setCellStyle(money);
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 12 * 256);
    }

    private record SalarySummary(Map<String, SalaryTotals> byTeacher,
                                 Map<String, SalaryTotals> byBuilding,
                                 SalaryTotals complex) {
        static SalarySummary empty() {
            return new SalarySummary(Map.of(), Map.of(), SalaryTotals.empty());
        }
    }

    private static class SalaryTotals {
        private BigDecimal hourSalary = BigDecimal.ZERO;
        private BigDecimal classLeadershipSalary = BigDecimal.ZERO;

        static SalaryTotals empty() {
            return new SalaryTotals();
        }

        void addHourSalary(BigDecimal value) {
            hourSalary = hourSalary.add(value == null ? BigDecimal.ZERO : value);
        }

        void addClassLeadershipSalary(BigDecimal value) {
            classLeadershipSalary = classLeadershipSalary.add(value == null ? BigDecimal.ZERO : value);
        }

        BigDecimal hourSalary() {
            return hourSalary;
        }

        BigDecimal classLeadershipSalary() {
            return classLeadershipSalary;
        }

        BigDecimal total() {
            return hourSalary.add(classLeadershipSalary);
        }
    }

    private String uniqueSheetName(Workbook workbook, String rawName) {
        String base = rawName == null || rawName.isBlank() ? "Не закреплены" : rawName.trim();
        base = WorkbookUtil.createSafeSheetName(base);
        if (base.isBlank()) {
            base = "Лист";
        }
        base = truncateSheetName(base, 31);
        String candidate = base;
        int counter = 2;
        while (workbook.getSheetIndex(candidate) >= 0) {
            String suffix = " (" + counter++ + ")";
            candidate = truncateSheetName(base, 31 - suffix.length()) + suffix;
        }
        return candidate;
    }

    private String truncateSheetName(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(1, maxLength));
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
                request.setClassId(parseLong(readCell(row, 12)));
                request.setMetaGroupId(parseLong(readCell(row, 13)));
                try {
                    validate(request);
                    if (!resolveImportForeignKeys(academicYear, request, i + 1)) {
                        log.warn("Строка {} импорта нагрузки пропущена: ordinary curriculum row is a metagroup member and must not create duplicate manual load", i + 1);
                        continue;
                    }
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
            throw new IllegalArgumentException("Импорт отклонён: " + String.join(" | ", errors));
        }
        return createBulk(requests);
    }

    @Override
    public ManualLoadStatsResponse buildStats(String academicYear, String numberSchoolBuilding, int page, int pageSize) {
        List<CurriculumPlanEntry> curriculum = curriculumPlanService.findAll(academicYear, numberSchoolBuilding).stream()
                .filter(this::contributesToManualLoad)
                .toList();
        List<ManualLoadEntry> manual = findAll(academicYear, numberSchoolBuilding);

        Map<String, String> subjectAreaByName = new HashMap<>();
        subjectCatalogRepository.findAll().forEach(s ->
                subjectAreaByName.put(normalizeToken(s.getSubjectName()), normalizeAreaName(s.getSubjectAreaName()))
        );

        Map<String, Integer> assignedByKey = new HashMap<>();
        Map<String, Integer> vacancyByKey = new HashMap<>();
        for (ManualLoadEntry row : manual) {
            if (row.getFioTeacher() == null || row.getFioTeacher().isBlank()) continue;
            String key = statsKey(row.getClassName(), row.getSubjectName(), row.getStudyPeriod(), row.getEducationLevel(), row.getGroupNameEducationalPlan());
            int loadValue = Math.max(row.getGroupLoad() == null ? row.getLoad() : row.getGroupLoad(), 0);
            assignedByKey.merge(key, loadValue, Integer::sum);
            if ("Вакансия".equalsIgnoreCase(normalizeValue(row.getFioTeacher()))) {
                vacancyByKey.merge(key, loadValue, Integer::sum);
            }
        }

        Map<String, ManualLoadStatsResponse.SubjectStat> bySubject = new HashMap<>();
        for (CurriculumPlanEntry row : curriculum) {
            List<CurriculumPlanEntry> expanded = expandForStats(row);
            for (CurriculumPlanEntry item : expanded) {
                String subjectName = normalizeValue(item.getSubjectName());
                if (subjectName.isBlank()) continue;
                String normalizedSubject = normalizeToken(subjectName);
                String area = subjectAreaByName.getOrDefault(normalizedSubject, SubjectAreaNames.defaultArea());
                ManualLoadStatsResponse.SubjectStat stat = bySubject.computeIfAbsent(normalizedSubject,
                        k -> new ManualLoadStatsResponse.SubjectStat(area, subjectName, 0, 0, 0, 0));
                int planned = Math.max(item.getPlannedHours() == null ? 0 : item.getPlannedHours().intValue(), 0);
                String key = statsKey(item.getClassName(), item.getSubjectName(), item.getStudyPeriod(), item.getEducationLevel(), groupNameForStats(item));
                int assigned = Math.min(planned, assignedByKey.getOrDefault(key, 0));
                int vacancy = Math.min(planned, vacancyByKey.getOrDefault(key, 0));
                stat.setPlanned(stat.getPlanned() + planned);
                stat.setAssigned(stat.getAssigned() + assigned);
                stat.setVacancy(stat.getVacancy() + Math.min(vacancy, assigned));
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

    @Override
    public ManualLoadHealthResponse buildHealth(String academicYear, String numberSchoolBuilding) {
        List<CurriculumPlanEntry> curriculum = curriculumPlanService.findAll(academicYear, numberSchoolBuilding).stream()
                .filter(this::contributesToManualLoad)
                .toList();
        List<ManualLoadEntry> manual = findAll(academicYear, numberSchoolBuilding);
        java.util.Set<String> assignedKeys = manual.stream()
                .map(this::healthSoftKey)
                .collect(java.util.stream.Collectors.toSet());
        int unassignedHours = curriculum.stream()
                .filter(row -> !assignedKeys.contains(healthSoftKey(row)))
                .mapToInt(row -> row.getPlannedHours() == null ? 0 : row.getPlannedHours().intValue())
                .sum();
        int orphanedCount = (int) manual.stream().filter(ManualLoadEntry::isOrphaned).count();
        return new ManualLoadHealthResponse(unassignedHours, orphanedCount);
    }

    private String healthSoftKey(ManualLoadEntry row) {
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

    private String healthSoftKey(CurriculumPlanEntry row) {
        return exportRowSoftKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                row.isSubgroupRequired() ? "Группа 1" : "",
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getEducationLevel()
        );
    }

    private String normalizeAreaName(String value) {
        String normalized = normalizeValue(value);
        return normalized.isBlank() ? SubjectAreaNames.defaultArea() : normalized;
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
                .filter(this::contributesToManualLoad)
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
                manualClassId(curriculum),
                manualMetaGroupId(curriculum),
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
                    template.classId(),
                    template.metaGroupId(),
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

    private java.util.Set<Long> scopedClassIds(ManualLoadBulkRequest request, List<ManualLoadEntry> entries) {
        java.util.Set<Long> classIds = new java.util.LinkedHashSet<>();
        if (request != null && request.getClassIds() != null) {
            request.getClassIds().stream().filter(java.util.Objects::nonNull).forEach(classIds::add);
        }
        entries.stream().map(ManualLoadEntry::getClassId).filter(java.util.Objects::nonNull).forEach(classIds::add);
        return classIds;
    }

    private void validateAddressScopeClassIds(String academicYear, Long schoolBuildingId, java.util.Set<Long> classIds) {
        if (academicYear == null || academicYear.isBlank() || schoolBuildingId == null) {
            throw new IllegalArgumentException("academicYear and schoolBuildingId are required for BUILDING_ADDRESS scope");
        }
        if (classIds == null || classIds.isEmpty()) {
            throw new IllegalArgumentException("classIds are required for BUILDING_ADDRESS scope");
        }
        java.util.Map<Long, ClassroomLeadershipEntry> classesById = classroomLeadershipRepository.findAllById(classIds).stream()
                .collect(java.util.stream.Collectors.toMap(ClassroomLeadershipEntry::getId, java.util.function.Function.identity()));
        java.util.List<Long> invalidIds = classIds.stream()
                .filter(id -> !classBelongsToAddressScope(classesById.get(id), academicYear, schoolBuildingId))
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new IllegalArgumentException("classIds do not belong to selected schoolBuildingId=" + schoolBuildingId + ": " + invalidIds);
        }
    }

    private boolean classBelongsToAddressScope(ClassroomLeadershipEntry entry, String academicYear, Long schoolBuildingId) {
        return entry != null
                && normalizeToken(entry.getAcademicYear()).equals(normalizeToken(academicYear))
                && java.util.Objects.equals(entry.getSchoolBuildingId(), schoolBuildingId);
    }

    private Long resolveOptionalSchoolBuildingIdForAddressScope(Long schoolBuildingId, String campusAddress) {
        if (schoolBuildingId != null) {
            return schoolBuildingId;
        }
        if (campusAddress == null || campusAddress.isBlank()) {
            return null;
        }
        return resolveSchoolBuildingIdForAddressScope(null, campusAddress);
    }

    private Long resolveSchoolBuildingIdForAddressScope(Long schoolBuildingId, String campusAddress) {
        if (schoolBuildingId != null) {
            return schoolBuildingId;
        }
        if (campusAddress == null || campusAddress.isBlank()) {
            throw new IllegalArgumentException("schoolBuildingId is required for BUILDING_ADDRESS scope");
        }
        java.util.List<SchoolBuilding> matches = schoolBuildingRepository.findAll().stream()
                .filter(building -> normalizeToken(building.getAddress()).equals(normalizeToken(campusAddress)))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("schoolBuildingId is required: physical site not found by campusAddress=" + campusAddress);
        }
        java.util.List<Long> ids = matches.stream().map(SchoolBuilding::getId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.size() != 1) {
            throw new IllegalArgumentException("schoolBuildingId is ambiguous for campusAddress=" + campusAddress + ": " + ids);
        }
        return ids.get(0);
    }

    private void validateBuildingGroupBulkScope(java.util.Set<String> academicYears, java.util.Set<String> buildingCodes, boolean explicitBuildingGroup) {
        if (buildingCodes == null || buildingCodes.isEmpty() || academicYears == null || academicYears.size() != 1) {
            return;
        }
        String academicYear = academicYears.iterator().next();
        java.util.List<String> unsafeBuildings = buildingCodes.stream()
                .filter(building -> buildingHasMultipleAddresses(academicYear, building))
                .filter(building -> !explicitBuildingGroup)
                .toList();
        if (!unsafeBuildings.isEmpty()) {
            throw new IllegalArgumentException("Manual load bulk save for multi-address building requires campusAddress/classIds or explicit scopeType=BUILDING_GROUP: " + String.join(", ", unsafeBuildings));
        }
    }

    private void validateBuildingGroupDeleteScope(String academicYear, String numberSchoolBuilding, boolean explicitBuildingGroup) {
        if (!explicitBuildingGroup && buildingHasMultipleAddresses(academicYear, numberSchoolBuilding)) {
            throw new IllegalArgumentException("Manual load delete for multi-address building requires campusAddress or explicit scopeType=BUILDING_GROUP");
        }
    }

    private boolean buildingHasMultipleAddresses(String academicYear, String numberSchoolBuilding) {
        if (academicYear == null || academicYear.isBlank() || numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            return false;
        }
        return classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> normalizeToken(entry.getNumberSchoolBuilding()).equals(normalizeToken(numberSchoolBuilding)))
                .map(ClassroomLeadershipEntry::getCampusAddress)
                .map(this::normalizeToken)
                .filter(address -> !address.isBlank())
                .distinct()
                .limit(2)
                .count() > 1;
    }

    private String normalizeScopeType(String scopeType) {
        return String.valueOf(scopeType == null ? "" : scopeType).trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String singleBuildingCode(java.util.Set<String> buildingCodes) {
        return buildingCodes == null || buildingCodes.size() != 1 ? null : buildingCodes.iterator().next();
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

    private Long parseLong(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        if (value.isBlank()) return null;
        return new BigDecimal(value).longValue();
    }

    private boolean resolveImportForeignKeys(String academicYear, ManualLoadEntryRequest request, int rowNumber) {
        StudyPeriod period = resolveStudyPeriod(academicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate());
        String subjectName = request.getSubjectName() == null ? "" : request.getSubjectName().trim();
        if (isExplicitMetaGroupRequest(request)) {
            CurriculumPlanEntry rule = request.getMetaGroupId() == null
                    ? resolveUniqueLegacyCurriculumRule(academicYear, request, period, true, rowNumber)
                    : findRuleByMetaGroupId(academicYear, request.getMetaGroupId(), subjectName, request.getEducationLevel(), period)
                    .orElseThrow(() -> new IllegalArgumentException("не найдено curriculum-правило метагруппы для meta_group_id=" + request.getMetaGroupId()));
            if (rule.getMetaGroupId() == null) {
                throw new IllegalArgumentException("meta_group_id не найден для строки метагруппы " + request.getClassName());
            }
            request.setClassId(null);
            request.setMetaGroupId(rule.getMetaGroupId());
            return true;
        }

        Long classId = request.getClassId();
        if (classId == null) {
            classId = resolveUniqueLegacyClassId(academicYear, request, rowNumber);
            request.setClassId(classId);
        }
        Long resolvedClassId = classId;
        CurriculumPlanEntry rule = findRuleByClassId(academicYear, resolvedClassId, subjectName, request.getEducationLevel(), period)
                .orElseThrow(() -> new IllegalArgumentException("не найдено curriculum-правило обычного класса для class_id=" + resolvedClassId));
        if (!contributesToManualLoad(rule)) {
            return false;
        }
        request.setMetaGroupId(null);
        return true;
    }

    private Long resolveUniqueLegacyClassId(String academicYear, ManualLoadEntryRequest request, int rowNumber) {
        String building = request.getNumberSchoolBuilding() == null ? "" : request.getNumberSchoolBuilding().trim();
        String className = ClassNameNormalizer.normalize(request.getClassName());
        java.util.List<ClassroomLeadershipEntry> matches = classroomLeadershipRepository
                .findAllByAcademicYearAndNumberSchoolBuildingAndClassName(academicYear, building, className);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("не найден class_id для legacy-строки класса " + building + " / " + className);
        }
        java.util.List<Long> ids = matches.stream()
                .map(ClassroomLeadershipEntry::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.size() != 1) {
            throw new IllegalArgumentException("неоднозначный class_id для legacy-строки класса " + building + " / " + className + ": " + ids);
        }
        return ids.get(0);
    }

    private CurriculumPlanEntry resolveUniqueLegacyCurriculumRule(String academicYear,
                                                                 ManualLoadEntryRequest request,
                                                                 StudyPeriod effectiveStudyPeriod,
                                                                 boolean explicitMetaGroup,
                                                                 int rowNumber) {
        String building = request.getNumberSchoolBuilding() == null ? "" : request.getNumberSchoolBuilding().trim();
        String className = ClassNameNormalizer.normalize(request.getClassName());
        String subjectName = request.getSubjectName() == null ? "" : request.getSubjectName().trim();
        java.util.List<CurriculumPlanEntry> matches = candidateStudyPeriods(effectiveStudyPeriod).stream()
                .flatMap(period -> curriculumPlanEntryRepository
                        .findAllByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                academicYear, building, className, subjectName, request.getEducationLevel(), period)
                        .stream())
                .filter(row -> explicitMetaGroup ? isExplicitMetaGroupRow(row) : !isExplicitMetaGroupRow(row))
                .distinct()
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("не найдено FK-соответствие curriculum для legacy-строки " + building + " / " + className + " / " + subjectName);
        }
        java.util.List<Long> relationIds = matches.stream()
                .map(row -> explicitMetaGroup ? row.getMetaGroupId() : row.getClassId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (relationIds.size() != 1 || matches.size() > 1) {
            throw new IllegalArgumentException("неоднозначное FK-соответствие curriculum для legacy-строки " + building + " / " + className + " / " + subjectName + ": " + relationIds);
        }
        return matches.get(0);
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleByClassId(String academicYear, Long classId, String subjectName, EducationLevel educationLevel, StudyPeriod effectiveStudyPeriod) {
        return candidateStudyPeriods(effectiveStudyPeriod).stream()
                .map(period -> curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                        academicYear, classId, subjectName, educationLevel, period))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleByMetaGroupId(String academicYear, Long metaGroupId, String subjectName, EducationLevel educationLevel, StudyPeriod effectiveStudyPeriod) {
        return candidateStudyPeriods(effectiveStudyPeriod).stream()
                .map(period -> curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                        academicYear, metaGroupId, subjectName, educationLevel, period))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
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

    private Long resolveClassId(String academicYear, ManualLoadEntryRequest request) {
        if (request.getClassId() != null) {
            return request.getClassId();
        }
        if (request.getNumberSchoolBuilding() == null || request.getClassName() == null) {
            return null;
        }
        return classroomLeadershipRepository
                .findByAcademicYearAndNumberSchoolBuildingAndClassName(
                        academicYear,
                        request.getNumberSchoolBuilding().trim(),
                        ClassNameNormalizer.normalize(request.getClassName())
                )
                .map(ClassroomLeadershipEntry::getId)
                .orElse(null);
    }

    private ManualLoadEntry toEntity(ManualLoadEntryRequest request) {
        validate(request);
        String effectiveAcademicYear = resolveAcademicYearOrDefault(request.getAcademicYear());
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setAcademicYear(effectiveAcademicYear);
        entity.setFioTeacher(request.getFioTeacher().trim());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        boolean explicitMetaGroup = isExplicitMetaGroupRequest(request);
        entity.setClassId(explicitMetaGroup ? null : resolveClassId(effectiveAcademicYear, request));
        entity.setMetaGroupId(explicitMetaGroup ? resolveMetaGroupId(effectiveAcademicYear, request) : null);
        SubjectCatalogEntry subject = subjectCatalogRepository.findAll().stream()
                .filter(s -> s.getSubjectName().equalsIgnoreCase(request.getSubjectName().trim()))
                .findFirst()
                .orElse(null);
        entity.setSubject(subject);
        entity.setSubjectName(subject == null ? request.getSubjectName().trim() : subject.getSubjectName());
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
        CurriculumPlanEntry rule = findRuleWithFallback(entry, effectiveStudyPeriod)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel() + ", period=" + effectiveStudyPeriod));

        int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();
        BigDecimal allowedHours = resolveAllowedHours(rule, entry.getGroupNameEducationalPlan());
        if (BigDecimal.valueOf(effectiveLoad).compareTo(allowedHours) > 0) {
            throw new IllegalArgumentException("Load exceeds planned hours for curriculum rule");
        }

        if (rule.isSubgroupRequired()) {
            if (entry.getGroupNameEducationalPlan() == null || entry.getGroupNameEducationalPlan().isBlank()) {
                throw new IllegalArgumentException("groupNameEducationalPlan is required because subgroupRequired=true in curriculum");
            }
        }

        return rule;
    }

    private BigDecimal resolveAllowedHours(CurriculumPlanEntry rule, String groupNameEducationalPlan) {
        if (!Boolean.TRUE.equals(rule.isSubgroupRequired())) {
            return rule.getPlannedHours() == null ? BigDecimal.ZERO : rule.getPlannedHours();
        }
        String group = String.valueOf(groupNameEducationalPlan == null ? "" : groupNameEducationalPlan).toLowerCase(java.util.Locale.ROOT);
        if (group.contains("1")) {
            return rule.getSubgroup1Hours() == null ? (rule.getPlannedHours() == null ? BigDecimal.ZERO : rule.getPlannedHours()) : BigDecimal.valueOf(rule.getSubgroup1Hours());
        }
        if (group.contains("2")) {
            return rule.getSubgroup2Hours() == null ? (rule.getPlannedHours() == null ? BigDecimal.ZERO : rule.getPlannedHours()) : BigDecimal.valueOf(rule.getSubgroup2Hours());
        }
        return rule.getPlannedHours() == null ? BigDecimal.ZERO : rule.getPlannedHours();
    }


    private boolean contributesToManualLoad(CurriculumPlanEntry row) {
        if (row == null) {
            return false;
        }
        if (isExplicitMetaGroupRow(row)) {
            return true;
        }
        return !row.isMetaGroup();
    }

    private boolean isExplicitMetaGroupRow(CurriculumPlanEntry row) {
        return row.getMetaGroupId() != null || isExplicitMetaGroupClassName(row.getClassName());
    }

    private boolean isExplicitMetaGroupRequest(ManualLoadEntryRequest request) {
        return request.getMetaGroupId() != null || isExplicitMetaGroupClassName(request.getClassName());
    }

    private boolean isExplicitMetaGroupEntry(ManualLoadEntry entry) {
        return entry.getMetaGroupId() != null || isExplicitMetaGroupClassName(entry.getClassName());
    }

    private boolean isExplicitMetaGroupClassName(String className) {
        return ClassNameNormalizer.normalize(className).toUpperCase(Locale.ROOT).startsWith("МГ:");
    }

    private Long manualClassId(CurriculumPlanEntry row) {
        return isExplicitMetaGroupRow(row) ? null : row.getClassId();
    }

    private Long manualMetaGroupId(CurriculumPlanEntry row) {
        return isExplicitMetaGroupRow(row) ? row.getMetaGroupId() : null;
    }

    private Long resolveMetaGroupId(String academicYear, ManualLoadEntryRequest request) {
        if (request.getMetaGroupId() != null) {
            return request.getMetaGroupId();
        }
        StudyPeriod period = resolveStudyPeriod(academicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate());
        return findRuleWithFallback(
                academicYear,
                request.getNumberSchoolBuilding().trim(),
                request.getClassName(),
                request.getSubjectName(),
                request.getEducationLevel(),
                period
        )
                .map(CurriculumPlanEntry::getMetaGroupId)
                .orElseThrow(() -> new IllegalArgumentException("meta_group_id is required for metagroup load row"));
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleWithFallback(ManualLoadEntry entry, StudyPeriod effectiveStudyPeriod) {
        java.util.List<StudyPeriod> candidates = candidateStudyPeriods(effectiveStudyPeriod);
        String subjectName = entry.getSubjectName() == null ? "" : entry.getSubjectName().trim();
        if (isExplicitMetaGroupEntry(entry)) {
            if (entry.getMetaGroupId() != null) {
                return candidates.stream()
                        .map(period -> curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                entry.getAcademicYear(), entry.getMetaGroupId(), subjectName, entry.getEducationLevel(), period))
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .findFirst();
            }
            return findRuleWithFallback(entry.getAcademicYear(), entry.getNumberSchoolBuilding().trim(), entry.getClassName(), subjectName, entry.getEducationLevel(), effectiveStudyPeriod)
                    .filter(this::isExplicitMetaGroupRow);
        }
        if (entry.getClassId() != null) {
            return candidates.stream()
                    .map(period -> curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                            entry.getAcademicYear(), entry.getClassId(), subjectName, entry.getEducationLevel(), period))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .filter(row -> !isExplicitMetaGroupRow(row))
                    .findFirst();
        }
        return findRuleWithFallback(entry.getAcademicYear(), entry.getNumberSchoolBuilding().trim(), entry.getClassName(), subjectName, entry.getEducationLevel(), effectiveStudyPeriod)
                .filter(row -> !isExplicitMetaGroupRow(row));
    }

    private java.util.List<StudyPeriod> candidateStudyPeriods(StudyPeriod effectiveStudyPeriod) {
        java.util.List<StudyPeriod> candidates = new java.util.ArrayList<>();
        candidates.add(effectiveStudyPeriod == null ? StudyPeriod.YEAR : effectiveStudyPeriod);
        candidates.add(StudyPeriod.YEAR);
        candidates.add(StudyPeriod.H1);
        candidates.add(StudyPeriod.H2);
        return candidates.stream().distinct().toList();
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleWithFallback(String academicYear,
                                                                         String numberSchoolBuilding,
                                                                         String className,
                                                                         String subjectName,
                                                                         org.school.personalLoad.model.EducationLevel educationLevel,
                                                                         StudyPeriod effectiveStudyPeriod) {
        return candidateStudyPeriods(effectiveStudyPeriod).stream()
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
                                         Long classId,
                                         Long metaGroupId,
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
