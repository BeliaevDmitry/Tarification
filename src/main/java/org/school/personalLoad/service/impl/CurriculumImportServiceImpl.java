package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.SubjectAreaNames;
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
import java.util.stream.Collectors;

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
    public byte[] exportEditableWorkbook(String academicYear) throws IOException {
        List<CurriculumPlanEntry> entries = new ArrayList<>(curriculumRepository.findAllByAcademicYear(academicYear).stream()
                .filter(e -> !e.isDeprecated())
                .toList());
        entries.sort(Comparator
                .comparing((CurriculumPlanEntry e) -> String.valueOf(e.getNumberSchoolBuilding()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getClassName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getCurriculumPart()))
                .thenComparing(e -> String.valueOf(e.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> String.valueOf(e.getEducationLevel()))
                .thenComparing(e -> String.valueOf(e.getStudyPeriod())));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            buildVisualSheet(workbook, "НОО", entries, 1, 4);
            buildVisualSheet(workbook, "ООО", entries, 5, 9);
            buildVisualSheet(workbook, "СОО", entries, 10, 11);
            Sheet legacySheet = workbook.createSheet("CURRICULUM_VISUAL");
            Row legacyRow = legacySheet.createRow(0);
            legacyRow.createCell(0).setCellValue("Экспорт перенесен в листы НОО/ООО/СОО. Этот лист оставлен для совместимости импорта.");

            workbook.write(output);
            return output.toByteArray();
        }
    }


    @Override
    public byte[] exportParallelWorkbook(String academicYear) throws IOException {
        List<CurriculumPlanEntry> entries = curriculumRepository.findAllByAcademicYear(academicYear).stream()
                .filter(e -> !e.isDeprecated())
                .filter(e -> extractParallelForExportClass(e.getClassName()) != null)
                .toList();
        Map<String, ClassroomLeadershipEntry> classDirectory = classroomRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.toMap(
                        c -> classExportKey(c.getNumberSchoolBuilding(), c.getClassName()),
                        c -> c,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Set<Integer> parallels = entries.stream()
                    .map(e -> extractParallelForExportClass(e.getClassName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (parallels.isEmpty()) {
                Sheet sheet = workbook.createSheet("Учебный план");
                sheet.createRow(0).createCell(0).setCellValue("Нет данных учебного плана за " + academicYear);
            } else {
                for (Integer parallel : parallels) {
                    buildParallelSheet(workbook, academicYear, parallel, entries, classDirectory);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void buildParallelSheet(Workbook workbook,
                                    String academicYear,
                                    int parallel,
                                    List<CurriculumPlanEntry> allEntries,
                                    Map<String, ClassroomLeadershipEntry> classDirectory) {
        List<CurriculumPlanEntry> entries = allEntries.stream()
                .filter(e -> Objects.equals(extractParallelForExportClass(e.getClassName()), parallel))
                .sorted(Comparator
                        .comparing((CurriculumPlanEntry e) -> String.valueOf(e.getNumberSchoolBuilding()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(e -> ClassNameNormalizer.normalize(e.getClassName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(e -> partOrder(e.getCurriculumPart()))
                        .thenComparing(e -> subjectAreaForExport(e.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(e -> normalizeSubject(e.getSubjectName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(e -> periodOrder(e.getStudyPeriod())))
                .toList();
        Sheet sheet = workbook.createSheet(org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(parallel + " параллель"));
        if (entries.isEmpty()) {
            sheet.createRow(0).createCell(0).setCellValue("Нет данных по " + parallel + " параллели");
            return;
        }

        List<ClassColumn> classColumns = entries.stream()
                .collect(Collectors.toMap(
                        e -> classExportKey(e.getNumberSchoolBuilding(), e.getClassName()),
                        e -> new ClassColumn(normalizeSubject(e.getNumberSchoolBuilding()), ClassNameNormalizer.normalize(e.getClassName())),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted((left, right) -> compareClassKeysForExport(left.key(), right.key()))
                .toList();

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        titleStyle.setWrapText(true);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setWrapText(true);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setThinBorders(headerStyle);

        CellStyle metaStyle = workbook.createCellStyle();
        metaStyle.cloneStyleFrom(headerStyle);
        metaStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        metaStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle partStyle = workbook.createCellStyle();
        partStyle.cloneStyleFrom(headerStyle);
        partStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        partStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle baseStyle = workbook.createCellStyle();
        baseStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        baseStyle.setWrapText(true);
        setThinBorders(baseStyle);

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.cloneStyleFrom(baseStyle);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle summaryStyle = workbook.createCellStyle();
        summaryStyle.cloneStyleFrom(numberStyle);
        Font summaryFont = workbook.createFont();
        summaryFont.setBold(true);
        summaryStyle.setFont(summaryFont);
        summaryStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28);
        titleRow.createCell(0).setCellValue("Учебный план по " + parallel + " параллели, " + academicYear);
        titleRow.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, Math.max(2, classColumns.size() + 1)));

        Row headerRow = sheet.createRow(1);
        headerRow.createCell(0).setCellValue("Часть учебного плана");
        headerRow.createCell(1).setCellValue("Предмет");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);
        for (int i = 0; i < classColumns.size(); i++) {
            Cell cell = headerRow.createCell(i + 2);
            cell.setCellValue("");
            cell.setCellStyle(headerStyle);
        }

        writeMetaRow(sheet, 2, "Период обучения", classColumns, column -> periodLabelForColumn(entries, column), metaStyle);
        writeMetaRow(sheet, 3, "Направление класса", classColumns, column -> Optional.ofNullable(classDirectory.get(column.key())).map(ClassroomLeadershipEntry::getClassDirection).orElse(""), metaStyle);
        writeMetaRow(sheet, 4, "ФИО классного руководителя", classColumns, column -> Optional.ofNullable(classDirectory.get(column.key())).map(ClassroomLeadershipEntry::getFioTeacher).orElse(""), metaStyle);
        writeMetaRow(sheet, 5, "Класс", classColumns, ClassColumn::className, metaStyle);

        int rowNum = 6;
        for (CurriculumPart part : List.of(CurriculumPart.CORE, CurriculumPart.FORMABLE, CurriculumPart.EXTRACURRICULAR, CurriculumPart.CORRECTIONAL)) {
            List<CurriculumPlanEntry> partEntries = entries.stream()
                    .filter(e -> (e.getCurriculumPart() == null ? CurriculumPart.CORE : e.getCurriculumPart()) == part)
                    .toList();
            if (partEntries.isEmpty()) continue;

            Row partRow = sheet.createRow(rowNum++);
            partRow.createCell(0).setCellValue(partDisplayName(part));
            partRow.getCell(0).setCellStyle(partStyle);
            for (int c = 1; c <= classColumns.size() + 1; c++) {
                Cell cell = partRow.createCell(c);
                cell.setCellStyle(partStyle);
            }
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(partRow.getRowNum(), partRow.getRowNum(), 0, classColumns.size() + 1));

            Map<String, List<CurriculumPlanEntry>> subjectRows = partEntries.stream()
                    .collect(Collectors.groupingBy(e -> subjectAreaForExport(e.getSubjectName()) + "|" + normalizeSubject(e.getSubjectName()), LinkedHashMap::new, Collectors.toList()));
            List<Map.Entry<String, List<CurriculumPlanEntry>>> sortedSubjects = subjectRows.entrySet().stream()
                    .sorted(Comparator
                            .comparing((Map.Entry<String, List<CurriculumPlanEntry>> e) -> subjectAreaOrderForExport(e.getKey().split("\\|", 2)[0]))
                            .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (Map.Entry<String, List<CurriculumPlanEntry>> subjectEntry : sortedSubjects) {
                String[] parts = subjectEntry.getKey().split("\\|", 2);
                String area = parts.length > 0 ? parts[0] : "";
                String subject = parts.length > 1 ? parts[1] : subjectEntry.getKey();
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(part == CurriculumPart.CORE ? area : partDisplayName(part));
                row.createCell(1).setCellValue(subject);
                row.getCell(0).setCellStyle(baseStyle);
                row.getCell(1).setCellStyle(baseStyle);

                for (int i = 0; i < classColumns.size(); i++) {
                    ClassColumn column = classColumns.get(i);
                    String rendered = renderHoursForColumn(subjectEntry.getValue(), column);
                    Cell cell = row.createCell(i + 2);
                    cell.setCellValue(rendered);
                    cell.setCellStyle(numberStyle);
                }
            }
        }

        rowNum = appendParallelTotalRows(sheet, rowNum, entries, classColumns, summaryStyle);

        for (int r = 1; r < rowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c <= classColumns.size() + 1; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
                if (cell.getCellStyle() == null || cell.getCellStyle().getIndex() == 0) cell.setCellStyle(baseStyle);
            }
        }
        sheet.createFreezePane(2, 6);
        sheet.setColumnWidth(0, 8500);
        sheet.setColumnWidth(1, 9000);
        for (int c = 2; c <= classColumns.size() + 1; c++) {
            sheet.setColumnWidth(c, 3000);
        }
    }

    private int appendParallelTotalRows(Sheet sheet,
                                        int startRow,
                                        List<CurriculumPlanEntry> entries,
                                        List<ClassColumn> classColumns,
                                        CellStyle style) {
        int rowNum = startRow;
        rowNum = appendParallelTotalRow(sheet, rowNum, "Итого основная часть", entries, classColumns, style, CurriculumPart.CORE);
        rowNum = appendParallelTotalRow(sheet, rowNum, "Итого формируемая часть", entries, classColumns, style, CurriculumPart.FORMABLE);
        rowNum = appendParallelTotalRow(sheet, rowNum, "Итого основная+формируемая часть", entries, classColumns, style, CurriculumPart.CORE, CurriculumPart.FORMABLE);
        return appendParallelTotalRow(sheet, rowNum, "Итого внеурочная часть", entries, classColumns, style, CurriculumPart.EXTRACURRICULAR);
    }

    private int appendParallelTotalRow(Sheet sheet,
                                       int rowNum,
                                       String title,
                                       List<CurriculumPlanEntry> entries,
                                       List<ClassColumn> classColumns,
                                       CellStyle style,
                                       CurriculumPart... parts) {
        Set<CurriculumPart> partSet = new HashSet<>(Arrays.asList(parts));
        List<CurriculumPlanEntry> totalEntries = entries.stream()
                .filter(e -> partSet.contains(e.getCurriculumPart() == null ? CurriculumPart.CORE : e.getCurriculumPart()))
                .toList();
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(title);
        row.createCell(1).setCellValue("");
        row.getCell(0).setCellStyle(style);
        row.getCell(1).setCellStyle(style);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum, rowNum, 0, 1));
        for (int i = 0; i < classColumns.size(); i++) {
            Cell cell = row.createCell(i + 2);
            cell.setCellValue(renderHoursForColumn(totalEntries, classColumns.get(i)));
            cell.setCellStyle(style);
        }
        return rowNum + 1;
    }

    private void writeMetaRow(Sheet sheet, int rowIndex, String title, List<ClassColumn> classColumns, java.util.function.Function<ClassColumn, String> valueFn, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(32);
        row.createCell(0).setCellValue(title);
        row.createCell(1).setCellValue("");
        row.getCell(0).setCellStyle(style);
        row.getCell(1).setCellStyle(style);
        for (int i = 0; i < classColumns.size(); i++) {
            Cell cell = row.createCell(i + 2);
            cell.setCellValue(valueFn.apply(classColumns.get(i)));
            cell.setCellStyle(style);
        }
    }

    private String renderHoursForColumn(List<CurriculumPlanEntry> values, ClassColumn column) {
        List<CurriculumPlanEntry> classValues = values.stream()
                .filter(e -> classExportKey(e.getNumberSchoolBuilding(), e.getClassName()).equals(column.key()))
                .toList();
        if (classValues.isEmpty()) return "";
        BigDecimal year = sumHours(classValues, StudyPeriod.YEAR);
        BigDecimal h1 = sumHours(classValues, StudyPeriod.H1);
        BigDecimal h2 = sumHours(classValues, StudyPeriod.H2);
        if (year.compareTo(BigDecimal.ZERO) > 0 && h1.compareTo(BigDecimal.ZERO) == 0 && h2.compareTo(BigDecimal.ZERO) == 0) {
            return formatHours(year);
        }
        if (year.compareTo(BigDecimal.ZERO) > 0) {
            h1 = h1.add(year);
            h2 = h2.add(year);
        }
        if (h1.compareTo(BigDecimal.ZERO) == 0 && h2.compareTo(BigDecimal.ZERO) == 0) return "";
        return formatHours(h1) + "/" + formatHours(h2);
    }

    private BigDecimal sumHours(List<CurriculumPlanEntry> values, StudyPeriod period) {
        return values.stream()
                .filter(v -> (v.getStudyPeriod() == null ? StudyPeriod.YEAR : v.getStudyPeriod()) == period)
                .map(CurriculumPlanEntry::getPlannedHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String periodLabelForColumn(List<CurriculumPlanEntry> entries, ClassColumn column) {
        Set<StudyPeriod> periods = entries.stream()
                .filter(e -> classExportKey(e.getNumberSchoolBuilding(), e.getClassName()).equals(column.key()))
                .map(e -> e.getStudyPeriod() == null ? StudyPeriod.YEAR : e.getStudyPeriod())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean hasH1 = periods.contains(StudyPeriod.H1);
        boolean hasH2 = periods.contains(StudyPeriod.H2);
        if (hasH1 && hasH2) return "1П/2П";
        if (hasH1) return "1П";
        if (hasH2) return "2П";
        return "";
    }

    private String classExportKey(String building, String className) {
        return normalizeSubject(building) + "|" + ClassNameNormalizer.normalize(className);
    }

    private String subjectAreaForExport(String subjectName) {
        return subjectCatalogRepository.findAll().stream()
                .filter(subject -> normalizeSubject(subject.getSubjectName()).equalsIgnoreCase(normalizeSubject(subjectName)))
                .map(SubjectCatalogEntry::getSubjectAreaName)
                .map(this::normalizeSubject)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(SubjectAreaNames.defaultArea());
    }

    private int subjectAreaOrderForExport(String area) {
        List<String> order = List.of(
                "Русский язык и литература",
                "Иностранные языки",
                "Математика и информатика",
                "Общественно-научные предметы",
                "Естественно-научные предметы",
                "Искусство",
                "Технология",
                "Физическая культура и основы безопасности и защиты Родины",
                "Коррекционно-развивающая область",
                "Иное"
        );
        int idx = order.indexOf(normalizeSubject(area));
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private String partDisplayName(CurriculumPart part) {
        return switch (part == null ? CurriculumPart.CORE : part) {
            case CORE -> "Обязательная часть";
            case FORMABLE -> "Часть, формируемая участниками образовательных отношений";
            case EXTRACURRICULAR -> "Внеурочная деятельность";
            case CORRECTIONAL -> "Коррекционная область";
        };
    }

    private int partOrder(CurriculumPart part) {
        return switch (part == null ? CurriculumPart.CORE : part) {
            case CORE -> 1;
            case FORMABLE -> 2;
            case EXTRACURRICULAR -> 3;
            case CORRECTIONAL -> 4;
        };
    }

    private int periodOrder(StudyPeriod period) {
        return switch (period == null ? StudyPeriod.YEAR : period) {
            case YEAR -> 1;
            case H1 -> 2;
            case H2 -> 3;
        };
    }

    private String formatHours(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) return "";
        return value.stripTrailingZeros().toPlainString();
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private int buildVisualSheet(Workbook workbook, String sheetName, List<CurriculumPlanEntry> allEntries, int parallelFrom, int parallelTo) {
        List<CurriculumPlanEntry> entries = allEntries.stream()
                .filter(e -> {
                    Integer p = extractParallelForExportClass(e.getClassName());
                    return p != null && p >= parallelFrom && p <= parallelTo;
                })
                .toList();
        Sheet sheet = workbook.createSheet(sheetName);
        if (entries.isEmpty()) {
            sheet.createRow(0).createCell(0).setCellValue("Нет данных");
            return 0;
        }
        List<String> classes = entries.stream()
                .map(e -> normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName()))
                .distinct()
                .sorted(this::compareClassKeysForExport)
                .toList();

        CellStyle headerStyle = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        headerStyle.setFont(bold);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle partStyle = workbook.createCellStyle();
        partStyle.cloneStyleFrom(headerStyle);
        partStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        partStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle sumStyle = workbook.createCellStyle();
        sumStyle.cloneStyleFrom(headerStyle);
        sumStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        sumStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle advancedHoursStyle = workbook.createCellStyle();
        advancedHoursStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        advancedHoursStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(sheetName + " (классы " + parallelFrom + "–" + parallelTo + ")");

        Row buildingRow = sheet.createRow(1);
        Row classRow = sheet.createRow(2);
        classRow.createCell(0).setCellValue("Блок / область");
        classRow.createCell(1).setCellValue("Предмет");
        classRow.getCell(0).setCellStyle(headerStyle);
        classRow.getCell(1).setCellStyle(headerStyle);

        String prevBuilding = null;
        int buildingStart = 1;
        for (int i = 0; i < classes.size(); i++) {
            String[] parts = classes.get(i).split("\\|", 2);
            String building = parts.length > 1 ? parts[0] : "СП0";
            String className = parts.length > 1 ? parts[1] : classes.get(i);
            int col = i + 2;
            buildingRow.createCell(col).setCellValue(building);
            classRow.createCell(col).setCellValue(className);
            classRow.getCell(col).setCellStyle(headerStyle);
            if (!Objects.equals(prevBuilding, building)) {
                if (prevBuilding != null && col - 1 > buildingStart) {
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, buildingStart, col - 1));
                }
                buildingStart = col;
                prevBuilding = building;
            }
        }
        if (prevBuilding != null && classes.size() >= 1 && classes.size() > buildingStart) {
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, buildingStart, classes.size()));
        }

        int rowNum = 3;
        Map<String, List<CurriculumPlanEntry>> byPartSubject = new LinkedHashMap<>();
        entries.forEach(e -> {
            String key = (e.getCurriculumPart() == null ? CurriculumPart.CORE : e.getCurriculumPart()) + "|" + normalizeSubject(e.getSubjectName());
            byPartSubject.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        });

        for (CurriculumPart part : List.of(CurriculumPart.CORE, CurriculumPart.FORMABLE, CurriculumPart.EXTRACURRICULAR)) {
            Row partRow = sheet.createRow(rowNum++);
            partRow.createCell(0).setCellValue(part == CurriculumPart.CORE ? "Основная часть"
                    : (part == CurriculumPart.FORMABLE ? "Формируемая часть" : "Внеурочная деятельность"));
            partRow.getCell(0).setCellStyle(partStyle);
            partRow.createCell(1).setCellValue("");
            partRow.getCell(1).setCellStyle(partStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(partRow.getRowNum(), partRow.getRowNum(), 0, 1));

            List<Map.Entry<String, List<CurriculumPlanEntry>>> subjects = byPartSubject.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(part.name() + "|"))
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            Map<String, String> coreAreas = subjectCatalogRepository.findAll().stream()
                    .collect(Collectors.toMap(s -> normalizeSubject(s.getSubjectName()), s -> normalizeSubject(s.getSubjectAreaName()), (a, b) -> a));

            for (Map.Entry<String, List<CurriculumPlanEntry>> subjectEntry : subjects) {
                String subjectName = subjectEntry.getKey().substring(subjectEntry.getKey().indexOf('|') + 1);
                Row row = sheet.createRow(rowNum++);
                if (part == CurriculumPart.CORE) {
                    row.createCell(0).setCellValue(coreAreas.getOrDefault(normalizeSubject(subjectName), ""));
                    row.createCell(1).setCellValue(subjectName);
                } else {
                    row.createCell(0).setCellValue(subjectName);
                    row.createCell(1).setCellValue("");
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 1));
                }
                for (int i = 0; i < classes.size(); i++) {
                    String classKey = classes.get(i);
                    List<CurriculumPlanEntry> classValues = subjectEntry.getValue().stream()
                            .filter(e -> (normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName())).equals(classKey))
                            .toList();
                    BigDecimal year = classValues.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.YEAR)
                            .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal h1 = classValues.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H1)
                            .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal h2 = classValues.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H2)
                            .map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    boolean subgroup = classValues.stream().anyMatch(CurriculumPlanEntry::isSubgroupRequired);
                    boolean meta = classValues.stream().anyMatch(CurriculumPlanEntry::isMetaGroup);
                    String marker = markerSuffixForExport(subgroup, meta);
                    String rendered;
                    if (year.compareTo(BigDecimal.ZERO) > 0) {
                        rendered = year.stripTrailingZeros().toPlainString() + marker;
                    } else if (subgroup) {
                        Integer subgroup1 = classValues.stream()
                                .map(CurriculumPlanEntry::getSubgroup1Hours)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                        Integer subgroup2 = classValues.stream()
                                .map(CurriculumPlanEntry::getSubgroup2Hours)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                        rendered = (subgroup1 == null ? "" : subgroup1) + "//" + (subgroup2 == null ? "" : subgroup2) + marker;
                    } else if (h1.compareTo(BigDecimal.ZERO) > 0 || h2.compareTo(BigDecimal.ZERO) > 0) {
                        String left = h1.compareTo(BigDecimal.ZERO) > 0 ? h1.stripTrailingZeros().toPlainString() : "";
                        String right = h2.compareTo(BigDecimal.ZERO) > 0 ? h2.stripTrailingZeros().toPlainString() : "";
                        rendered = left + "/" + right + marker;
                    } else {
                        rendered = "";
                    }
                    Cell cell = row.createCell(i + 2);
                    cell.setCellValue(rendered);
                    if (!rendered.isBlank() && classValues.stream().anyMatch(v -> v.getEducationLevel() == EducationLevel.ADVANCED)) {
                        cell.setCellStyle(advancedHoursStyle);
                    }
                }
            }
        }

        java.util.function.Predicate<CurriculumPlanEntry> coreFormableFilter =
                entry -> entry.getCurriculumPart() == CurriculumPart.CORE || entry.getCurriculumPart() == CurriculumPart.FORMABLE;
        java.util.function.Predicate<CurriculumPlanEntry> extracurricularFilter =
                entry -> entry.getCurriculumPart() == CurriculumPart.EXTRACURRICULAR;

        rowNum = appendPartSumRowVisual(sheet, rowNum, "Сумма О+Ф", entries, classes, coreFormableFilter, sumStyle);
        rowNum = appendPartSumRowVisual(sheet, rowNum, "Сумма внеурочной деятельности", entries, classes, extracurricularFilter, sumStyle);
        rowNum = appendLevelSumRowVisual(sheet, rowNum, "Сумма Базовый уровень", entries, classes, EducationLevel.BASIC, sumStyle);
        rowNum = appendLevelSumRowVisual(sheet, rowNum, "Сумма Углублённый уровень", entries, classes, EducationLevel.ADVANCED, sumStyle);

        if (entries.stream().anyMatch(CurriculumPlanEntry::isSubgroupRequired)) {
            Row noteRow = sheet.createRow(rowNum);
            noteRow.createCell(0).setCellValue("* предмет делится на 2 группы");
            rowNum++;
        }
        if (entries.stream().anyMatch(CurriculumPlanEntry::isMetaGroup)) {
            Row noteRow = sheet.createRow(rowNum);
            noteRow.createCell(0).setCellValue("** часы реализуются в метагруппе");
            rowNum++;
        }

        CellStyle baseStyle = workbook.createCellStyle();
        baseStyle.setWrapText(true);
        baseStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        baseStyle.setBorderBottom(BorderStyle.THIN);
        baseStyle.setBorderTop(BorderStyle.THIN);
        baseStyle.setBorderLeft(BorderStyle.THIN);
        baseStyle.setBorderRight(BorderStyle.THIN);
        for (int r = 2; r <= rowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < classes.size() + 2; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
                if (cell.getCellStyle() == null || cell.getCellStyle().getIndex() == 0) {
                    cell.setCellStyle(baseStyle);
                }
            }
        }
        sheet.setColumnWidth(0, 7000);
        sheet.setColumnWidth(1, 9000);
        for (int i = 2; i <= classes.size() + 1; i++) {
            sheet.setColumnWidth(i, 2600);
        }
        return rowNum;
    }

    private int appendLevelSumRowVisual(Sheet sheet,
                                        int rowNum,
                                        String title,
                                        List<CurriculumPlanEntry> entries,
                                        List<String> classes,
                                        EducationLevel level,
                                        CellStyle headerStyle) {
        Row sumRow = sheet.createRow(rowNum++);
        sumRow.createCell(0).setCellValue(title);
        sumRow.getCell(0).setCellStyle(headerStyle);
        for (int i = 0; i < classes.size(); i++) {
            String classKey = classes.get(i);
            List<CurriculumPlanEntry> values = entries.stream()
                    .filter(e -> e.getEducationLevel() == level)
                    .filter(e -> (normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName())).equals(classKey))
                    .toList();
            BigDecimal year = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.YEAR).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal h1 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H1).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal h2 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H2).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            String rendered = year.compareTo(BigDecimal.ZERO) > 0
                    ? year.stripTrailingZeros().toPlainString() + "/" + year.stripTrailingZeros().toPlainString()
                    : (h1.compareTo(BigDecimal.ZERO) > 0 || h2.compareTo(BigDecimal.ZERO) > 0
                    ? h1.stripTrailingZeros().toPlainString() + "/" + h2.stripTrailingZeros().toPlainString()
                    : "");
            sumRow.createCell(i + 2).setCellValue(rendered);
        }
        return rowNum;
    }

    private int appendPartSumRowVisual(Sheet sheet,
                                       int rowNum,
                                       String title,
                                       List<CurriculumPlanEntry> entries,
                                       List<String> classes,
                                       java.util.function.Predicate<CurriculumPlanEntry> filter,
                                       CellStyle style) {
        Row sumRow = sheet.createRow(rowNum++);
        sumRow.createCell(0).setCellValue(title);
        sumRow.getCell(0).setCellStyle(style);
        for (int i = 0; i < classes.size(); i++) {
            String classKey = classes.get(i);
            List<CurriculumPlanEntry> values = entries.stream()
                    .filter(filter)
                    .filter(e -> (normalizeSubject(e.getNumberSchoolBuilding()) + "|" + ClassNameNormalizer.normalize(e.getClassName())).equals(classKey))
                    .toList();
            BigDecimal year = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.YEAR).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal h1 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H1).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal h2 = values.stream().filter(v -> v.getStudyPeriod() == StudyPeriod.H2).map(CurriculumPlanEntry::getPlannedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            String rendered = year.compareTo(BigDecimal.ZERO) > 0
                    ? year.stripTrailingZeros().toPlainString() + "/" + year.stripTrailingZeros().toPlainString()
                    : (h1.compareTo(BigDecimal.ZERO) > 0 || h2.compareTo(BigDecimal.ZERO) > 0
                    ? h1.stripTrailingZeros().toPlainString() + "/" + h2.stripTrailingZeros().toPlainString()
                    : "");
            sumRow.createCell(i + 2).setCellValue(rendered);
            sumRow.getCell(i + 2).setCellStyle(style);
        }
        return rowNum;
    }

    @Override
    public CurriculumImportResult importFile(MultipartFile file, String academicYear) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");
        if (academicYear == null || academicYear.isBlank()) throw new IllegalArgumentException("academicYear is required");

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
            Map<String, String> buildingByNormalizedClass = classroomRepository.findAllByAcademicYear(academicYear).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            c -> ClassNameNormalizer.normalize(c.getClassName()),
                            c -> normalizeSubject(c.getNumberSchoolBuilding()),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            if (!editableRows.isEmpty()) {
                for (EditableImportRow row : editableRows) {
                    String normalizedClassName = ClassNameNormalizer.normalize(row.className());
                    String resolvedBuilding = resolveBuildingForClass(row.numberSchoolBuilding(), normalizedClassName, buildingByNormalizedClass);
                    StudyPeriodSetting resolvedEditableRule = studyPeriodSettingService.resolveRuleForClassAndPeriod(academicYear, row.className(), row.studyPeriod());
                    CurriculumPlanEntry entry = curriculumRepository
                            .findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                                    academicYear,
                                    resolvedBuilding,
                                    normalizedClassName,
                                    row.subjectName(),
                                    row.educationLevel(),
                                    row.curriculumPart(),
                                    resolvedEditableRule.getStudyPeriod(),
                                    resolvedEditableRule.getId()
                            )
                            .orElseGet(CurriculumPlanEntry::new);
                    boolean isNew = entry.getId() == null;
                    entry.setAcademicYear(academicYear);
                    entry.setStage(entry.getStage() == null ? CurriculumStage.NOO : entry.getStage());
                    entry.setNumberSchoolBuilding(resolvedBuilding);
                    entry.setClassName(normalizedClassName);
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
                    boolean explicitMetaGroupRow = isExplicitMetaGroupClassName(normalizedClassName);
                    if (explicitMetaGroupRow && row.excludedFromManualLoad()) {
                        throw new IllegalArgumentException("Строка нагрузки метагруппы должна переноситься в нагрузку");
                    }
                    entry.setMetaGroup(explicitMetaGroupRow || row.metaGroup());
                    entry.setExcludedFromManualLoad(explicitMetaGroupRow ? false : row.excludedFromManualLoad());

                    CurriculumPlanEntry saved = curriculumRepository.save(entry);
                    importedIds.add(saved.getId());
                    if (isNew) created++; else updated++;

                    boolean createdClass = ensureClassroom(academicYear, resolvedBuilding, normalizedClassName, row.classDirection(), fallbackTeacher);
                    buildingByNormalizedClass.putIfAbsent(normalizedClassName, resolvedBuilding);
                    if (createdClass) classesCreated++;

                    SubjectType subjectType = resolveSubjectType(row.curriculumPart(), row.subjectName());
                    String normalizedSubject = normalizeSubject(row.subjectName());
                    String subjectKey = subjectKey(normalizedSubject, subjectType);
                    if (!normalizedSubject.isBlank() && !existingSubjects.containsKey(subjectKey)) {
                        SubjectCatalogEntry subjectCatalogEntry = new SubjectCatalogEntry();
                        subjectCatalogEntry.setSubjectName(normalizedSubject);
                        subjectCatalogEntry.setSubjectType(subjectType);
                        subjectCatalogEntry.setSubjectAreaName(SubjectAreaNames.defaultArea());
                        existingSubjects.put(subjectKey, subjectCatalogRepository.save(subjectCatalogEntry));
                        subjectsImported++;
                    }
                }
            } else {
                for (CurriculumImportRow row : parsed) {
                    row.setAcademicYear(academicYear);
                    String normalizedClassName = ClassNameNormalizer.normalize(row.getClassName());
                    String resolvedBuilding = resolveBuildingForClass(null, normalizedClassName, buildingByNormalizedClass);
                    CurriculumPlanEntry entry = curriculumRepository
                            .findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(
                                    row.getAcademicYear(), row.getStage(), normalizedClassName, row.getSubjectName(), row.getStudyPeriod())
                            .orElseGet(CurriculumPlanEntry::new);

                    boolean isNew = entry.getId() == null;
                    entry.setAcademicYear(row.getAcademicYear());
                    entry.setStage(row.getStage());
                    entry.setClassName(normalizedClassName);
                    entry.setSubjectName(row.getSubjectName());
                    StudyPeriodSetting resolvedRule = studyPeriodSettingService.resolveRuleForClassAndPeriod(academicYear, row.getClassName(), row.getStudyPeriod());
                    entry.setStudyPeriod(resolvedRule.getStudyPeriod());
                    entry.setStudyPeriodSettingId(resolvedRule.getId());
                    entry.setPlannedHours(row.getPlannedHours());
                    entry.setCurriculumPart(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart());
                    entry.setDeprecated(false);
                    entry.setMetaGroup(row.isMetaGroup());
                    entry.setSubgroupRequired(row.isSubgroupRequired());
                    entry.setSubgroupCount(row.isSubgroupRequired() ? 2 : 0);
                    if (row.isSubgroupRequired() && row.getPlannedHours() != null) {
                        int subgroupHours = row.getPlannedHours().intValue();
                        entry.setSubgroup1Hours(subgroupHours);
                        entry.setSubgroup2Hours(subgroupHours);
                        entry.setSubgroup1EducationLevel(entry.getEducationLevel() == EducationLevel.ADVANCED ? EducationLevel.ADVANCED : EducationLevel.BASIC);
                        entry.setSubgroup2EducationLevel(entry.getEducationLevel() == EducationLevel.ADVANCED ? EducationLevel.ADVANCED : EducationLevel.BASIC);
                    } else {
                        entry.setSubgroup1Hours(null);
                        entry.setSubgroup2Hours(null);
                        entry.setSubgroup1EducationLevel(null);
                        entry.setSubgroup2EducationLevel(null);
                    }
                    if (isNew) {
                        entry.setNumberSchoolBuilding(resolvedBuilding);
                        entry.setEducationLevel(EducationLevel.BASIC);
                    }

                    if (entry.getEducationLevel() != EducationLevel.ADVANCED) {
                        entry.setEducationLevel(EducationLevel.BASIC);
                    }
                    entry.setNumberSchoolBuilding(resolveBuildingForClass(entry.getNumberSchoolBuilding(), normalizedClassName, buildingByNormalizedClass));

                    CurriculumPlanEntry saved = curriculumRepository.save(entry);
                    importedIds.add(saved.getId());
                    if (isNew) created++; else updated++;

                    boolean createdClass = ensureClassroom(academicYear, entry.getNumberSchoolBuilding(), normalizedClassName, row.getClassDirection(), fallbackTeacher);
                    buildingByNormalizedClass.putIfAbsent(normalizedClassName, entry.getNumberSchoolBuilding());
                    if (createdClass) classesCreated++;

                    SubjectType subjectType = resolveSubjectType(row);
                    String normalizedSubject = normalizeSubject(row.getSubjectName());
                    String subjectKey = subjectKey(normalizedSubject, subjectType);
                    if (!normalizedSubject.isBlank() && !existingSubjects.containsKey(subjectKey)) {
                        SubjectCatalogEntry subjectCatalogEntry = new SubjectCatalogEntry();
                        subjectCatalogEntry.setSubjectName(normalizedSubject);
                        subjectCatalogEntry.setSubjectType(subjectType);
                        subjectCatalogEntry.setSubjectAreaName(
                                row.getSubjectAreaName() == null || row.getSubjectAreaName().isBlank()
                                        ? SubjectAreaNames.defaultArea()
                                        : row.getSubjectAreaName().trim()
                        );
                        existingSubjects.put(subjectKey, subjectCatalogRepository.save(subjectCatalogEntry));
                        subjectsImported++;
                    }
                }
            }

            int deprecated = 0;
            List<CurriculumPlanEntry> all = curriculumRepository.findAll().stream()
                    .filter(e -> academicYear.equals(e.getAcademicYear()))
                    .toList();
            for (CurriculumPlanEntry e : all) {
                boolean shouldDeprecate = !importedIds.contains(e.getId());
                if (shouldDeprecate && !e.isDeprecated()) {
                    e.setDeprecated(true);
                    curriculumRepository.save(e);
                    deprecated++;
                }
            }

            Set<String> activeKeys = new HashSet<>();
            curriculumRepository.findAll().stream()
                    .filter(e -> academicYear.equals(e.getAcademicYear()))
                    .filter(e -> !e.isDeprecated())
                    .forEach(e ->
                    activeKeys.add(keyWithoutBuilding(e.getClassName(), e.getSubjectName(), e.getEducationLevel(), e.getStudyPeriod())));

            int orphaned = 0;
            List<ManualLoadEntry> loads = manualLoadRepository.findAllByAcademicYear(academicYear);
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

    private boolean ensureClassroom(String academicYear, String building, String className, String classDirection, String fallbackTeacher) {
        String resolvedDirection = classDirection == null || classDirection.isBlank() ? "Не указана" : classDirection;
        Optional<ClassroomLeadershipEntry> existing = classroomRepository
                .findByAcademicYearAndNumberSchoolBuildingAndClassName(academicYear, building, className);
        if (existing.isPresent()) {
            ClassroomLeadershipEntry entry = existing.get();
            if (!resolvedDirection.isBlank() && !resolvedDirection.equals(entry.getClassDirection())) {
                entry.setClassDirection(resolvedDirection);
                classroomRepository.save(entry);
            }
            return false;
        }
        ClassroomLeadershipEntry cls = new ClassroomLeadershipEntry();
        cls.setAcademicYear(academicYear);
        cls.setNumberSchoolBuilding(building);
        cls.setClassName(className);
        cls.setClassDirection(resolvedDirection);
        cls.setFioTeacher(fallbackTeacher);
        cls.setCampusAddress("Не указан");
        classroomRepository.save(cls);
        return true;
    }

    private String resolveBuildingForClass(String requestedBuilding, String normalizedClassName, Map<String, String> buildingByNormalizedClass) {
        String existingBuilding = buildingByNormalizedClass.get(ClassNameNormalizer.normalize(normalizedClassName));
        if (existingBuilding != null && !existingBuilding.isBlank()) {
            return existingBuilding;
        }
        String normalizedRequested = normalizeSubject(requestedBuilding);
        return normalizedRequested.isBlank() ? "СП0" : normalizedRequested;
    }

    private String currentAcademicYear() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int start = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return start + "/" + (start + 1);
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
                        row.getSubjectAreaName(),
                        row.getSubjectName(),
                        row.getPlannedHours(),
                        StudyPeriod.YEAR,
                        row.getCurriculumPart(),
                        row.isSubgroupRequired(),
                        row.isMetaGroup()
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
                        row.getSubjectAreaName(),
                        row.getSubjectName(),
                        row.getPlannedHours(),
                        StudyPeriod.YEAR,
                        row.getCurriculumPart(),
                        row.isSubgroupRequired(),
                        row.isMetaGroup()
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
            if (sheet == null) {
                return parseStageVisualSheets(workbook);
            }
            Row header = sheet.getRow(0);
            if (header == null) return new VisualParseResult(List.of(), Map.of());

            List<ClassHeaderMeta> classColumns = new ArrayList<>();
            int classStartCol = normalizeSubject(readCell(header.getCell(1))).toLowerCase(Locale.ROOT).contains("предмет") ? 2 : 1;
            for (int col = classStartCol; col < header.getLastCellNum(); col++) {
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
                String title = normalizeSubject(readCell(row.getCell(1)));
                if (title.isBlank()) title = normalizeSubject(readCell(row.getCell(0)));
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
                    String cellRaw = readCell(row.getCell(classMeta.colIndex));
                    MarkerFlags markerFlags = parseMarkerFlags(cellRaw);
                    String rawHours = markerFlags.value();
                    if (rawHours.isBlank() || "0".equals(rawHours)) continue;
                    if (rawHours.contains("//")) {
                        boolean subgroup = true;
                        boolean meta = markerFlags.metaGroup();
                        SubgroupHoursParseResult parsed = parseSubgroupHours(rawHours);
                        EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                        if (parsed != null && (parsed.h1g1() > 0 || parsed.h1g2() > 0)) {
                            int max = Math.max(parsed.h1g1(), parsed.h1g2());
                            result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                    detectedLevel, StudyPeriod.H1, BigDecimal.valueOf(max), subgroup, parsed.h1g1(), detectedLevel, parsed.h1g2(), detectedLevel, meta));
                        }
                        if (parsed != null && parsed.hasH2() && (parsed.h2g1() > 0 || parsed.h2g2() > 0)) {
                            int max = Math.max(parsed.h2g1(), parsed.h2g2());
                            result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                    detectedLevel, StudyPeriod.H2, BigDecimal.valueOf(max), subgroup, parsed.h2g1(), detectedLevel, parsed.h2g2(), detectedLevel, meta));
                        }
                        continue;
                    } else if (rawHours.contains("/")) {
                        String[] halves = rawHours.split("/", -1);
                        MarkerFlags h1Flags = parseMarkerFlags(halves.length > 0 ? halves[0] : "");
                        MarkerFlags h2Flags = parseMarkerFlags(halves.length > 1 ? halves[1] : "");
                        boolean subgroup = markerFlags.subgroupRequired() || h1Flags.subgroupRequired() || h2Flags.subgroupRequired();
                        boolean meta = markerFlags.metaGroup() || h1Flags.metaGroup() || h2Flags.metaGroup();
                        BigDecimal h1 = parseDecimal(h1Flags.value());
                        BigDecimal h2 = parseDecimal(h2Flags.value());
                    EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                    if (h1 != null && h1.compareTo(BigDecimal.ZERO) > 0) {
                        result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                detectedLevel, StudyPeriod.H1, h1, subgroup, h1.intValue(), detectedLevel, h1.intValue(), detectedLevel, meta));
                    }
                    if (h2 != null && h2.compareTo(BigDecimal.ZERO) > 0) {
                        result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                                detectedLevel, StudyPeriod.H2, h2, subgroup, h2.intValue(), detectedLevel, h2.intValue(), detectedLevel, meta));
                    }
                    continue;
                }
                BigDecimal year = parseDecimal(rawHours);
                if (year == null || year.compareTo(BigDecimal.ZERO) <= 0) continue;
                EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title,
                        detectedLevel, StudyPeriod.YEAR, year, markerFlags.subgroupRequired(), year.intValue(), detectedLevel, year.intValue(), detectedLevel, markerFlags.metaGroup()));
            }
            }
            return new VisualParseResult(result, expectedSums);
        } catch (Exception e) {
            return new VisualParseResult(List.of(), Map.of());
        }
    }

    private VisualParseResult parseStageVisualSheets(Workbook workbook) {
        List<EditableImportRow> allRows = new ArrayList<>();
        Map<String, Map<String, SumPair>> expected = new LinkedHashMap<>();
        for (String name : List.of("НОО", "ООО", "СОО")) {
            Sheet sheet = workbook.getSheet(name);
            if (sheet == null) continue;
            VisualParseResult one = parseVisualSheet(sheet, 2);
            allRows.addAll(one.rows());
            one.expectedSums().forEach((k, v) -> expected.computeIfAbsent(k, kk -> new LinkedHashMap<>()).putAll(v));
        }
        return new VisualParseResult(allRows, expected);
    }

    private VisualParseResult parseVisualSheet(Sheet sheet, int headerRowIndex) {
        Row header = sheet.getRow(headerRowIndex);
        if (header == null) return new VisualParseResult(List.of(), Map.of());

        // Парсим только новый "визуальный" формат экспорта (колонка A = "Блок / предмет / часы").
        // Для классического шаблона (где в этой строке обычно "Период обучения")
        // возвращаем пустой результат, чтобы сработал legacy-парсер CurriculumExcelParser.
        String headerTitle = normalizeSubject(readCell(header.getCell(0))).toLowerCase(Locale.ROOT);
        if (!headerTitle.contains("блок") || !headerTitle.contains("предмет")) {
            return new VisualParseResult(List.of(), Map.of());
        }
        List<ClassHeaderMeta> classColumns = new ArrayList<>();
        int classStartCol = normalizeSubject(readCell(header.getCell(1))).toLowerCase(Locale.ROOT).contains("предмет") ? 2 : 1;
        for (int col = classStartCol; col < header.getLastCellNum(); col++) {
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
        for (int rowIdx = headerRowIndex + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            String title = normalizeSubject(readCell(row.getCell(1)));
            if (title.isBlank()) title = normalizeSubject(readCell(row.getCell(0)));
            if (title.isBlank()) continue;
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.contains("основная часть")) { currentPart = CurriculumPart.CORE; continue; }
            if (lower.contains("формируем")) { currentPart = CurriculumPart.FORMABLE; continue; }
            if (lower.contains("внеуроч")) { currentPart = CurriculumPart.EXTRACURRICULAR; continue; }
            if (lower.startsWith("сумма")) {
                String label = normalizeSumLabel(lower);
                if (label != null) {
                    Map<String, SumPair> byClass = expectedSums.computeIfAbsent(label, k -> new LinkedHashMap<>());
                    for (ClassHeaderMeta classMeta : classColumns) {
                        SumPair pair = parseSumCell(readCell(row.getCell(classMeta.colIndex)));
                        if (pair != null) byClass.put(classMeta.building + "|" + classMeta.className, pair);
                    }
                }
                continue;
            }
            for (ClassHeaderMeta classMeta : classColumns) {
                String cellRaw = readCell(row.getCell(classMeta.colIndex));
                MarkerFlags markerFlags = parseMarkerFlags(cellRaw);
                String rawHours = markerFlags.value();
                if (rawHours.isBlank() || "0".equals(rawHours)) continue;
                if (rawHours.contains("//")) {
                    EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                    boolean subgroup = true;
                    boolean meta = markerFlags.metaGroup();
                    SubgroupHoursParseResult parsed = parseSubgroupHours(rawHours);
                    if (parsed != null && (parsed.h1g1() > 0 || parsed.h1g2() > 0)) {
                        int max = Math.max(parsed.h1g1(), parsed.h1g2());
                        result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title, detectedLevel, StudyPeriod.H1, BigDecimal.valueOf(max), subgroup, parsed.h1g1(), detectedLevel, parsed.h1g2(), detectedLevel, meta));
                    }
                    if (parsed != null && parsed.hasH2() && (parsed.h2g1() > 0 || parsed.h2g2() > 0)) {
                        int max = Math.max(parsed.h2g1(), parsed.h2g2());
                        result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title, detectedLevel, StudyPeriod.H2, BigDecimal.valueOf(max), subgroup, parsed.h2g1(), detectedLevel, parsed.h2g2(), detectedLevel, meta));
                    }
                } else if (rawHours.contains("/")) {
                    EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                    String[] halves = rawHours.split("/", -1);
                    MarkerFlags h1Flags = parseMarkerFlags(halves.length > 0 ? halves[0] : "");
                    MarkerFlags h2Flags = parseMarkerFlags(halves.length > 1 ? halves[1] : "");
                    boolean subgroup = markerFlags.subgroupRequired() || h1Flags.subgroupRequired() || h2Flags.subgroupRequired();
                    boolean meta = markerFlags.metaGroup() || h1Flags.metaGroup() || h2Flags.metaGroup();
                    BigDecimal h1 = parseDecimal(h1Flags.value());
                    BigDecimal h2 = parseDecimal(h2Flags.value());
                    if (h1 != null && h1.compareTo(BigDecimal.ZERO) > 0) result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title, detectedLevel, StudyPeriod.H1, h1, subgroup, h1.intValue(), detectedLevel, h1.intValue(), detectedLevel, meta));
                    if (h2 != null && h2.compareTo(BigDecimal.ZERO) > 0) result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title, detectedLevel, StudyPeriod.H2, h2, subgroup, h2.intValue(), detectedLevel, h2.intValue(), detectedLevel, meta));
                } else {
                    BigDecimal year = parseDecimal(rawHours);
                    EducationLevel detectedLevel = isAdvancedMarked(row.getCell(classMeta.colIndex)) ? EducationLevel.ADVANCED : EducationLevel.BASIC;
                    if (year != null && year.compareTo(BigDecimal.ZERO) > 0) result.add(new EditableImportRow(classMeta.building, classMeta.className, "", currentPart, title, detectedLevel, StudyPeriod.YEAR, year, markerFlags.subgroupRequired(), year.intValue(), detectedLevel, year.intValue(), detectedLevel, markerFlags.metaGroup()));
                }
            }
        }
        return new VisualParseResult(result, expectedSums);
    }

    private boolean isAdvancedMarked(Cell cell) {
        if (cell == null || cell.getCellStyle() == null) return false;
        short fg = cell.getCellStyle().getFillForegroundColor();
        return fg == IndexedColors.LIGHT_ORANGE.getIndex() || fg == IndexedColors.ORANGE.getIndex();
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
                String legacyMetaRaw = normalizeSubject(readCell(row.getCell(13)));
                String excludedRaw = normalizeSubject(readCell(row.getCell(14)));
                boolean legacyMetaGroup = Boolean.parseBoolean(legacyMetaRaw);
                boolean excludedFromManualLoad = excludedRaw.isBlank()
                        ? legacyMetaGroup && !isExplicitMetaGroupClassName(className)
                        : Boolean.parseBoolean(excludedRaw);
                if (building.isBlank() || className.isBlank() || subject.isBlank() || hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (isExplicitMetaGroupClassName(className) && excludedFromManualLoad) {
                    throw new IllegalArgumentException("Строка нагрузки метагруппы должна переноситься в нагрузку");
                }
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
                        subgroup2Level == null ? EducationLevel.BASIC : subgroup2Level,
                        legacyMetaGroup || isExplicitMetaGroupClassName(className),
                        excludedFromManualLoad
                ));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
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
            EducationLevel subgroup2EducationLevel,
            boolean metaGroup,
            boolean excludedFromManualLoad
    ) {
        private EditableImportRow(String numberSchoolBuilding,
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
                                  EducationLevel subgroup2EducationLevel,
                                  boolean metaGroup) {
            this(numberSchoolBuilding, className, classDirection, curriculumPart, subjectName, educationLevel,
                    studyPeriod, plannedHours, subgroupRequired, subgroup1Hours, subgroup1EducationLevel,
                    subgroup2Hours, subgroup2EducationLevel, metaGroup,
                    metaGroup && !isExplicitMetaGroupClassName(className));
        }
    }

    private record ClassHeaderMeta(int colIndex, String building, String className) {}
    private record ClassColumn(String building, String className) {
        String key() { return building + "|" + className; }
    }
    private record SumPair(BigDecimal h1, BigDecimal h2) {}
    private record VisualParseResult(List<EditableImportRow> rows, Map<String, Map<String, SumPair>> expectedSums) {}
    private record MarkerFlags(String value, boolean subgroupRequired, boolean metaGroup) {}
    private record SubgroupHoursParseResult(Integer h1g1, Integer h1g2, Integer h2g1, Integer h2g2, boolean hasH2) {}

    private SubjectType resolveSubjectType(CurriculumImportRow row) {
        return resolveSubjectType(row.getCurriculumPart(), row.getSubjectName());
    }

    private static boolean isExplicitMetaGroupClassName(String className) {
        return String.valueOf(className == null ? "" : className).trim().toUpperCase(Locale.ROOT).startsWith("МГ:");
    }

    private SubjectType resolveSubjectType(CurriculumPart curriculumPart, String subjectName) {
        if (curriculumPart == CurriculumPart.CORE) return SubjectType.CORE;
        if (curriculumPart == CurriculumPart.FORMABLE) return SubjectType.FORMABLE;
        if (curriculumPart == CurriculumPart.EXTRACURRICULAR) return SubjectType.EXTRACURRICULAR;
        if (curriculumPart == CurriculumPart.CORRECTIONAL) return SubjectType.CORRECTIONAL;

        String value = String.valueOf(subjectName == null ? "" : subjectName).trim().toLowerCase(Locale.ROOT);
        if (value.contains("внеур") || value.contains("разговоры о важном")) {
            return SubjectType.EXTRACURRICULAR;
        }
        return SubjectType.CORE;
    }

    private String normalizeSubject(String value) {
        return String.valueOf(value == null ? "" : value).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private MarkerFlags parseMarkerFlags(String raw) {
        String value = normalizeSubject(raw);
        if (value.endsWith("**")) {
            return new MarkerFlags(value.substring(0, value.length() - 2).trim(), false, true);
        }
        if (value.endsWith("*")) {
            return new MarkerFlags(value.substring(0, value.length() - 1).trim(), true, false);
        }
        return new MarkerFlags(value, false, false);
    }

    private String markerSuffixForExport(boolean subgroupRequired, boolean metaGroup) {
        if (metaGroup) return "**";
        if (subgroupRequired) return "*";
        return "";
    }

    private SubgroupHoursParseResult parseSubgroupHours(String raw) {
        String value = normalizeSubject(raw);
        if (value.isBlank()) return null;
        String[] periods = value.split("/", -1);
        String left = periods.length > 0 ? periods[0].trim() : "";
        String right = periods.length > 1 ? periods[1].trim() : "";
        int[] h1 = parseSubgroupPair(left);
        int[] h2 = periods.length > 1 ? parseSubgroupPair(right) : h1;
        return new SubgroupHoursParseResult(h1[0], h1[1], h2[0], h2[1], periods.length > 1);
    }

    private int[] parseSubgroupPair(String raw) {
        String value = normalizeSubject(raw);
        if (value.contains("//")) {
            String[] pair = value.split("//", -1);
            int g1 = parseIntSafe(pair.length > 0 ? pair[0] : "");
            int g2 = parseIntSafe(pair.length > 1 ? pair[1] : "");
            return new int[]{g1, g2};
        }
        int same = parseIntSafe(value);
        return new int[]{same, same};
    }

    private int parseIntSafe(String raw) {
        BigDecimal v = parseDecimal(raw);
        return v == null ? 0 : v.intValue();
    }

    private int compareClassKeysForExport(String left, String right) {
        String[] l = String.valueOf(left).split("\\|", 2);
        String[] r = String.valueOf(right).split("\\|", 2);
        String lb = l.length > 0 ? l[0] : "";
        String rb = r.length > 0 ? r[0] : "";
        int buildingCmp = lb.compareToIgnoreCase(rb);
        if (buildingCmp != 0) return buildingCmp;

        String lc = l.length > 1 ? l[1] : "";
        String rc = r.length > 1 ? r[1] : "";
        Integer lp = extractParallelForExportClass(lc);
        Integer rp = extractParallelForExportClass(rc);
        int pCmp = Integer.compare(lp == null ? Integer.MAX_VALUE : lp, rp == null ? Integer.MAX_VALUE : rp);
        if (pCmp != 0) return pCmp;

        boolean lMeta = lc.startsWith("МГ:");
        boolean rMeta = rc.startsWith("МГ:");
        if (lMeta != rMeta) return lMeta ? 1 : -1; // метагруппа после обычных классов своей параллели

        return lc.compareToIgnoreCase(rc);
    }

    private Integer extractParallelForExportClass(String className) {
        String normalized = normalizeSubject(className);
        if (normalized.startsWith("МГ:")) {
            normalized = normalized.substring(3).trim();
        }
        return ClassNameNormalizer.extractParallel(normalized);
    }

    private String subjectKey(String name, SubjectType type) {
        return normalizeSubject(name).toLowerCase(Locale.ROOT) + "|" + type.name();
    }

    private String keyWithoutBuilding(String c, String s, EducationLevel l, StudyPeriod studyPeriod) {
        return String.join("|", String.valueOf(c).trim(), String.valueOf(s).trim(), String.valueOf(l), String.valueOf(studyPeriod == null ? StudyPeriod.YEAR : studyPeriod));
    }
}
