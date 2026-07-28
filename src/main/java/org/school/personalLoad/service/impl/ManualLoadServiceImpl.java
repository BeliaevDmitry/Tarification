package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
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
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.SalaryGroupCoefficientSubjectRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.service.ClassSizeService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.ManualLoadService;
import org.school.personalLoad.service.LoadSalaryCalculationService;
import org.school.personalLoad.service.PrimarySubjectService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualLoadServiceImpl implements ManualLoadService {

    private static final BigDecimal STUDENT_HOUR_MULTIPLIER = BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
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
    private final SubjectLevelCoefficientRepository subjectLevelCoefficientRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final SalarySettingsRepository salarySettingsRepository;
    private final SalaryGroupCoefficientSubjectRepository salaryGroupCoefficientSubjectRepository;
    private final MetaGroupRepository metaGroupRepository;
    private final PrimarySubjectService primarySubjectService;
    private final ClassSizeService classSizeService;
    private final LoadSalaryCalculationService loadSalaryCalculationService;

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
        Map<String, ManualLoadEntry> existingAllocationByKey = explicitAcademicYears.stream()
                .flatMap(year -> manualLoadEntryRepository.findAllByAcademicYear(year).stream())
                .filter(ManualLoadEntry::isInRateAllocationConfirmed)
                .collect(java.util.stream.Collectors.toMap(
                        this::manualLoadDuplicateKey,
                        java.util.function.Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        List<ManualLoadEntry> entries = requests.stream()
                .map(this::toEntity)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(this::manualLoadDuplicateKey, java.util.function.Function.identity(), (first, second) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
        entries.forEach(entry -> {
            ManualLoadEntry existing = existingAllocationByKey.get(manualLoadDuplicateKey(entry));
            if (existing == null) return;
            entry.setEmploymentContractId(existing.getEmploymentContractId());
            entry.setIncludedInRateHours(existing.getIncludedInRateHours());
            entry.setInRateAllocationConfirmed(true);
            entry.setInRateReason(existing.getInRateReason());
            entry.setInRateUpdatedAt(existing.getInRateUpdatedAt());
            entry.setInRateUpdatedBy(existing.getInRateUpdatedBy());
        });
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
            java.util.Set<Long> metaGroupIds = scopedMetaGroupIds(entries);
            validateAddressScopeTargets(academicYear, resolvedSchoolBuildingId, classIds, metaGroupIds);
            if (!classIds.isEmpty()) {
                manualLoadEntryRepository.deleteByAcademicYearAndClassIds(academicYear, classIds);
            }
            if (!metaGroupIds.isEmpty()) {
                manualLoadEntryRepository.deleteByAcademicYearAndMetaGroupIds(academicYear, metaGroupIds);
            }
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

            RuleKey key = new RuleKey(rule.getClassName(), rule.getSubjectName(), rule.getCurriculumPart(), rule.getEducationLevel(), rule.getStudyPeriod());
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
            header.createCell(14).setCellValue("TEACHER_ID");
            header.createCell(15).setCellValue("SUBJECT_ID");
            header.createCell(16).setCellValue("CURRICULUM_PART");
            header.createCell(17).setCellValue("CURRICULUM_MODULE_ID");
            header.createCell(18).setCellValue("MODULE_NAME");

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
                if (row.teacherId() != null) excelRow.createCell(14).setCellValue(row.teacherId());
                if (row.subjectId() != null) excelRow.createCell(15).setCellValue(row.subjectId());
                excelRow.createCell(16).setCellValue((row.curriculumPart() == null ? CurriculumPart.CORE : row.curriculumPart()).name());
                if (row.curriculumModuleId() != null) excelRow.createCell(17).setCellValue(row.curriculumModuleId());
                excelRow.createCell(18).setCellValue(row.moduleName() == null ? "" : row.moduleName());
            }

            for (int i = 0; i <= 18; i++) {
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

    @Override
    public byte[] exportSalaryOneWorkbook(String academicYear) throws IOException {
        List<ManualLoadEntry> rows = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> !normalizeDisplayValue(row.getFioTeacher()).isBlank())
                .filter(this::isFirstHalfSalaryRow)
                .sorted(Comparator.comparing(ManualLoadEntry::getFioTeacher, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ManualLoadEntry::getNumberSchoolBuilding, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ManualLoadEntry::getSubjectName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ManualLoadEntry::getClassName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ManualLoadEntry::getGroupNameEducationalPlan, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        Map<Long, LoadSalaryCalculationService.SalaryLine> salaryLines =
                loadSalaryCalculationService.calculate(academicYear, rows);

        Map<String, String> buildingNameByCode = schoolBuildingRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        building -> normalizeToken(building.getCode()),
                        building -> normalizeDisplayValue(building.getName()).isBlank()
                                ? normalizeDisplayValue(building.getCode())
                                : normalizeDisplayValue(building.getName()),
                        (first, second) -> first
                ));
        Map<String, String> classTypeByClassKey = classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> classAddressKey(entry.getNumberSchoolBuilding(), entry.getClassName()),
                        entry -> normalizeToken(entry.getClassType()),
                        (first, second) -> first
                ));
        Map<Long, String> classTypeByMetaGroupId = metaGroupRepository.findAll().stream()
                .filter(metaGroup -> academicYear.equals(metaGroup.getAcademicYear()))
                .collect(java.util.stream.Collectors.toMap(
                        MetaGroup::getId,
                        metaGroup -> normalizeToken(metaGroup.getClassType()),
                        (first, second) -> first
                ));
        Map<String, java.util.Optional<CurriculumPlanEntry>> curriculumCache = new HashMap<>();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setAlignment(HorizontalAlignment.CENTER);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);

            Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, "Нагрузка для ЗП 1"));
            List<String> headers = List.of(
                    "№",
                    "ФИО педагога",
                    "Название корпуса",
                    "Предмет",
                    "Класс",
                    "Группа",
                    "Период",
                    "Часы всего",
                    "Часы внутри ставки",
                    "Часы к оплате",
                    "Основание",
                    "Часть учебного плана",
                    "Обязательный / по выбору школы",
                    "Деление на подгруппы"
            );
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) {
                headerRow.createCell(column).setCellValue(headers.get(column));
                headerRow.getCell(column).setCellStyle(header);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));

            int rowNum = 1;
            int sequence = 1;
            for (ManualLoadEntry entry : rows) {
                CurriculumPlanEntry curriculum = resolveCurriculumForSalaryExport(entry, curriculumCache).orElse(null);
                CurriculumPart part = curriculum == null
                        ? (entry.getCurriculumPart() == null ? CurriculumPart.CORE : entry.getCurriculumPart())
                        : (curriculum.getCurriculumPart() == null ? CurriculumPart.CORE : curriculum.getCurriculumPart());
                boolean aoop = isAoopLoadRow(entry, classTypeByClassKey, classTypeByMetaGroupId);

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sequence++);
                row.createCell(1).setCellValue(normalizeDisplayValue(entry.getFioTeacher()));
                row.createCell(2).setCellValue(buildingNameForSalaryExport(entry, buildingNameByCode));
                row.createCell(3).setCellValue(salaryExportSubject(entry, aoop));
                row.createCell(4).setCellValue(normalizeDisplayValue(entry.getClassName()));
                row.createCell(5).setCellValue(salaryExportGroup(entry));
                row.createCell(6).setCellValue(studyPeriodLabel(entry.getStudyPeriod()));
                LoadSalaryCalculationService.SalaryLine line = salaryLines.get(entry.getId());
                if (line == null) {
                    line = loadSalaryCalculationService.calculate(academicYear, entry);
                }
                BigDecimal included = line == null ? BigDecimal.ZERO : line.includedHours();
                BigDecimal paid = line == null ? BigDecimal.valueOf(manualLoadHours(entry)) : line.paidHours();
                row.createCell(7).setCellValue(manualLoadHours(entry));
                row.createCell(8).setCellValue(included.doubleValue());
                row.createCell(9).setCellValue(paid.doubleValue());
                row.createCell(10).setCellValue(included.signum() > 0
                        ? (normalizeDisplayValue(entry.getInRateReason()).isBlank()
                        ? "Внутри должностного оклада" : entry.getInRateReason()) : "");
                row.createCell(11).setCellValue(curriculumPartSalaryLabel(part));
                row.createCell(12).setCellValue(subjectRequirementSalaryLabel(curriculum, part));
                row.createCell(13).setCellValue(subgroupPolicySalaryLabel(curriculum, entry));
                for (int column = 0; column < headers.size(); column++) {
                    row.getCell(column).setCellStyle(wrap);
                }
            }

            int[] widths = {8, 32, 26, 28, 12, 24, 12, 11, 14, 12, 34, 24, 26, 26};
            for (int column = 0; column < widths.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public byte[] exportDepartmentLoadWorkbook(String academicYear) throws IOException {
        List<ManualLoadTemplateRow> rows = buildTemplateRows(academicYear).stream()
                .filter(this::includeInDepartmentLoadExport)
                .toList();
        Map<String, Integer> classSizeByClass = latestClassSizeByClass(academicYear);
        Map<Long, String> metaGroupNameById = metaGroupRepository.findAll().stream()
                .filter(metaGroup -> academicYear.equals(metaGroup.getAcademicYear()))
                .collect(java.util.stream.Collectors.toMap(
                        MetaGroup::getId,
                        metaGroup -> normalizeDisplayValue(metaGroup.getName()),
                        (first, second) -> first
                ));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setAlignment(HorizontalAlignment.CENTER);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);

            buildDepartmentFinanceSheet(workbook, rows, classSizeByClass, metaGroupNameById, header, wrap);
            buildDepartmentClassesSheet(workbook, rows, classSizeByClass, header, wrap);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private boolean includeInDepartmentLoadExport(ManualLoadTemplateRow row) {
        StudyPeriod period = row == null || row.studyPeriod() == null ? StudyPeriod.YEAR : row.studyPeriod();
        return period != StudyPeriod.H2;
    }

    @Override
    public byte[] exportConsolidatedWorkbook(String academicYear) throws IOException {
        List<ManualLoadEntry> rows = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> normalizeDisplayValue(row.getFioTeacher()).length() > 0)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<Long, String> primarySubjectByTeacherId = primarySubjectService.resolveForExport(academicYear);
        Map<String, BigDecimal> subjectCoefficientByLevel = subjectCoefficientByLevel();
        Set<String> groupCoefficientSubjects = groupCoefficientSubjects();
        Map<String, List<String>> classLeadershipByTeacher = new HashMap<>();
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).forEach(entry -> {
            String teacherKey = normalizeToken(entry.getFioTeacher());
            String className = normalizeDisplayValue(entry.getClassName());
            if (!teacherKey.isBlank() && !className.isBlank()) {
                classLeadershipByTeacher.computeIfAbsent(teacherKey, key -> new ArrayList<>()).add(className);
            }
        });
        classLeadershipByTeacher.values().forEach(classes -> classes.sort(String.CASE_INSENSITIVE_ORDER));

        Map<Long, List<ManualLoadEntry>> rowsByTeacher = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        this::requiredTeacherId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toCollection(ArrayList::new)
                ));
        List<ConsolidatedTeacherGroup> teacherGroups = rowsByTeacher.entrySet().stream()
                .map(entry -> new ConsolidatedTeacherGroup(
                        normalizeDisplayValue(entry.getValue().get(0).getFioTeacher()),
                        normalizeToken(entry.getValue().get(0).getFioTeacher()),
                        primarySubjectByTeacherId.getOrDefault(entry.getKey(), ""),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(ConsolidatedTeacherGroup::primarySubject, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ConsolidatedTeacherGroup::fio, String.CASE_INSENSITIVE_ORDER))
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setAlignment(HorizontalAlignment.CENTER);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);

            Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, "По основному предмету"));
            sheet.getPrintSetup().setLandscape(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);

            List<String> cols = List.of(
                    "Основной предмет*",
                    "ФИО",
                    "Корпус",
                    "Предмет",
                    "Класс",
                    "Группа",
                    "Период",
                    "Кол-во часов",
                    "ИТОГО Часов",
                    "К-во детей (Норм)",
                    "К-во детей (с К=2)",
                    "К-во детей (с К=3)",
                    "Коэф. Предмета",
                    "Классное руководство"
            );
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < cols.size(); i++) {
                headerRow.createCell(i).setCellValue(cols.get(i));
                headerRow.getCell(i).setCellStyle(header);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, cols.size() - 1));

            int rowNum = 1;
            for (ConsolidatedTeacherGroup group : teacherGroups) {
                List<ManualLoadEntry> teacherRows = group.rows().stream()
                        .sorted(Comparator.comparing(ManualLoadEntry::getNumberSchoolBuilding, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(ManualLoadEntry::getSubjectName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(ManualLoadEntry::getClassName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(ManualLoadEntry::getGroupNameEducationalPlan, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(entry -> entry.getStudyPeriod() == null ? StudyPeriod.YEAR : entry.getStudyPeriod()))
                        .toList();
                int teacherStart = rowNum;
                String totalHours = formatTotalHalfHours(teacherRows);
                String classLeadership = String.join(", ", classLeadershipByTeacher.getOrDefault(group.teacherKey(), List.of()));

                for (ManualLoadEntry entry : teacherRows) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(group.primarySubject());
                    row.createCell(1).setCellValue(group.fio());
                    row.createCell(2).setCellValue(normalizeDisplayValue(entry.getNumberSchoolBuilding()));
                    row.createCell(3).setCellValue(normalizeDisplayValue(entry.getSubjectName()));
                    row.createCell(4).setCellValue(normalizeDisplayValue(entry.getClassName()));
                    row.createCell(5).setCellValue(normalizeDisplayValue(entry.getGroupNameEducationalPlan()));
                    row.createCell(6).setCellValue(studyPeriodLabel(entry.getStudyPeriod()));
                    row.createCell(7).setCellValue(manualLoadHours(entry));
                    row.createCell(8).setCellValue(totalHours);
                    row.createCell(9).setCellValue("");
                    row.createCell(10).setCellValue("");
                    row.createCell(11).setCellValue("");
                    row.createCell(12).setCellValue(subjectCoefficient(entry, subjectCoefficientByLevel)
                            .stripTrailingZeros()
                            .toPlainString());
                    row.createCell(13).setCellValue(classLeadership);
                    for (int c = 0; c < cols.size(); c++) {
                        row.getCell(c).setCellStyle(wrap);
                    }
                }
                if (rowNum - 1 > teacherStart) {
                    sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 0, 0));
                    sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 1, 1));
                    sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 8, 8));
                    sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 13, 13));
                }
            }

            int[] widths = {28, 30, 12, 22, 10, 12, 10, 12, 13, 15, 15, 15, 14, 20};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
    private String formatTotalHalfHours(List<ManualLoadEntry> rows) {
        int h1 = 0;
        int h2 = 0;
        for (ManualLoadEntry row : rows) {
            int hours = manualLoadHours(row);
            StudyPeriod period = row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod();
            if (period == StudyPeriod.H1) {
                h1 += hours;
            } else if (period == StudyPeriod.H2) {
                h2 += hours;
            } else {
                h1 += hours;
                h2 += hours;
            }
        }
        return h1 == h2 ? String.valueOf(h1) : h1 + " | " + h2;
    }

    private String studyPeriodLabel(StudyPeriod period) {
        if (period == StudyPeriod.H1) {
            return "1П";
        }
        if (period == StudyPeriod.H2) {
            return "2П";
        }
        return "ГОД";
    }

    private java.util.Optional<CurriculumPlanEntry> resolveCurriculumForSalaryExport(
            ManualLoadEntry entry,
            Map<String, java.util.Optional<CurriculumPlanEntry>> cache
    ) {
        StudyPeriod effectiveStudyPeriod = resolveStudyPeriod(
                entry.getAcademicYear(),
                entry.getClassName(),
                entry.getStudyPeriod(),
                entry.getLoadFromDate(),
                entry.getLoadToDate()
        );
        String key = String.join("|",
                normalizeDisplayValue(entry.getAcademicYear()),
                String.valueOf(entry.getClassId()),
                String.valueOf(entry.getMetaGroupId()),
                String.valueOf(entry.getSubjectId()),
                normalizeToken(entry.getSubjectName()),
                String.valueOf(entry.getCurriculumPart() == null ? CurriculumPart.CORE : entry.getCurriculumPart()),
                String.valueOf(entry.getEducationLevel()),
                String.valueOf(effectiveStudyPeriod),
                String.valueOf(entry.getCurriculumModuleId())
        );
        return cache.computeIfAbsent(key, ignored -> {
            java.util.Optional<CurriculumPlanEntry> byFk = findRuleByFk(entry, effectiveStudyPeriod);
            if (byFk.isPresent()) {
                return byFk;
            }
            return curriculumPlanEntryRepository.findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                    entry.getAcademicYear(),
                    entry.getClassName(),
                    entry.getSubjectName(),
                    entry.getEducationLevel(),
                    effectiveStudyPeriod
            );
        });
    }

    private String buildingNameForSalaryExport(ManualLoadEntry entry, Map<String, String> buildingNameByCode) {
        String building = normalizeDisplayValue(entry.getNumberSchoolBuilding());
        return buildingNameByCode.getOrDefault(normalizeToken(building), building);
    }

    private boolean isAoopLoadRow(ManualLoadEntry entry,
                                  Map<String, String> classTypeByClassKey,
                                  Map<Long, String> classTypeByMetaGroupId) {
        String classType = entry.getMetaGroupId() == null
                ? classTypeByClassKey.getOrDefault(classAddressKey(entry.getNumberSchoolBuilding(), entry.getClassName()), "")
                : classTypeByMetaGroupId.getOrDefault(entry.getMetaGroupId(), "");
        String normalized = normalizeToken(classType);
        return normalized.contains("AOOP") || normalized.contains("АООП") || normalized.contains("АУОП");
    }

    private String salaryExportSubject(ManualLoadEntry entry, boolean aoop) {
        String subject = normalizeDisplayValue(entry.getSubjectName());
        if (subject.isBlank() || aoop) {
            return subject;
        }
        EducationStage stage = educationStageForClass(entry.getClassName());
        if (stage == EducationStage.NOO) {
            return subject + " НОО";
        }
        if (stage == EducationStage.OOO) {
            return subject + " ООО";
        }
        if (stage == EducationStage.SOO) {
            return subject + " СОО";
        }
        return subject;
    }

    private String salaryExportGroup(ManualLoadEntry entry) {
        String group = normalizeDisplayValue(entry.getGroupNameEducationalPlan());
        if (group.isBlank()) {
            return "";
        }
        String subject = normalizeDisplayValue(entry.getSubjectName());
        if (normalizeToken(group).contains(normalizeToken(subject))) {
            return group;
        }
        return subject.isBlank() ? group : subject + " " + group;
    }

    private String curriculumPartSalaryLabel(CurriculumPart part) {
        return switch (part == null ? CurriculumPart.CORE : part) {
            case CORE -> "основная";
            case FORMABLE -> "формируемая";
            case EXTRACURRICULAR -> "внеурочная";
            case CORRECTIONAL -> "коррекционная";
        };
    }

    private String subjectRequirementSalaryLabel(CurriculumPlanEntry curriculum, CurriculumPart part) {
        SubjectRequirement requirement = curriculum == null || curriculum.getSubjectRequirement() == null
                ? (part == CurriculumPart.CORE ? SubjectRequirement.MANDATORY : SubjectRequirement.SCHOOL_CHOICE)
                : curriculum.getSubjectRequirement();
        return requirement == SubjectRequirement.MANDATORY ? "Обязательный" : "По выбору школы";
    }

    private String subgroupPolicySalaryLabel(CurriculumPlanEntry curriculum, ManualLoadEntry entry) {
        boolean subgroupRequired = curriculum == null
                ? !normalizeDisplayValue(entry.getGroupNameEducationalPlan()).isBlank()
                : curriculum.isSubgroupRequired();
        if (!subgroupRequired) {
            return "";
        }
        SubgroupPolicy policy = curriculum == null || curriculum.getSubgroupPolicy() == null
                ? SubgroupPolicy.RECOMMENDED
                : curriculum.getSubgroupPolicy();
        return policy == SubgroupPolicy.SCHOOL_CHOICE ? "Выбор школы" : "Рекомендовано";
    }

    private void buildDepartmentFinanceSheet(Workbook workbook,
                                             List<ManualLoadTemplateRow> rows,
                                             Map<String, Integer> classSizeByClass,
                                             Map<Long, String> metaGroupNameById,
                                             CellStyle header,
                                             CellStyle wrap) {
        Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, "Справочник финансов"));
        List<String> headers = List.of(
                "Класс",
                "Тип предмета",
                "Название предмета",
                "Название метагруппы",
                "Кол-во учеников в группе",
                "Кол-во часов в неделю"
        );
        Row headerRow = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            headerRow.createCell(column).setCellValue(headers.get(column));
            headerRow.getCell(column).setCellStyle(header);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));

        List<ManualLoadTemplateRow> sortedRows = rows.stream()
                .sorted(Comparator.comparing(ManualLoadTemplateRow::className, this::compareClassNames)
                        .thenComparing(ManualLoadTemplateRow::curriculumPart, Comparator.nullsFirst(Comparator.comparing(Enum::name)))
                        .thenComparing(ManualLoadTemplateRow::subjectName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(row -> row.groupNameEducationalPlan() == null ? "" : row.groupNameEducationalPlan()))
                .toList();

        int rowNum = 1;
        for (ManualLoadTemplateRow entry : sortedRows) {
            boolean metaGroup = entry.metaGroupId() != null;
            String className = metaGroup ? "" : ClassNameNormalizer.normalize(entry.className());
            String metaGroupName = metaGroup
                    ? metaGroupNameById.getOrDefault(entry.metaGroupId(), normalizeDisplayValue(entry.className()))
                    : "";
            int classSize = metaGroup ? 25 : classSizeFor(classSizeByClass, entry.className());
            int groupSize = metaGroup ? 25 : departmentGroupSize(classSize, entry.groupNameEducationalPlan());

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(className);
            row.createCell(1).setCellValue(departmentSubjectType(entry.curriculumPart()));
            row.createCell(2).setCellValue(departmentSubjectName(entry.subjectName()));
            row.createCell(3).setCellValue(metaGroupName);
            row.createCell(4).setCellValue(groupSize);
            row.createCell(5).setCellValue(entry.load() == null ? 0 : entry.load());
            for (int column = 0; column < headers.size(); column++) {
                row.getCell(column).setCellStyle(wrap);
            }
        }

        int[] widths = {14, 24, 34, 30, 22, 22};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    private void buildDepartmentClassesSheet(Workbook workbook,
                                             List<ManualLoadTemplateRow> rows,
                                             Map<String, Integer> classSizeByClass,
                                             CellStyle header,
                                             CellStyle wrap) {
        Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, "Справочник классов"));
        List<String> headers = List.of("Параллель", "Литера", "Кол-во учеников в классе");
        Row headerRow = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            headerRow.createCell(column).setCellValue(headers.get(column));
            headerRow.getCell(column).setCellStyle(header);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));

        List<String> classes = rows.stream()
                .filter(row -> row.metaGroupId() == null)
                .map(ManualLoadTemplateRow::className)
                .map(ClassNameNormalizer::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(this::compareClassNames)
                .toList();

        int rowNum = 1;
        for (String className : classes) {
            Row row = sheet.createRow(rowNum++);
            Integer parallel = ClassNameNormalizer.extractParallel(className);
            row.createCell(0).setCellValue(parallel == null ? "" : String.valueOf(parallel));
            row.createCell(1).setCellValue(departmentClassLetter(className));
            row.createCell(2).setCellValue(classSizeFor(classSizeByClass, className));
            for (int column = 0; column < headers.size(); column++) {
                row.getCell(column).setCellStyle(wrap);
            }
        }

        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 24 * 256);
    }

    private Map<String, Integer> latestClassSizeByClass(String academicYear) {
        return classSizeService.effectiveClassSizes(academicYear);
    }

    private int classSizeFor(Map<String, Integer> classSizeByClass, String className) {
        if (classSizeByClass == null || classSizeByClass.isEmpty()) {
            return 30;
        }
        String key = classSizeKey(className);
        Integer value = classSizeByClass.get(key);
        if (value != null) {
            return value;
        }
        value = classSizeByClass.get(ClassNameNormalizer.normalize(className));
        if (value != null) {
            return value;
        }
        value = classSizeByClass.get(normalizeToken(className));
        return value == null ? 30 : value;
    }

    private String classSizeKey(String className) {
        return ClassNameNormalizer.normalize(className)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
    }

    private int departmentGroupSize(int classSize, String groupName) {
        String group = normalizeToken(groupName);
        if (group.isBlank()) {
            return classSize;
        }
        int firstGroupSize = (classSize + 1) / 2;
        int secondGroupSize = classSize - firstGroupSize;
        if (group.contains("2")) {
            return secondGroupSize;
        }
        return firstGroupSize;
    }

    private String departmentSubjectType(CurriculumPart part) {
        return switch (part == null ? CurriculumPart.CORE : part) {
            case CORE -> "Основной предмет";
            case FORMABLE -> "Учебный курс";
            case EXTRACURRICULAR -> "Курс ВД";
            case CORRECTIONAL -> "Коррекционная область";
        };
    }

    private String departmentSubjectName(String subjectName) {
        return normalizeDisplayValue(subjectName)
                .replaceFirst("(?iu)\\s+(НОО|ООО|СОО)(\\s+[0-9.,\\s]+)?$", "")
                .trim();
    }

    private String departmentClassLetter(String className) {
        String normalized = ClassNameNormalizer.normalize(className);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\d{1,2}\\s*[- ]?\\s*(.+)$").matcher(normalized);
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1).trim();
    }


    @Override
    public byte[] exportSubjectLoadWorkbook(String academicYear, String building, String campusAddress) throws IOException {
        List<ManualLoadEntry> allRows = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> !normalizeDisplayValue(row.getFioTeacher()).isBlank())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        List<ClassroomLeadershipEntry> classEntries = classroomLeadershipRepository.findAllByAcademicYear(academicYear);
        Map<String, String> addressByClass = new HashMap<>();
        for (ClassroomLeadershipEntry entry : classEntries) {
            String key = classAddressKey(entry.getNumberSchoolBuilding(), entry.getClassName());
            addressByClass.put(key, normalizeDisplayValue(entry.getCampusAddress()));
        }
        Map<String, List<String>> addressesByBuilding = schoolBuildingRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> normalizeToken(row.getCode()),
                        java.util.stream.Collectors.mapping(SchoolBuilding::getAddress, java.util.stream.Collectors.toList())
                ));
        Map<String, String> subjectAreaBySubject = subjectCatalogRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> normalizeToken(entry.getSubjectName()),
                        entry -> normalizeDisplayValue(entry.getSubjectAreaName()),
                        (first, second) -> first
                ));

        List<String> addressSheets = subjectLoadAddresses(allRows, classEntries, addressByClass, addressesByBuilding);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SubjectLoadStyles styles = createSubjectLoadStyles(workbook);
            for (String address : addressSheets) {
                buildSubjectLoadSheet(
                        workbook,
                        styles,
                        address,
                        allRows,
                        classEntries,
                        addressByClass,
                        addressesByBuilding,
                        subjectAreaBySubject
                );
            }
            buildSubjectLoadSheet(
                    workbook,
                    styles,
                    "",
                    allRows,
                    classEntries,
                    addressByClass,
                    addressesByBuilding,
                    subjectAreaBySubject
            );
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buildSubjectLoadSheet(Workbook workbook,
                                       SubjectLoadStyles styles,
                                       String selectedAddress,
                                       List<ManualLoadEntry> allRows,
                                       List<ClassroomLeadershipEntry> classEntries,
                                       Map<String, String> addressByClass,
                                       Map<String, List<String>> addressesByBuilding,
                                       Map<String, String> subjectAreaBySubject) {
        String selectedAddressKey = normalizeToken(selectedAddress);
        List<ManualLoadEntry> scopedRows = allRows.stream()
                .filter(row -> rowInSubjectExportScope(row, selectedAddressKey, addressByClass, addressesByBuilding))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        List<String> classColumns = classEntries.stream()
                .filter(entry -> selectedAddressKey.isBlank() || normalizeToken(entry.getCampusAddress()).equals(selectedAddressKey))
                .map(ClassroomLeadershipEntry::getClassName)
                .map(this::normalizeDisplayValue)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(this::compareClassNames)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        scopedRows.stream()
                .map(ManualLoadEntry::getClassName)
                .map(this::normalizeDisplayValue)
                .filter(value -> !value.isBlank() && !classColumns.contains(value))
                .sorted(this::compareClassNames)
                .forEach(classColumns::add);

        Map<String, List<ManualLoadEntry>> rowsByTeacher = allRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(row -> normalizeToken(row.getFioTeacher()), HashMap::new, java.util.stream.Collectors.toList()));
        Map<String, List<String>> leadershipByTeacher = new HashMap<>();
        for (ClassroomLeadershipEntry entry : classEntries) {
            String teacherKey = normalizeToken(entry.getFioTeacher());
            String className = normalizeDisplayValue(entry.getClassName());
            if (!teacherKey.isBlank() && !className.isBlank()) {
                leadershipByTeacher.computeIfAbsent(teacherKey, key -> new ArrayList<>()).add(className);
            }
        }
        leadershipByTeacher.values().forEach(values -> values.sort(this::compareClassNames));

        Map<SubjectTeacherKey, SubjectTeacherSummary> summaries = new LinkedHashMap<>();
        scopedRows.stream()
                .sorted(Comparator
                        .comparing((ManualLoadEntry row) -> subjectAreaSortKey(row.getSubjectName(), subjectAreaBySubject), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> normalizeDisplayValue(row.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> normalizeDisplayValue(row.getFioTeacher()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> normalizeDisplayValue(row.getClassName()), String.CASE_INSENSITIVE_ORDER))
                .forEach(row -> {
                    SubjectTeacherKey key = new SubjectTeacherKey(
                            normalizeDisplayValue(row.getSubjectName()),
                            normalizeDisplayValue(row.getFioTeacher()),
                            subjectAreaSortKey(row.getSubjectName(), subjectAreaBySubject),
                            normalizeToken(row.getSubjectName()),
                            normalizeToken(row.getFioTeacher())
                    );
                    SubjectTeacherSummary summary = summaries.computeIfAbsent(key, SubjectTeacherSummary::new);
                    summary.addClassHours(normalizeDisplayValue(row.getClassName()), groupSlot(row), row);
                });

        for (SubjectTeacherSummary summary : summaries.values()) {
            List<ManualLoadEntry> teacherRows = rowsByTeacher.getOrDefault(summary.key.teacherKey(), List.of());
            for (ManualLoadEntry row : teacherRows) {
                summary.addTotal(row, rowInSubjectExportScope(row, selectedAddressKey, addressByClass, addressesByBuilding));
            }
            summary.addressesElsewhere = teacherRows.stream()
                    .filter(row -> !rowInSubjectExportScope(row, selectedAddressKey, addressByClass, addressesByBuilding))
                    .map(row -> resolveRowAddress(row, addressByClass, addressesByBuilding))
                    .filter(address -> !address.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(java.util.stream.Collectors.joining(", "));
            summary.classLeadership = String.join(", ", leadershipByTeacher.getOrDefault(summary.key.teacherKey(), List.of()));
        }

        Sheet sheet = workbook.createSheet(uniqueSheetName(workbook, subjectLoadSheetName(selectedAddress)));
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);

        int fixedColumns = 3;
        int leadershipColumn = fixedColumns + classColumns.size();
        int addressColumn = leadershipColumn + 1;
        int lastColumn = addressColumn;

        Row title = sheet.createRow(0);
        title.setHeightInPoints(24);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue(subjectLoadTitle(selectedAddress));
        titleCell.setCellStyle(styles.title());
        if (lastColumn > 0) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));

        Row topHeader = sheet.createRow(1);
        Row subHeader = sheet.createRow(2);
        topHeader.setHeightInPoints(36);
        subHeader.setHeightInPoints(34);
        setMergedHeader(sheet, topHeader, subHeader, 0, "Предмет", styles.header());
        setMergedHeader(sheet, topHeader, subHeader, 1, "ФИО", styles.header());
        Cell classesCell = topHeader.createCell(2);
        classesCell.setCellValue("Классы");
        classesCell.setCellStyle(styles.header());
        subHeader.createCell(2).setCellValue("часов\nпредмет/площадка/комплекс");
        subHeader.getCell(2).setCellStyle(styles.header());
        for (int i = 0; i < classColumns.size(); i++) {
            Cell cell = topHeader.createCell(fixedColumns + i);
            cell.setCellValue(classColumns.get(i));
            cell.setCellStyle(styles.header());
            Cell subCell = subHeader.createCell(fixedColumns + i);
            subCell.setCellValue("1г; 2г");
            subCell.setCellStyle(styles.groupHeader());
        }
        setMergedHeader(sheet, topHeader, subHeader, leadershipColumn, "классное\nруководство", styles.header());
        setMergedHeader(sheet, topHeader, subHeader, addressColumn, "адреса, где\nработает ещё", styles.header());

        int rowNum = 3;
        String currentSubject = null;
        int subjectStart = rowNum;
        for (SubjectTeacherSummary summary : summaries.values()) {
            if (currentSubject != null && !Objects.equals(currentSubject, summary.key.subjectKey()) && rowNum - 1 > subjectStart) {
                sheet.addMergedRegion(new CellRangeAddress(subjectStart, rowNum - 1, 0, 0));
                subjectStart = rowNum;
            } else if (currentSubject != null && !Objects.equals(currentSubject, summary.key.subjectKey())) {
                subjectStart = rowNum;
            }
            currentSubject = summary.key.subjectKey();
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(28);
            writeCell(row, 0, summary.key.subject(), styles.subject());
            writeCell(row, 1, summary.key.fio(), styles.text());
            writeCell(row, 2, summary.hoursSummary(), styles.center());
            for (int i = 0; i < classColumns.size(); i++) {
                writeCell(row, fixedColumns + i, summary.classCell(classColumns.get(i)), styles.center());
            }
            writeCell(row, leadershipColumn, summary.classLeadership, styles.text());
            writeCell(row, addressColumn, summary.addressesElsewhere, styles.text());
        }
        if (currentSubject != null && rowNum - 1 > subjectStart) {
            sheet.addMergedRegion(new CellRangeAddress(subjectStart, rowNum - 1, 0, 0));
        }

        sheet.createFreezePane(3, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowNum - 1), 0, lastColumn));
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        for (int i = 0; i < classColumns.size(); i++) {
            sheet.setColumnWidth(fixedColumns + i, 9 * 256);
        }
        sheet.setColumnWidth(leadershipColumn, 18 * 256);
        sheet.setColumnWidth(addressColumn, 34 * 256);
    }

    private int manualLoadHours(ManualLoadEntry entry) {
        return entry.getGroupLoad() != null ? entry.getGroupLoad() : (entry.getLoad() == null ? 0 : entry.getLoad());
    }

    private byte[] exportFullWorkbook(String academicYear, boolean includeSalary) throws IOException {
        List<ManualLoadEntry> rows = manualLoadEntryRepository.findAllByAcademicYear(academicYear);
        Map<String, TeacherDirectoryEntry> teacherByFio = teacherDirectoryRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> String.valueOf(t.getFioTeacher()).trim().toLowerCase(Locale.ROOT),
                        t -> t,
                        (a, b) -> a
        ));
        Map<String, BigDecimal> subjectCoefficientByLevel = subjectCoefficientByLevel();
        Set<String> groupCoefficientSubjects = groupCoefficientSubjects();
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
        Map<String, Integer> classSizeByClass = latestClassSizeByClass(academicYear);

        BigDecimal studentHourRate = includeSalary ? resolveStudentHourRate() : SalarySettings.DEFAULT_STUDENT_HOUR_RATE;
        SalarySummary salarySummary = includeSalary
                ? calculateSalarySummary(rows, classEntries, classSizeByClass, subjectCoefficientByLevel, groupCoefficientSubjects, studentHourRate)
                : SalarySummary.empty();
        Map<Long, LoadSalaryCalculationService.SalaryLine> salaryLines = includeSalary
                ? loadSalaryCalculationService.calculate(academicYear, rows)
                : Map.of();

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
                if (includeSalary) {
                    cols.subList(cols.size() - 3, cols.size()).clear();
                    cols.add("Часы внутри ставки");
                    cols.add("Часы к оплате");
                    cols.add("Основание");
                    cols.add("Предметный коэф.");
                    cols.add("Коэф. группы");
                    cols.add("За строку");
                    cols.add("За оплачиваемые часы итого");
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
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 16, 16));
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 17, 17));
                                sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 18, 18));
                            }
                        }
                        currentTeacher = key;
                        teacherStart = rowNum;
                    }

                    int subjectHours = e.getGroupLoad() != null ? e.getGroupLoad() : (e.getLoad() == null ? 0 : e.getLoad());
                    String periodLabel = e.getStudyPeriod() == StudyPeriod.H1 ? "1П"
                            : e.getStudyPeriod() == StudyPeriod.H2 ? "2П" : "ГОД";
                    String hoursSummary = formatScopedTotalHours(t[0], t[1], t[2], t[3]);
                    int classSize = classSizeFor(classSizeByClass, e.getClassName());
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
                        SalaryTotals salary = salarySummary.byTeacher()
                                .getOrDefault(salaryTeacherKey(e.getTeacherId(), e.getFioTeacher()), SalaryTotals.empty());
                        LoadSalaryCalculationService.SalaryLine rowSalary = salaryLines.get(e.getId());
                        if (rowSalary == null) {
                            rowSalary = loadSalaryCalculationService.calculate(academicYear, e);
                        }
                        BigDecimal includedHours = rowSalary == null ? BigDecimal.ZERO : rowSalary.includedHours();
                        BigDecimal paidHours = rowSalary == null ? BigDecimal.valueOf(subjectHours) : rowSalary.paidHours();
                        r.createCell(10).setCellValue(includedHours.doubleValue());
                        r.createCell(11).setCellValue(paidHours.doubleValue());
                        r.createCell(12).setCellValue(includedHours.signum() > 0
                                ? (normalizeDisplayValue(e.getInRateReason()).isBlank()
                                ? "Внутри должностного оклада" : e.getInRateReason()) : "");
                        r.createCell(13).setCellValue(coefficientValue(rowSalary == null ? BigDecimal.ONE : rowSalary.subjectCoefficient()));
                        r.createCell(14).setCellValue(coefficientValue(rowSalary == null ? BigDecimal.ONE : rowSalary.groupCoefficient()));
                        r.createCell(15).setCellValue(moneyValue(rowSalary == null ? BigDecimal.ZERO : rowSalary.amount()));
                        r.createCell(16).setCellValue(moneyValue(salary.hourSalary()));
                        r.createCell(17).setCellValue(moneyValue(salary.classLeadershipSalary()));
                        r.createCell(18).setCellValue(moneyValue(salary.total()));
                    }
                    for (int c = 0; c <= (includeSalary ? 18 : 9); c++) r.getCell(c).setCellStyle(c >= 15 ? money : wrap);
                    if (i == buildingRows.size() - 1 && rowNum - 1 > teacherStart) {
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 0, 0));
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 7, 7));
                        sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 9, 9));
                        if (includeSalary) {
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 16, 16));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 17, 17));
                            sheet.addMergedRegion(new CellRangeAddress(teacherStart, rowNum - 1, 18, 18));
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
                    sheet.setColumnWidth(11, 12 * 256);
                    sheet.setColumnWidth(12, 32 * 256);
                    sheet.setColumnWidth(13, 12 * 256);
                    sheet.setColumnWidth(14, 18 * 256);
                    sheet.setColumnWidth(15, 12 * 256);
                    sheet.setColumnWidth(16, 16 * 256);
                    sheet.setColumnWidth(17, 18 * 256);
                    sheet.setColumnWidth(18, 14 * 256);
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
        return formatHalfHours(scopedH1, scopedH2) + " / " + formatHalfHours(totalH1, totalH2);
    }

    private String formatHalfHours(int h1, int h2) {
        return h1 == h2 ? String.valueOf(h1) : h1 + " | " + h2;
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



    private List<String> subjectLoadAddresses(List<ManualLoadEntry> allRows,
                                              List<ClassroomLeadershipEntry> classEntries,
                                              Map<String, String> addressByClass,
                                              Map<String, List<String>> addressesByBuilding) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        addressesByBuilding.values().stream()
                .flatMap(List::stream)
                .map(this::normalizeDisplayValue)
                .filter(address -> !address.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(addresses::add);
        classEntries.stream()
                .map(ClassroomLeadershipEntry::getCampusAddress)
                .map(this::normalizeDisplayValue)
                .filter(address -> !address.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(addresses::add);
        allRows.stream()
                .map(row -> resolveRowAddress(row, addressByClass, addressesByBuilding))
                .filter(address -> !address.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(addresses::add);
        return new ArrayList<>(addresses);
    }

    private String subjectAreaSortKey(String subjectName, Map<String, String> subjectAreaBySubject) {
        String area = normalizeDisplayValue(subjectAreaBySubject.getOrDefault(normalizeToken(subjectName), ""));
        return area.isBlank() ? "Без предметной области" : area;
    }

    private String subjectLoadSheetName(String campusAddress) {
        String address = normalizeDisplayValue(campusAddress);
        return address.isBlank() ? "Комплекс" : address;
    }

    private boolean rowInSubjectExportScope(ManualLoadEntry row,
                                            String selectedAddressKey,
                                            Map<String, String> addressByClass,
                                            Map<String, List<String>> addressesByBuilding) {
        if (selectedAddressKey.isBlank()) {
            return true;
        }
        return normalizeToken(resolveRowAddress(row, addressByClass, addressesByBuilding)).equals(selectedAddressKey);
    }

    private String groupSlot(ManualLoadEntry row) {
        String group = normalizeToken(row.getGroupNameEducationalPlan());
        if (row.getMetaGroupId() == null && group.isBlank()) {
            return "CLASS";
        }
        if (group.contains("2")) {
            return "G2";
        }
        if (group.contains("1")) {
            return "G1";
        }
        String className = normalizeToken(row.getClassName());
        if (className.contains("2")) {
            return "G2";
        }
        return "G1";
    }

    private int compareClassNames(String left, String right) {
        Integer leftParallel = ClassNameNormalizer.extractParallel(left);
        Integer rightParallel = ClassNameNormalizer.extractParallel(right);
        if (leftParallel != null && rightParallel != null && !Objects.equals(leftParallel, rightParallel)) {
            return Integer.compare(leftParallel, rightParallel);
        }
        if (leftParallel != null && rightParallel == null) return -1;
        if (leftParallel == null && rightParallel != null) return 1;
        return normalizeDisplayValue(left).compareToIgnoreCase(normalizeDisplayValue(right));
    }

    private String subjectLoadTitle(String campusAddress) {
        String address = normalizeDisplayValue(campusAddress);
        return address.isBlank() ? "Нагрузка по предметам — весь комплекс" : "Нагрузка по предметам — " + address;
    }

    private SubjectLoadStyles createSubjectLoadStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = subjectBaseStyle(workbook);
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);

        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle header = subjectBaseStyle(workbook);
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle groupHeader = subjectBaseStyle(workbook);
        groupHeader.cloneStyleFrom(header);
        groupHeader.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());

        CellStyle subject = subjectBaseStyle(workbook);
        subject.setFont(bold);
        subject.setAlignment(HorizontalAlignment.RIGHT);
        subject.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle text = subjectBaseStyle(workbook);
        text.setAlignment(HorizontalAlignment.LEFT);
        text.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle center = subjectBaseStyle(workbook);
        center.setAlignment(HorizontalAlignment.CENTER);
        center.setVerticalAlignment(VerticalAlignment.CENTER);
        return new SubjectLoadStyles(title, header, groupHeader, subject, text, center);
    }

    private CellStyle subjectBaseStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void setMergedHeader(Sheet sheet, Row topHeader, Row subHeader, int column, String value, CellStyle style) {
        Cell top = topHeader.createCell(column);
        top.setCellValue(value);
        top.setCellStyle(style);
        Cell sub = subHeader.createCell(column);
        sub.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(topHeader.getRowNum(), subHeader.getRowNum(), column, column));
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private record SubjectLoadStyles(CellStyle title,
                                     CellStyle header,
                                     CellStyle groupHeader,
                                     CellStyle subject,
                                     CellStyle text,
                                     CellStyle center) {}

    private record SubjectTeacherKey(String subject, String fio, String subjectAreaKey, String subjectKey, String teacherKey) {}

    private final class SubjectTeacherSummary {
        private final SubjectTeacherKey key;
        private final Map<String, GroupedHours> hoursByClass = new LinkedHashMap<>();
        private final PeriodTotals subjectTotals = new PeriodTotals();
        private final PeriodTotals scopedTotals = new PeriodTotals();
        private final PeriodTotals totalTotals = new PeriodTotals();
        private String classLeadership = "";
        private String addressesElsewhere = "";

        private SubjectTeacherSummary(SubjectTeacherKey key) {
            this.key = key;
        }

        private void addClassHours(String className, String slot, ManualLoadEntry row) {
            if (className.isBlank()) return;
            GroupedHours grouped = hoursByClass.computeIfAbsent(className, ignored -> new GroupedHours());
            grouped.add(slot, row);
            subjectTotals.add(row);
        }

        private void addTotal(ManualLoadEntry row, boolean scoped) {
            totalTotals.add(row);
            if (scoped) {
                scopedTotals.add(row);
            }
        }

        private String hoursSummary() {
            return formatPeriodTotals(subjectTotals) + " / " + formatPeriodTotals(scopedTotals) + " / " + formatPeriodTotals(totalTotals);
        }

        private String classCell(String className) {
            GroupedHours hours = hoursByClass.get(className);
            return hours == null ? "" : hours.format();
        }
    }

    private final class GroupedHours {
        private final PeriodTotals classHours = new PeriodTotals();
        private final PeriodTotals firstGroup = new PeriodTotals();
        private final PeriodTotals secondGroup = new PeriodTotals();

        private void add(String slot, ManualLoadEntry row) {
            if ("G1".equals(slot)) {
                firstGroup.add(row);
            } else if ("G2".equals(slot)) {
                secondGroup.add(row);
            } else {
                classHours.add(row);
            }
        }

        private String format() {
            if (!classHours.isEmpty()) {
                return formatPeriodTotals(classHours);
            }
            return formatPeriodTotals(firstGroup) + " / " + formatPeriodTotals(secondGroup);
        }
    }

    private final class PeriodTotals {
        private int year;
        private int h1;
        private int h2;

        private void add(ManualLoadEntry row) {
            int hours = manualLoadHours(row);
            if (hours == 0) return;
            StudyPeriod period = row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod();
            if (period == StudyPeriod.H1) {
                h1 += hours;
            } else if (period == StudyPeriod.H2) {
                h2 += hours;
            } else {
                year += hours;
            }
        }

        private boolean isEmpty() {
            return year == 0 && h1 == 0 && h2 == 0;
        }
    }

    private String formatPeriodTotals(PeriodTotals totals) {
        int first = totals.year + totals.h1;
        int second = totals.year + totals.h2;
        if (first == 0 && second == 0) {
            return "";
        }
        return first == second ? String.valueOf(first) : first + " | " + second;
    }

    private String normalizeDisplayValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private Map<String, BigDecimal> subjectCoefficientByLevel() {
        return subjectLevelCoefficientRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> subjectCoefficientKey(entry.getSubjectName(), entry.getEducationStage()),
                        entry -> resolvePositiveCoefficient(entry.getCoefficient()),
                        (a, b) -> a
                ));
    }

    private Set<String> groupCoefficientSubjects() {
        return salaryGroupCoefficientSubjectRepository.findAll().stream()
                .flatMap(row -> java.util.stream.Stream.of(
                        row.getSubjectId() == null ? "" : "id:" + row.getSubjectId(),
                        normalizeToken(row.getSubjectName()).isBlank() ? "" : "name:" + normalizeToken(row.getSubjectName())
                ))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private BigDecimal subjectCoefficient(ManualLoadEntry row, Map<String, BigDecimal> subjectCoefficientByLevel) {
        return subjectCoefficientByLevel.getOrDefault(
                subjectCoefficientKey(row.getSubjectName(), educationStageForClass(row.getClassName())),
                BigDecimal.ONE
        );
    }

    private String subjectCoefficientKey(String subjectName, EducationStage stage) {
        return normalizeToken(subjectName) + "|" + (stage == null ? "" : stage.name());
    }

    private EducationStage educationStageForClass(String className) {
        String normalized = String.valueOf(className == null ? "" : className);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        int grade;
        try {
            grade = Integer.parseInt(matcher.group());
        } catch (Exception e) {
            return null;
        }
        if (grade >= 1 && grade <= 4) return EducationStage.NOO;
        if (grade >= 5 && grade <= 9) return EducationStage.OOO;
        if (grade >= 10 && grade <= 11) return EducationStage.SOO;
        return null;
    }

    private SalarySummary calculateSalarySummary(List<ManualLoadEntry> rows,
                                                 List<ClassroomLeadershipEntry> classEntries,
                                                 Map<String, Integer> classSizeByClass,
                                                 Map<String, BigDecimal> subjectCoefficientByLevel,
                                                 Set<String> groupCoefficientSubjects,
                                                 BigDecimal studentHourRate) {
        Map<String, SalaryTotals> byTeacher = new HashMap<>();
        Map<String, SalaryTotals> byBuilding = new HashMap<>();
        SalaryTotals complex = new SalaryTotals();
        Map<Long, LoadSalaryCalculationService.SalaryLine> salaryLines =
                loadSalaryCalculationService.calculate(
                        rows.stream().map(ManualLoadEntry::getAcademicYear).filter(Objects::nonNull)
                                .findFirst().orElse(""),
                        rows
                );

        for (ManualLoadEntry row : rows) {
            if (!isFirstHalfSalaryRow(row)) {
                continue;
            }
            String building = buildingKey(row.getNumberSchoolBuilding());
            String teacher = salaryTeacherKey(row.getTeacherId(), row.getFioTeacher());
            LoadSalaryCalculationService.SalaryLine line = salaryLines.get(row.getId());
            if (line == null) {
                line = loadSalaryCalculationService.calculate(row.getAcademicYear(), row);
            }
            BigDecimal hourSalary = line.amount();
            byTeacher.computeIfAbsent(teacher, key -> new SalaryTotals()).addHourSalary(hourSalary);
            byBuilding.computeIfAbsent(building, key -> new SalaryTotals()).addHourSalary(hourSalary);
            complex.addHourSalary(hourSalary);
        }

        for (ClassroomLeadershipEntry entry : classEntries) {
            String teacher = salaryTeacherKey(entry.getTeacherId(), entry.getFioTeacher());
            if (teacher.isBlank()) {
                continue;
            }
            String building = buildingKey(entry.getNumberSchoolBuilding());
            int classSize = classSizeFor(classSizeByClass, entry.getClassName());
            BigDecimal leadershipSalary = CLASS_LEADERSHIP_PER_STUDENT
                    .multiply(BigDecimal.valueOf(classSize))
                    .add(CLASS_LEADERSHIP_BASE);
            byTeacher.computeIfAbsent(teacher, key -> new SalaryTotals()).addClassLeadershipSalary(leadershipSalary);
            byBuilding.computeIfAbsent(building, key -> new SalaryTotals()).addClassLeadershipSalary(leadershipSalary);
            complex.addClassLeadershipSalary(leadershipSalary);
        }

        return new SalarySummary(byTeacher, byBuilding, complex);
    }

    private String salaryTeacherKey(Long teacherId, String fio) {
        return teacherId == null
                ? "fio:" + String.valueOf(fio).trim().toLowerCase(Locale.ROOT)
                : "id:" + teacherId;
    }

    private boolean isFirstHalfSalaryRow(ManualLoadEntry row) {
        return row.getStudyPeriod() != StudyPeriod.H2;
    }

    private BigDecimal calculateHourSalary(ManualLoadEntry row,
                                           Map<String, Integer> classSizeByClass,
                                           Map<String, BigDecimal> subjectCoefficientByLevel,
                                           Set<String> groupCoefficientSubjects,
                                           BigDecimal studentHourRate) {
        return calculateHourSalaryDetails(row, classSizeByClass, subjectCoefficientByLevel, groupCoefficientSubjects, studentHourRate).hoursSalary();
    }

    private SalaryRowDetails calculateHourSalaryDetails(ManualLoadEntry row,
                                           Map<String, Integer> classSizeByClass,
                                           Map<String, BigDecimal> subjectCoefficientByLevel,
                                           Set<String> groupCoefficientSubjects,
                                           BigDecimal studentHourRate) {
        int classSize = classSizeFor(classSizeByClass, row.getClassName());
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
        BigDecimal coefficient = subjectCoefficient(row, subjectCoefficientByLevel);
        BigDecimal groupCoefficient = groupCoefficient(row, group, safeChildrenCount, groupCoefficientSubjects);
        BigDecimal baseSalary = studentHourRate
                .multiply(BigDecimal.valueOf(safeChildrenCount))
                .multiply(BigDecimal.valueOf(Math.max(subjectHours, 0)))
                .multiply(STUDENT_HOUR_MULTIPLIER);
        BigDecimal subjectBonus = baseSalary.multiply(coefficient.subtract(BigDecimal.ONE));
        BigDecimal groupBonus = baseSalary.multiply(groupCoefficient.subtract(BigDecimal.ONE));
        BigDecimal result = baseSalary.add(subjectBonus).add(groupBonus);
        return new SalaryRowDetails(coefficient, groupCoefficient, result);
    }

    private BigDecimal groupCoefficient(ManualLoadEntry row, String group, int safeChildrenCount, Set<String> groupCoefficientSubjects) {
        boolean enabled = row.getSubjectId() != null
                ? groupCoefficientSubjects.contains("id:" + row.getSubjectId())
                : groupCoefficientSubjects.contains("name:" + normalizeToken(row.getSubjectName()));
        if (group.isBlank() || !enabled) {
            return BigDecimal.ONE;
        }
        return GROUP_BASE_SIZE.divide(BigDecimal.valueOf(safeChildrenCount), 10, RoundingMode.HALF_UP);
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

    private double coefficientValue(BigDecimal value) {
        return value == null ? 1D : value.setScale(4, RoundingMode.HALF_UP).doubleValue();
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

    private record SalaryRowDetails(BigDecimal subjectCoefficient,
                                    BigDecimal groupCoefficient,
                                    BigDecimal hoursSalary) {}

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
            validateManualLoadImportHeaders(sheet);
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
                Long classId = parseLong(readCell(row, 12));
                Long metaGroupId = parseLong(readCell(row, 13));
                Long teacherId = parseLong(readCell(row, 14));
                Long subjectId = parseLong(readCell(row, 15));
                TeacherDirectoryEntry teacherForImport = null;
                SubjectCatalogEntry subjectForImport = null;
                try {
                    validateStrictImportIds(academicYear, classId, metaGroupId, teacherId, subjectId, i + 1);
                    teacherForImport = teacherDirectoryRepository.findById(teacherId)
                            .orElseThrow(() -> new IllegalArgumentException("teacher_id не найден в справочнике — " + teacherId));
                    subjectForImport = subjectCatalogRepository.findById(subjectId)
                            .orElseThrow(() -> new IllegalArgumentException("subject_id не найден в справочнике — " + subjectId));
                } catch (IllegalArgumentException ex) {
                    errors.add("Строка " + (i + 1) + ": " + ex.getMessage());
                    continue;
                }
                String fio = teacherForImport.getFioTeacher();
                if (!fio.toLowerCase(Locale.ROOT).contains("вакан")) {
                    TeacherDirectoryEntry teacher = teacherForImport;
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
                request.setSubjectName(subjectForImport.getSubjectName());
                request.setSubjectId(subjectId);
                request.setGroupNameEducationalPlan(emptyToNull(readCell(row, 4)));
                request.setStudyPeriod(parseStudyPeriod(readCell(row, 5)));
                request.setLoadFromDate(parseDate(readCell(row, 6)));
                request.setLoadToDate(parseDate(readCell(row, 7)));
                Integer load = parseInteger(readCell(row, 8));
                request.setLoad(load);
                request.setGroupLoad(request.getGroupNameEducationalPlan() == null ? null : load);
                request.setCurriculumPart(parseCurriculumPart(readCell(row, 16)));
                request.setCurriculumModuleId(parseLong(readCell(row, 17)));
                request.setEducationLevel(parseEducationLevel(readCell(row, 9)));
                request.setFioTeacher(fio);
                request.setTeacherId(teacherId);
                request.setClassId(classId);
                request.setMetaGroupId(metaGroupId);
                try {
                    validate(request);
                    validateImportCurriculumRule(academicYear, request, i + 1);
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
                .flatMap(row -> expandForStats(row).stream())
                .filter(row -> !assignedKeys.contains(healthSoftKey(row)))
                .mapToInt(row -> Math.max(row.getPlannedHours() == null ? 0 : row.getPlannedHours().intValue(), 0))
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
                row.getCurriculumPart()
        );
    }

    private String healthSoftKey(CurriculumPlanEntry row) {
        return exportRowSoftKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                groupNameForStats(row),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getCurriculumPart()
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
        if (row.isSubgroupRequired()) {
            int groupIndex = row.getSubgroupCount() == null ? 1 : row.getSubgroupCount();
            return groupIndex == 2 ? "Группа 2" : "Группа 1";
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
        first.setSubgroupCount(1);
        result.add(first);
        CurriculumPlanEntry second = new CurriculumPlanEntry();
        copyForStats(row, second);
        second.setPlannedHours(row.getSubgroup2Hours() != null ? java.math.BigDecimal.valueOf(row.getSubgroup2Hours()) : row.getPlannedHours());
        second.setEducationLevel(row.getSubgroup2EducationLevel() != null ? row.getSubgroup2EducationLevel() : row.getEducationLevel());
        second.setSubgroupCount(2);
        result.add(second);
        return result;
    }

    private void copyForStats(CurriculumPlanEntry from, CurriculumPlanEntry to) {
        to.setAcademicYear(from.getAcademicYear());
        to.setNumberSchoolBuilding(from.getNumberSchoolBuilding());
        to.setClassName(from.getClassName());
        to.setSubjectName(from.getSubjectName());
        to.setStudyPeriod(from.getStudyPeriod());
        to.setCurriculumPart(from.getCurriculumPart());
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
            if (row.isModularSystem()) {
                for (org.school.personalLoad.model.CurriculumModule module : row.getModules()) {
                    if (module.isSubgroupRequired()) {
                        result.add(toTemplateRow(academicYear, row, module, 1, existingByKey, existingBySoftKey));
                        result.add(toTemplateRow(academicYear, row, module, 2, existingByKey, existingBySoftKey));
                    } else {
                        result.add(toTemplateRow(academicYear, row, module, null, existingByKey, existingBySoftKey));
                    }
                }
            } else if (row.isSubgroupRequired()) {
                result.add(toTemplateRow(academicYear, row, null, 1, existingByKey, existingBySoftKey));
                result.add(toTemplateRow(academicYear, row, null, 2, existingByKey, existingBySoftKey));
            } else {
                result.add(toTemplateRow(academicYear, row, null, null, existingByKey, existingBySoftKey));
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
                                                org.school.personalLoad.model.CurriculumModule module,
                                                Integer groupIndex,
                                                Map<String, ManualLoadEntry> existingByKey,
                                                Map<String, ManualLoadEntry> existingBySoftKey) {
        StudyPeriod studyPeriod = curriculum.getStudyPeriod() == null ? StudyPeriod.YEAR : curriculum.getStudyPeriod();
        StudyPeriodSettingService.DateRange range = studyPeriodSettingService.resolveDateRange(academicYear, curriculum.getClassName(), studyPeriod);
        String groupName = groupIndex == null ? null : ("Группа " + groupIndex);
        int loadHours = module == null
                ? (curriculum.getPlannedHours() == null ? 0 : curriculum.getPlannedHours().intValue())
                : module.getPlannedHours().intValue();
        EducationLevel level = module == null ? curriculum.getEducationLevel() : module.getEducationLevel();
        if (groupIndex != null) {
            Integer groupHours = groupIndex == 1
                    ? (module == null ? curriculum.getSubgroup1Hours() : module.getSubgroup1Hours())
                    : (module == null ? curriculum.getSubgroup2Hours() : module.getSubgroup2Hours());
            if (groupHours != null) loadHours = groupHours;
            EducationLevel groupLevel = groupIndex == 1
                    ? (module == null ? curriculum.getSubgroup1EducationLevel() : module.getSubgroup1EducationLevel())
                    : (module == null ? curriculum.getSubgroup2EducationLevel() : module.getSubgroup2EducationLevel());
            if (groupLevel != null) level = groupLevel;
        }
        Long moduleId = module == null ? null : module.getId();
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
                curriculum.getCurriculumPart() == null ? CurriculumPart.CORE : curriculum.getCurriculumPart(),
                level,
                "",
                null,
                curriculum.getSubjectId(),
                manualClassId(curriculum),
                manualMetaGroupId(curriculum),
                moduleId,
                module == null ? null : module.getModuleName(),
                moduleAwareKey(exportRowKey(academicYear, curriculum.getNumberSchoolBuilding(), curriculum.getClassName(), curriculum.getSubjectName(), groupName, studyPeriod, range.startDate(), range.endDate(), curriculum.getCurriculumPart()), moduleId)
        );
        ManualLoadEntry existing = existingByKey.get(template.rowKey());
        if (existing == null) {
            existing = existingBySoftKey.get(moduleAwareKey(exportRowSoftKey(
                    template.academicYear(),
                    template.numberSchoolBuilding(),
                    template.className(),
                    template.subjectName(),
                    template.groupNameEducationalPlan(),
                    template.studyPeriod(),
                    template.curriculumPart()
            ), template.curriculumModuleId()));
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
                    template.curriculumPart(),
                    template.educationLevel(),
                    existing.getFioTeacher(),
                    existing.getTeacherId(),
                    template.subjectId(),
                    template.classId(),
                    template.metaGroupId(),
                    template.curriculumModuleId(),
                    template.moduleName(),
                    template.rowKey()
            );
        }
        return template;
    }

    private String manualRowKey(ManualLoadEntry row) {
        return moduleAwareKey(exportRowKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                row.getGroupNameEducationalPlan(),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getLoadFromDate(),
                row.getLoadToDate(),
                row.getCurriculumPart()
        ), row.getCurriculumModuleId());
    }

    private String manualRowSoftKey(ManualLoadEntry row) {
        return moduleAwareKey(exportRowSoftKey(
                row.getAcademicYear(),
                row.getNumberSchoolBuilding(),
                row.getClassName(),
                row.getSubjectName(),
                row.getGroupNameEducationalPlan(),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR : row.getStudyPeriod(),
                row.getCurriculumPart()
        ), row.getCurriculumModuleId());
    }

    private String moduleAwareKey(String baseKey, Long moduleId) {
        return baseKey + "|module:" + String.valueOf(moduleId == null ? "" : moduleId);
    }

    private String manualLoadDuplicateKey(ManualLoadEntry row) {
        return String.join("|",
                normalizeToken(row.getAcademicYear()),
                String.valueOf(row.getTeacherId()),
                normalizeToken(row.getFioTeacher()),
                normalizeToken(row.getNumberSchoolBuilding()),
                String.valueOf(row.getSchoolBuildingId()),
                String.valueOf(row.getSubjectId()),
                normalizeToken(row.getSubjectName()),
                String.valueOf(row.getClassId()),
                String.valueOf(row.getMetaGroupId()),
                normalizeToken(row.getClassName()),
                String.valueOf(row.getCurriculumModuleId()),
                normalizeToken(row.getGroupNameEducationalPlan()),
                row.getCurriculumPart() == null ? CurriculumPart.CORE.name() : row.getCurriculumPart().name(),
                String.valueOf(row.getGroupLoad() == null ? row.getLoad() : row.getGroupLoad()),
                String.valueOf(row.getLoad()),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR.name() : row.getStudyPeriod().name(),
                String.valueOf(row.getLoadFromDate()),
                String.valueOf(row.getLoadToDate())
        );
    }

    private String exportRowSoftKey(String year,
                                    String building,
                                    String className,
                                    String subject,
                                    String group,
                                    StudyPeriod studyPeriod,
                                    CurriculumPart curriculumPart) {
        return String.join("|",
                normalizeToken(year),
                normalizeToken(building),
                normalizeToken(ClassNameNormalizer.normalize(className)),
                normalizeToken(subject),
                normalizeToken(group),
                normalizeToken(studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()),
                normalizeToken(curriculumPart == null ? CurriculumPart.CORE.name() : curriculumPart.name()));
    }

    private String exportRowKey(String year,
                                String building,
                                String className,
                                String subject,
                                String group,
                                StudyPeriod studyPeriod,
                                LocalDate from,
                                LocalDate to,
                                CurriculumPart curriculumPart) {
        return String.join("|",
                normalizeToken(year),
                normalizeToken(building),
                normalizeToken(ClassNameNormalizer.normalize(className)),
                normalizeToken(subject),
                normalizeToken(group),
                normalizeToken(studyPeriod == null ? StudyPeriod.YEAR.name() : studyPeriod.name()),
                normalizeToken(from == null ? "" : from.toString()),
                normalizeToken(to == null ? "" : to.toString()),
                normalizeToken(curriculumPart == null ? CurriculumPart.CORE.name() : curriculumPart.name()));
    }

    private java.util.Set<Long> scopedClassIds(ManualLoadBulkRequest request, List<ManualLoadEntry> entries) {
        java.util.Set<Long> classIds = new java.util.LinkedHashSet<>();
        if (request != null && request.getClassIds() != null) {
            request.getClassIds().stream().filter(java.util.Objects::nonNull).forEach(classIds::add);
        }
        entries.stream().map(ManualLoadEntry::getClassId).filter(java.util.Objects::nonNull).forEach(classIds::add);
        return classIds;
    }

    private java.util.Set<Long> scopedMetaGroupIds(List<ManualLoadEntry> entries) {
        java.util.Set<Long> metaGroupIds = new java.util.LinkedHashSet<>();
        entries.stream().map(ManualLoadEntry::getMetaGroupId).filter(java.util.Objects::nonNull).forEach(metaGroupIds::add);
        return metaGroupIds;
    }

    private void validateAddressScopeTargets(String academicYear, Long schoolBuildingId, java.util.Set<Long> classIds, java.util.Set<Long> metaGroupIds) {
        if (academicYear == null || academicYear.isBlank() || schoolBuildingId == null) {
            throw new IllegalArgumentException("academicYear and schoolBuildingId are required for BUILDING_ADDRESS scope");
        }
        boolean hasClasses = classIds != null && !classIds.isEmpty();
        boolean hasMetaGroups = metaGroupIds != null && !metaGroupIds.isEmpty();
        if (!hasClasses && !hasMetaGroups) {
            throw new IllegalArgumentException("classIds or metaGroupIds are required for BUILDING_ADDRESS scope");
        }
        if (hasClasses) {
            java.util.Map<Long, ClassroomLeadershipEntry> classesById = classroomLeadershipRepository.findAllById(classIds).stream()
                    .collect(java.util.stream.Collectors.toMap(ClassroomLeadershipEntry::getId, java.util.function.Function.identity()));
            java.util.List<Long> invalidIds = classIds.stream()
                    .filter(id -> !classBelongsToAddressScope(classesById.get(id), academicYear, schoolBuildingId))
                    .toList();
            if (!invalidIds.isEmpty()) {
                throw new IllegalArgumentException("classIds do not belong to selected schoolBuildingId=" + schoolBuildingId + ": " + invalidIds);
            }
        }
        if (hasMetaGroups) {
            java.util.Map<Long, MetaGroup> metaGroupsById = metaGroupRepository.findAllById(metaGroupIds).stream()
                    .collect(java.util.stream.Collectors.toMap(MetaGroup::getId, java.util.function.Function.identity()));
            java.util.List<Long> invalidIds = metaGroupIds.stream()
                    .filter(id -> !metaGroupBelongsToAddressScope(metaGroupsById.get(id), academicYear, schoolBuildingId))
                    .toList();
            if (!invalidIds.isEmpty()) {
                throw new IllegalArgumentException("metaGroupIds do not belong to selected schoolBuildingId=" + schoolBuildingId + ": " + invalidIds);
            }
        }
    }

    private boolean classBelongsToAddressScope(ClassroomLeadershipEntry entry, String academicYear, Long schoolBuildingId) {
        return entry != null
                && normalizeToken(entry.getAcademicYear()).equals(normalizeToken(academicYear))
                && java.util.Objects.equals(entry.getSchoolBuildingId(), schoolBuildingId);
    }

    private boolean metaGroupBelongsToAddressScope(MetaGroup metaGroup, String academicYear, Long schoolBuildingId) {
        if (metaGroup == null) {
            return false;
        }
        if (metaGroup.getAcademicYear() != null && !java.util.Objects.equals(metaGroup.getAcademicYear(), academicYear)) {
            return false;
        }
        if (metaGroup.getSchoolBuildingId() == null) {
            throw new IllegalArgumentException("Для метагруппы не выбрана физическая площадка проведения. Укажите площадку в редактировании метагруппы.");
        }
        return java.util.Objects.equals(metaGroup.getSchoolBuildingId(), schoolBuildingId);
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

    private Long requiredTeacherId(ManualLoadEntry row) {
        if (row.getTeacherId() == null) {
            throw new IllegalStateException("У педагога не заполнен teacherId: " + normalizeDisplayValue(row.getFioTeacher()));
        }
        return row.getTeacherId();
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

    private void validateManualLoadImportHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null
                || !"CLASS_ID".equalsIgnoreCase(readCell(header, 12).trim())
                || !"META_GROUP_ID".equalsIgnoreCase(readCell(header, 13).trim())
                || !"TEACHER_ID".equalsIgnoreCase(readCell(header, 14).trim())
                || !"SUBJECT_ID".equalsIgnoreCase(readCell(header, 15).trim())
                || !"CURRICULUM_PART".equalsIgnoreCase(readCell(header, 16).trim())) {
            throw new IllegalArgumentException("Файл создан в старом формате. Выгрузите новый шаблон нагрузки и перенесите данные в него.");
        }
        String moduleHeader = readCell(header, 17).trim();
        if (!moduleHeader.isBlank() && !"CURRICULUM_MODULE_ID".equalsIgnoreCase(moduleHeader)) {
            throw new IllegalArgumentException("Колонка 18 должна называться CURRICULUM_MODULE_ID");
        }
    }

    private void validateStrictImportIds(String academicYear,
                                         Long classId,
                                         Long metaGroupId,
                                         Long teacherId,
                                         Long subjectId,
                                         int rowNumber) {
        if (teacherId == null) {
            throw new IllegalArgumentException("TEACHER_ID is required");
        }
        if (subjectId == null) {
            throw new IllegalArgumentException("SUBJECT_ID is required");
        }
        if (classId == null && metaGroupId == null) {
            throw new IllegalArgumentException("укажите ровно один FK: CLASS_ID или META_GROUP_ID");
        }
        if (classId != null && metaGroupId != null) {
            throw new IllegalArgumentException("нельзя одновременно указывать CLASS_ID и META_GROUP_ID");
        }
        if (classId != null && classroomLeadershipRepository.findById(classId).isEmpty()) {
            throw new IllegalArgumentException("class_id не найден: " + classId);
        }
        if (metaGroupId != null) {
            MetaGroup metaGroup = metaGroupRepository.findById(metaGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("meta_group_id не найден: " + metaGroupId));
            if (metaGroup.getAcademicYear() != null && !academicYear.equals(metaGroup.getAcademicYear())) {
                throw new IllegalArgumentException("meta_group_id относится к другому учебному году: " + metaGroupId);
            }
        }
        if (teacherDirectoryRepository.findById(teacherId).isEmpty()) {
            throw new IllegalArgumentException("teacher_id не найден в справочнике — " + teacherId);
        }
        if (subjectCatalogRepository.findById(subjectId).isEmpty()) {
            throw new IllegalArgumentException("subject_id не найден в справочнике — " + subjectId);
        }
    }

    private void validateImportCurriculumRule(String academicYear, ManualLoadEntryRequest request, int rowNumber) {
        StudyPeriod period = resolveStudyPeriod(academicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate());
        Long subjectId = request.getSubjectId();
        CurriculumPlanEntry rule;
        if (request.getMetaGroupId() != null) {
            rule = findRuleByMetaGroupIdAndSubjectId(academicYear, request.getMetaGroupId(), subjectId, request.getCurriculumPart(), request.getEducationLevel(), period)
                    .orElseThrow(() -> new IllegalArgumentException("не найдено curriculum-правило метагруппы для meta_group_id=" + request.getMetaGroupId() + " и subject_id=" + subjectId));
            if (!isExplicitMetaGroupRow(rule)) {
                throw new IllegalArgumentException("curriculum-правило meta_group_id=" + request.getMetaGroupId() + " не является explicit строкой метагруппы");
            }
            return;
        }
        rule = findRuleByClassIdAndSubjectId(academicYear, request.getClassId(), subjectId, request.getCurriculumPart(), request.getEducationLevel(), period)
                .orElseThrow(() -> new IllegalArgumentException("не найдено curriculum-правило обычного класса для class_id=" + request.getClassId() + " и subject_id=" + subjectId));
        if (!contributesToManualLoad(rule)) {
            throw new IllegalArgumentException("ordinary member row метагруппы не должна импортироваться как отдельная нагрузка");
        }
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleByClassIdAndSubjectId(String academicYear, Long classId, Long subjectId, CurriculumPart curriculumPart, EducationLevel educationLevel, StudyPeriod effectiveStudyPeriod) {
        return candidateStudyPeriods(effectiveStudyPeriod).stream()
                .map(period -> {
                    java.util.Optional<CurriculumPlanEntry> exact = curriculumPart == null
                            ? curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, classId, subjectId, educationLevel, period)
                            : curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubject_IdAndCurriculumPartAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, classId, subjectId, curriculumPart, educationLevel, period);
                    if (exact.isPresent() || curriculumPart == null) return exact;
                    return curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubject_IdAndCurriculumPartAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, classId, subjectId, curriculumPart, period)
                            .filter(CurriculumPlanEntry::isModularSystem);
                })
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleByMetaGroupIdAndSubjectId(String academicYear, Long metaGroupId, Long subjectId, CurriculumPart curriculumPart, EducationLevel educationLevel, StudyPeriod effectiveStudyPeriod) {
        return candidateStudyPeriods(effectiveStudyPeriod).stream()
                .map(period -> {
                    java.util.Optional<CurriculumPlanEntry> exact = curriculumPart == null
                            ? curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, metaGroupId, subjectId, educationLevel, period)
                            : curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndCurriculumPartAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, metaGroupId, subjectId, curriculumPart, educationLevel, period);
                    if (exact.isPresent() || curriculumPart == null) return exact;
                    return curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndCurriculumPartAndStudyPeriodAndDeprecatedFalse(
                                    academicYear, metaGroupId, subjectId, curriculumPart, period)
                            .filter(CurriculumPlanEntry::isModularSystem);
                })
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

    private CurriculumPart parseCurriculumPart(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        return value.isBlank() ? CurriculumPart.CORE : CurriculumPart.valueOf(value);
    }

    private String emptyToNull(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Long resolveClassId(String academicYear, ManualLoadEntryRequest request) {
        if (request.getClassId() == null) {
            throw new IllegalArgumentException("class_id is required for ordinary manual-load row");
        }
        if (classroomLeadershipRepository.findById(request.getClassId()).isEmpty()) {
            throw new IllegalArgumentException("class_id не найден: " + request.getClassId());
        }
        return request.getClassId();
    }

    private ManualLoadEntry toEntity(ManualLoadEntryRequest request) {
        validate(request);
        String effectiveAcademicYear = resolveAcademicYearOrDefault(request.getAcademicYear());
        ManualLoadEntry entity = new ManualLoadEntry();
        entity.setAcademicYear(effectiveAcademicYear);
        TeacherDirectoryEntry teacher = resolveTeacher(request);
        entity.setTeacherId(teacher.getId());
        entity.setFioTeacher(teacher.getFioTeacher());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        boolean explicitMetaGroup = isExplicitMetaGroupRequest(request);
        entity.setClassId(explicitMetaGroup ? null : resolveClassId(effectiveAcademicYear, request));
        entity.setMetaGroupId(explicitMetaGroup ? resolveMetaGroupId(effectiveAcademicYear, request) : null);
        if (explicitMetaGroup) {
            validateMetaGroupHasPhysicalSite(entity.getMetaGroupId());
            metaGroupRepository.findById(entity.getMetaGroupId())
                    .map(MetaGroup::getSchoolBuildingId)
                    .ifPresent(entity::setSchoolBuildingId);
        } else if (entity.getClassId() != null) {
            classroomLeadershipRepository.findById(entity.getClassId())
                    .map(ClassroomLeadershipEntry::getSchoolBuildingId)
                    .ifPresent(entity::setSchoolBuildingId);
        }
        SubjectCatalogEntry subject = resolveSubject(request);
        entity.setSubject(subject);
        entity.setSubjectName(subject.getSubjectName());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        entity.setLoad(request.getLoad());
        entity.setGroupNameEducationalPlan(request.getGroupNameEducationalPlan());
        entity.setGroupLoad(request.getGroupLoad());
        entity.setCurriculumModuleId(request.getCurriculumModuleId());
        entity.setCurriculumPart(request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart());
        entity.setEducationLevel(request.getEducationLevel());
        entity.setStudyPeriod(resolveStudyPeriod(effectiveAcademicYear, request.getClassName(), request.getStudyPeriod(), request.getLoadFromDate(), request.getLoadToDate()));
        entity.setLoadFromDate(request.getLoadFromDate());
        entity.setLoadToDate(request.getLoadToDate());
        entity.setContinuityStatus(request.getContinuityStatus() == null ? ContinuityStatus.UNKNOWN : request.getContinuityStatus());
        return entity;
    }


    private TeacherDirectoryEntry resolveTeacher(ManualLoadEntryRequest request) {
        if (request.getTeacherId() == null) {
            throw new IllegalArgumentException("teacher_id is required for manual-load row");
        }
        return teacherDirectoryRepository.findById(request.getTeacherId())
                .filter(teacher -> !teacher.isArchived())
                .orElseThrow(() -> new IllegalArgumentException("teacher_id не найден в справочнике: " + request.getTeacherId()));
    }

    private SubjectCatalogEntry resolveSubject(ManualLoadEntryRequest request) {
        if (request.getSubjectId() == null) {
            throw new IllegalArgumentException("subject_id is required for manual-load row");
        }
        return subjectCatalogRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("subject_id не найден в справочнике: " + request.getSubjectId()));
    }

    private CurriculumPlanEntry validateAgainstCurriculum(ManualLoadEntry entry) {
        StudyPeriod effectiveStudyPeriod = resolveStudyPeriod(entry.getAcademicYear(), entry.getClassName(), entry.getStudyPeriod(), entry.getLoadFromDate(), entry.getLoadToDate());
        CurriculumPlanEntry rule = findRuleByFk(entry, effectiveStudyPeriod)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum rule not found for class=" + entry.getClassName() +
                ", subject=" + entry.getSubjectName() + ", level=" + entry.getEducationLevel() + ", period=" + effectiveStudyPeriod));

        int effectiveLoad = entry.getGroupLoad() != null ? entry.getGroupLoad() : entry.getLoad();
        org.school.personalLoad.model.CurriculumModule module = resolveCurriculumModule(rule, entry.getCurriculumModuleId());
        BigDecimal allowedHours = resolveAllowedHours(rule, module, entry.getGroupNameEducationalPlan());
        if (BigDecimal.valueOf(effectiveLoad).compareTo(allowedHours) > 0) {
            throw new IllegalArgumentException("Load exceeds planned hours for curriculum rule");
        }

        boolean subgroupRequired = module == null ? rule.isSubgroupRequired() : module.isSubgroupRequired();
        if (subgroupRequired) {
            if (entry.getGroupNameEducationalPlan() == null || entry.getGroupNameEducationalPlan().isBlank()) {
                throw new IllegalArgumentException("groupNameEducationalPlan is required because subgroupRequired=true in curriculum");
            }
        }

        return rule;
    }

    private org.school.personalLoad.model.CurriculumModule resolveCurriculumModule(CurriculumPlanEntry rule, Long moduleId) {
        if (!rule.isModularSystem()) {
            if (moduleId != null) {
                throw new IllegalArgumentException("curriculumModuleId задан для немодульного предмета");
            }
            return null;
        }
        if (moduleId == null) {
            throw new IllegalArgumentException("curriculumModuleId обязателен для модульного предмета");
        }
        return rule.getModules().stream()
                .filter(module -> java.util.Objects.equals(module.getId(), moduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Модуль не найден в выбранном предмете: " + moduleId));
    }

    private BigDecimal resolveAllowedHours(CurriculumPlanEntry rule,
                                           org.school.personalLoad.model.CurriculumModule module,
                                           String groupNameEducationalPlan) {
        boolean subgroupRequired = module == null ? rule.isSubgroupRequired() : module.isSubgroupRequired();
        BigDecimal plannedHours = module == null ? rule.getPlannedHours() : module.getPlannedHours();
        Integer subgroup1Hours = module == null ? rule.getSubgroup1Hours() : module.getSubgroup1Hours();
        Integer subgroup2Hours = module == null ? rule.getSubgroup2Hours() : module.getSubgroup2Hours();
        if (!subgroupRequired) {
            return plannedHours == null ? BigDecimal.ZERO : plannedHours;
        }
        String group = String.valueOf(groupNameEducationalPlan == null ? "" : groupNameEducationalPlan).toLowerCase(java.util.Locale.ROOT);
        if (group.contains("1")) {
            return subgroup1Hours == null ? (plannedHours == null ? BigDecimal.ZERO : plannedHours) : BigDecimal.valueOf(subgroup1Hours);
        }
        if (group.contains("2")) {
            return subgroup2Hours == null ? (plannedHours == null ? BigDecimal.ZERO : plannedHours) : BigDecimal.valueOf(subgroup2Hours);
        }
        return plannedHours == null ? BigDecimal.ZERO : plannedHours;
    }


    private boolean contributesToManualLoad(CurriculumPlanEntry row) {
        if (row == null) {
            return false;
        }
        if (isExplicitMetaGroupRow(row)) {
            return true;
        }
        return !row.isExcludedFromManualLoad();
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
        if (request.getMetaGroupId() == null) {
            throw new IllegalArgumentException("meta_group_id is required for metagroup load row");
        }
        MetaGroup metaGroup = metaGroupRepository.findById(request.getMetaGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена: " + request.getMetaGroupId()));
        if (metaGroup.getAcademicYear() != null && !academicYear.equals(metaGroup.getAcademicYear())) {
            throw new IllegalArgumentException("Метагруппа относится к другому учебному году: " + request.getMetaGroupId());
        }
        return request.getMetaGroupId();
    }

    private void validateMetaGroupHasPhysicalSite(Long metaGroupId) {
        MetaGroup metaGroup = metaGroupRepository.findById(metaGroupId)
                .orElseThrow(() -> new IllegalArgumentException("Метагруппа не найдена: " + metaGroupId));
        if (metaGroup.getSchoolBuildingId() == null) {
            throw new IllegalArgumentException("Для метагруппы не выбрана физическая площадка проведения. Укажите площадку в редактировании метагруппы.");
        }
    }

    private java.util.Optional<CurriculumPlanEntry> findRuleByFk(ManualLoadEntry entry, StudyPeriod effectiveStudyPeriod) {
        Long subjectId = entry.getSubjectId();
        if (subjectId == null) {
            return java.util.Optional.empty();
        }
        if (isExplicitMetaGroupEntry(entry)) {
            if (entry.getMetaGroupId() == null) {
                return java.util.Optional.empty();
            }
            return findRuleByMetaGroupIdAndSubjectId(entry.getAcademicYear(), entry.getMetaGroupId(), subjectId, entry.getCurriculumPart(), entry.getEducationLevel(), effectiveStudyPeriod);
        }
        if (entry.getClassId() == null) {
            return java.util.Optional.empty();
        }
        return findRuleByClassIdAndSubjectId(entry.getAcademicYear(), entry.getClassId(), subjectId, entry.getCurriculumPart(), entry.getEducationLevel(), effectiveStudyPeriod)
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
        private final CurriculumPart curriculumPart;
        private final org.school.personalLoad.model.EducationLevel educationLevel;
        private final StudyPeriod studyPeriod;

        private RuleKey(String className, String subjectName, CurriculumPart curriculumPart, org.school.personalLoad.model.EducationLevel educationLevel, StudyPeriod studyPeriod) {
            this.className = className;
            this.subjectName = subjectName;
            this.curriculumPart = curriculumPart == null ? CurriculumPart.CORE : curriculumPart;
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
                    && curriculumPart == ruleKey.curriculumPart
                    && educationLevel == ruleKey.educationLevel
                    && studyPeriod == ruleKey.studyPeriod;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(className, subjectName, curriculumPart, educationLevel, studyPeriod);
        }
    }

    private record ConsolidatedTeacherGroup(String fio, String teacherKey, String primarySubject, List<ManualLoadEntry> rows) {}

    private record ManualLoadTemplateRow(String academicYear,
                                         String numberSchoolBuilding,
                                         String className,
                                         String subjectName,
                                         String groupNameEducationalPlan,
                                         StudyPeriod studyPeriod,
                                         LocalDate loadFromDate,
                                         LocalDate loadToDate,
                                         Integer load,
                                         CurriculumPart curriculumPart,
                                         EducationLevel educationLevel,
                                         String fioTeacher,
                                         Long teacherId,
                                         Long subjectId,
                                         Long classId,
                                         Long metaGroupId,
                                         Long curriculumModuleId,
                                         String moduleName,
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
