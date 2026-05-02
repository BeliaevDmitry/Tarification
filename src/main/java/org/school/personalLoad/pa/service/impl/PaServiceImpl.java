package org.school.personalLoad.pa.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaParticipationRepository;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.repository.PaSpecImportLogRepository;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaSpecificationTaskRepository;
import org.school.personalLoad.pa.service.PaService;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;

@Service
@RequiredArgsConstructor
public class PaServiceImpl implements PaService {
    private record SheetImportStats(int specs, int tasks, java.util.Set<String> subjects, java.util.Set<String> parallels) {}

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^(\\d{1,2}).*");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));
    private static final String PA_REPORT_STORAGE_DIR = "pa-reports";
    private static final String PA_SPEC_STORAGE_DIR = "pa-specifications";
    private static final int TEMPLATE_VARIANTS_COUNT = 6;
    private static final short PRESENCE_PRESENT_COLOR = IndexedColors.LIGHT_GREEN.getIndex();
    private static final short PRESENCE_ABSENT_COLOR = IndexedColors.ROSE.getIndex();

    private final PaSpecificationRepository specificationRepository;
    private final PaSpecificationTaskRepository taskRepository;
    private final PaParticipationRepository participationRepository;
    private final PaReportVersionRepository reportVersionRepository;
    private final PaSpecImportLogRepository paSpecImportLogRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    private record TemplateStyles(CellStyle header,
                                  CellStyle subHeader,
                                  CellStyle body,
                                  CellStyle numericBody) {}

    @Override
    @Transactional
    public List<PaDtos.ImportResult> importSpecifications(String academicYear, List<MultipartFile> files, String username) {
        List<PaDtos.ImportResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                PaDtos.ImportResult item = new PaDtos.ImportResult(file == null ? "unknown" : file.getOriginalFilename(), 0, 0, List.of(), List.of(), List.of("Файл пустой"));
                results.add(item);
                saveSpecImportLog(academicYear, username, item);
                continue;
            }
            List<String> warnings = new ArrayList<>();
            int importedSpecs = 0;
            int importedTasks = 0;
            java.util.Set<String> importedSubjects = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            java.util.Set<String> importedParallels = new java.util.TreeSet<>();
            try (InputStream inputStream = file.getInputStream();
                 Workbook workbook = new XSSFWorkbook(inputStream)) {
                Path specDir = Path.of(PA_SPEC_STORAGE_DIR, academicYear.replace("/", "-"));
                Files.createDirectories(specDir);
                if (file.getOriginalFilename() != null) {
                    Files.write(specDir.resolve(file.getOriginalFilename()), file.getBytes());
                }
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    SheetImportStats stats = importSheet(academicYear, file.getOriginalFilename(), sheet, warnings);
                    importedSpecs += stats.specs();
                    importedTasks += stats.tasks();
                    importedSubjects.addAll(stats.subjects());
                    importedParallels.addAll(stats.parallels());
                }
            } catch (Exception e) {
                warnings.add("Ошибка чтения файла: " + e.getMessage());
            }
            PaDtos.ImportResult item = new PaDtos.ImportResult(file.getOriginalFilename(), importedSpecs, importedTasks, new java.util.ArrayList<>(importedSubjects), new java.util.ArrayList<>(importedParallels), warnings);
            results.add(item);
            saveSpecImportLog(academicYear, username, item);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaDtos.ImportLogRow> specificationImportLog(String academicYear, String username, boolean admin) {
        List<org.school.personalLoad.pa.model.PaSpecImportLog> rows = admin
                ? paSpecImportLogRepository.findAllByAcademicYearOrderByCreatedAtDescIdDesc(academicYear)
                : paSpecImportLogRepository.findAllByAcademicYearAndCreatedByOrderByCreatedAtDescIdDesc(academicYear, username);
        return rows.stream().map(r -> new PaDtos.ImportLogRow(
                r.getFileName(), r.getSubjects(), r.getParallels(), r.getStatus(), r.getMessage(),
                r.getRecordsCount() == null ? 0 : r.getRecordsCount(), r.getCreatedBy(), r.getCreatedAt()
        )).toList();
    }

    private void saveSpecImportLog(String academicYear, String username, PaDtos.ImportResult result) {
        var row = new org.school.personalLoad.pa.model.PaSpecImportLog();
        row.setAcademicYear(academicYear);
        row.setFileName(result.fileName() == null ? "—" : result.fileName());
        row.setSubjects((result.subjects() == null || result.subjects().isEmpty()) ? "—" : String.join(", ", result.subjects()));
        row.setParallels((result.parallels() == null || result.parallels().isEmpty()) ? "—" : String.join(", ", result.parallels()));
        int records = result.importedTasks();
        boolean hasError = (result.warnings() != null && result.warnings().stream().anyMatch(w -> String.valueOf(w).toLowerCase(java.util.Locale.ROOT).startsWith("ошибка"))) || records <= 0;
        row.setStatus(hasError ? "Ошибка" : "Успешно");
        String message = (result.warnings() == null || result.warnings().isEmpty()) ? (records > 0 ? "Импорт выполнен" : "Нет загруженных записей") : String.join("; ", result.warnings());
        row.setMessage(message);
        row.setRecordsCount(records);
        row.setCreatedBy(username == null || username.isBlank() ? "unknown" : username);
        row.setCreatedAt(java.time.LocalDateTime.now());
        paSpecImportLogRepository.save(row);
    }

    private SheetImportStats importSheet(String academicYear, String sourceFileName, Sheet sheet, List<String> warnings) {
        List<int[]> subjectCells = findCellsByValue(sheet, "Предмет");
        if (subjectCells.isEmpty()) {
            warnings.add("Лист " + sheet.getSheetName() + ": не найден блок 'Предмет'");
            return new SheetImportStats(0, 0, java.util.Set.of(), java.util.Set.of());
        }
        int importedSpecs = 0;
        int importedTasks = 0;
        java.util.Set<String> subjects = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> parallels = new java.util.TreeSet<>();
        for (int[] cellPos : subjectCells) {
            int subjectRow = cellPos[0];
            int subjectCol = cellPos[1];
            String subjectName = firstNonBlank(sheet, subjectRow, subjectCol + 1, subjectCol + 6);
            if (subjectName.isBlank()) continue;
            int blockEndCol = detectBlockEndCol(sheet, subjectRow, subjectCol);
            PaSpecification spec = parseBlock(academicYear, sourceFileName, sheet, subjectRow, subjectCol, blockEndCol, warnings);
            if (spec == null) continue;
            String thresholdError = validateThresholds(spec);
            if (thresholdError != null) {
                warnings.add("Лист " + sheet.getSheetName() + ": спецификация '" + spec.getSubjectName()
                        + "' (" + spec.getScopeValue() + ") не загружена — нет порогов или они не валидны");
                continue;
            }
            spec.setCreatedAt(LocalDateTime.now());

            List<PaSpecification> sameSpecs = specificationRepository
                    .findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(academicYear)
                    .stream()
                    .filter(s -> normalize(s.getSubjectName()).equals(normalize(spec.getSubjectName())))
                    .filter(s -> s.getScopeType() == spec.getScopeType())
                    .filter(s -> normalizeClass(s.getScopeValue()).equals(normalizeClass(spec.getScopeValue())))
                    .filter(s -> s.getLevel() == spec.getLevel())
                    .filter(s -> s.getWorkType() == spec.getWorkType())
                    .filter(s -> Objects.equals(s.getWorkDate(), spec.getWorkDate()))
                    .toList();
            sameSpecs.forEach(s -> s.setActiveVersion(false));
            if (!sameSpecs.isEmpty()) {
                specificationRepository.saveAll(sameSpecs);
            }

            spec.setVersionNo(specificationRepository.findMaxVersion(
                    spec.getAcademicYear(),
                    spec.getSubjectName(),
                    spec.getScopeType(),
                    spec.getScopeValue(),
                    spec.getLevel(),
                    spec.getWorkType(),
                    spec.getWorkDate()
            ) + 1);
            spec.setActiveVersion(true);
            PaSpecification saved = specificationRepository.save(spec);
            List<PaSpecificationTask> tasks = parseTasks(sheet, subjectRow, subjectCol, blockEndCol, saved, warnings);
            if (tasks.isEmpty()) {
                specificationRepository.delete(saved);
                String workTypeLabel = saved.getWorkType() == PaWorkType.ENTRY
                        ? "Входной"
                        : saved.getWorkType() == PaWorkType.EXIT ? "Выходной" : "Промежуточной";
                warnings.add("Лист " + sheet.getSheetName() + ": спецификация '" + subjectName + "' для " + workTypeLabel + " работы не загружена — нет ни одной темы");
                continue;
            }
            taskRepository.saveAll(tasks);
            importedSpecs += 1;
            importedTasks += tasks.size();
            subjects.add(saved.getSubjectName());
            Integer p = parseParallel(saved.getScopeValue());
            if (p != null) parallels.add(String.valueOf(p));
        }
        return new SheetImportStats(importedSpecs, importedTasks, subjects, parallels);
    }

    private PaSpecification parseBlock(String academicYear,
                                       String sourceFileName,
                                       Sheet sheet,
                                       int baseRow,
                                       int baseCol,
                                       int blockEndCol,
                                       List<String> warnings) {
        String subject = firstNonBlank(sheet, baseRow, baseCol + 1, blockEndCol);
        String scope = findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "Параллель/Класс");
        String workTypeRaw = findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "Тип");
        if (subject.isBlank() || scope.isBlank() || workTypeRaw.isBlank()) {
            warnings.add("Лист " + sheet.getSheetName() + ": пропущены обязательные поля (предмет/параллель/тип)");
            return null;
        }
        PaWorkType workType = parseWorkType(workTypeRaw);
        if (workType == null) {
            warnings.add("Лист " + sheet.getSheetName() + ": неизвестный тип работы '" + workTypeRaw + "'");
            return null;
        }

        String levelRaw = findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "Уровень");
        PaLevel level = parseLevel(levelRaw);

        PaSpecification spec = new PaSpecification();
        spec.setAcademicYear(academicYear);
        spec.setSubjectName(subject.trim());
        spec.setScopeValue(scope.trim().toUpperCase(Locale.ROOT));
        spec.setScopeType(detectScopeType(scope));
        spec.setWorkType(workType);
        spec.setLevel(level);
        spec.setSchoolName(findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "Школа"));
        spec.setTeacherFio(findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "Учитель"));
        spec.setTeacherFioNormalized(normalizeFio(spec.getTeacherFio()));
        spec.setGrade5Percent(resolveThresholdPercent(sheet, baseRow, baseCol, blockEndCol, "5"));
        spec.setGrade4Percent(resolveThresholdPercent(sheet, baseRow, baseCol, blockEndCol, "4"));
        spec.setGrade3Percent(resolveThresholdPercent(sheet, baseRow, baseCol, blockEndCol, "3"));
        spec.setSourceFileName(sourceFileName);
        spec.setPairKey(buildPairKey(academicYear, subject, scope, level, workType, sheet.getSheetName()));
        spec.setActiveVersion(true);
        return spec;
    }

    private List<PaSpecificationTask> parseTasks(Sheet sheet,
                                                 int baseRow,
                                                 int baseCol,
                                                 int blockEndCol,
                                                 PaSpecification specification,
                                                 List<String> warnings) {
        int headerRow = -1;
        int headerCol = -1;
        int maxRow = Math.min(sheet.getLastRowNum(), baseRow + 200);
        for (int r = baseRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int taskColumn = findColumnWithLabel(row, "№ задания", baseCol, blockEndCol);
            if (taskColumn >= 0) {
                headerRow = r;
                headerCol = taskColumn;
                break;
            }
        }
        if (headerRow < 0) return List.of();

        Row header = sheet.getRow(headerRow);
        Map<String, Integer> colMap = new HashMap<>();
        for (int c = headerCol; c <= blockEndCol; c++) {
            String value = getCell(header, c);
            if (containsNormalized(value, "№ задания")) colMap.put("task", c);
            if (containsNormalized(value, "тема")) colMap.put("topic", c);
            if (containsNormalized(value, "навык")) colMap.put("skill", c);
            if (containsNormalized(value, "тип задания")) colMap.put("kind", c);
            if (containsNormalized(value, "если повторение")) colMap.put("repeat", c);
            if (containsNormalized(value, "балл")) colMap.put("score", c);
        }
        if (!colMap.containsKey("task")) return List.of();
        if (!colMap.containsKey("score")) {
            warnings.add("Лист " + sheet.getSheetName() + ": не найдена колонка «Балл» для предмета '" + specification.getSubjectName() + "'");
            return List.of();
        }

        List<PaSpecificationTask> tasks = new ArrayList<>();
        int emptyStreak = 0;
        for (int r = headerRow + 1; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                emptyStreak++;
                if (emptyStreak >= 3) break;
                continue;
            }
            String taskNoRaw = getCell(row, colMap.get("task"));
            String topic = getCell(row, colMap.get("topic"));
            String skill = getCell(row, colMap.get("skill"));
            String maxScoreRaw = getCell(row, colMap.get("score"));
            if (taskNoRaw.isBlank() && topic.isBlank() && skill.isBlank() && maxScoreRaw.isBlank()) {
                emptyStreak++;
                if (emptyStreak >= 3) break;
                continue;
            }
            emptyStreak = 0;
            Integer taskNo = parseInt(taskNoRaw);
            if (taskNo == null) continue;
            boolean isTaskEmpty = topic.isBlank()
                    && skill.isBlank()
                    && maxScoreRaw.isBlank();
            if (isTaskEmpty) continue;

            PaSpecificationTask task = new PaSpecificationTask();
            task.setSpecification(specification);
            task.setTaskNo(taskNo);
            task.setTopic(topic);
            task.setSkill(skill);
            task.setTaskKind(parseTaskKind(getCell(row, colMap.get("kind"))));
            task.setRepeatFromTaskNo(parseRepeatFromTaskNo(getCell(row, colMap.get("repeat"))));
            task.setMaxScore(parseInt(maxScoreRaw));
            tasks.add(task);
        }
        boolean hasAtLeastOneTopic = tasks.stream().anyMatch(task -> task.getTopic() != null && !task.getTopic().isBlank());
        if (!hasAtLeastOneTopic) {
            return List.of();
        }
        return tasks;
    }

    private int findColumnWithLabel(Row row, String label, int fromCol, int toCol) {
        if (row == null) return -1;
        for (int c = Math.max(0, fromCol); c <= Math.max(fromCol, toCol); c++) {
            String value = getCell(row, c);
            if (!value.isBlank() && containsNormalized(value, label)) {
                return c;
            }
        }
        return -1;
    }

    @Override
    public List<PaDtos.SpecificationRow> specifications(String academicYear) {
        return specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(academicYear).stream()
                .map(s -> new PaDtos.SpecificationRow(
                        s.getId(),
                        s.getAcademicYear(),
                        s.getSubjectName(),
                        s.getScopeType(),
                        s.getScopeValue(),
                        s.getLevel(),
                        s.getWorkType(),
                        s.getWorkDate(),
                        s.getGrade5Percent(),
                        s.getGrade4Percent(),
                        s.getGrade3Percent(),
                        s.getTeacherFio(),
                        s.getSourceFileName(),
                        s.getVersionNo(),
                        s.isActiveVersion(),
                        s.getPairKey()
                ))
                .toList();
    }

    @Override
    public List<PaDtos.SpecificationTaskRow> specificationTasks(Long specificationId) {
        return taskRepository.findAllBySpecificationIdOrderByTaskNoAsc(specificationId).stream()
                .map(t -> new PaDtos.SpecificationTaskRow(t.getTaskNo(), t.getTopic(), t.getSkill(), t.getTaskKind(), t.getRepeatFromTaskNo(), t.getMaxScore()))
                .toList();
    }

    @Override
    public PaDtos.SummaryResponse summary(String academicYear) {
        Set<String> primarySubjects = new TreeSet<>();
        Set<String> secondarySubjects = new TreeSet<>();
        for (CurriculumPlanEntry entry : curriculumPlanEntryRepository.findAllByAcademicYear(academicYear)) {
            if (entry.isDeprecated()) continue;
            Integer parallel = parseParallel(entry.getClassName());
            if (parallel == null) continue;
            if (parallel <= 4) primarySubjects.add(entry.getSubjectName());
            else secondarySubjects.add(entry.getSubjectName());
        }
        List<PaSpecification> specs = specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(academicYear);
        Map<String, PaParticipation> participationMap = participationRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.toMap(this::participationKey, p -> p, (a, b) -> b));
        List<PaDtos.SummaryCell> primary = buildSummaryCells(primarySubjects, specs, participationMap, 1, 4);
        List<PaDtos.SummaryCell> secondary = buildSummaryCells(secondarySubjects, specs, participationMap, 5, 11);
        return new PaDtos.SummaryResponse(primary, secondary);
    }

    @Override
    public List<PaDtos.ReportVersionRow> reportVersions(String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level, PaWorkType workType, LocalDate workDate) {
        return reportVersionRepository.findTop10ByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDateOrderByCreatedAtDesc(
                        academicYear, subjectName, scopeType, scopeValue, level, workType, workDate)
                .stream()
                .map(r -> new PaDtos.ReportVersionRow(
                        r.getId(), r.getAcademicYear(), r.getSubjectName(), r.getScopeType(), r.getScopeValue(), r.getLevel(),
                        r.getWorkType(), r.getWorkDate(), r.getVersionNo(), r.isActiveVersion(), r.getStatus(),
                        r.getValidationMessage(), r.getSourceFileName(), r.getCreatedAt(), r.isDownloadedAtLeastOnce(), r.isUploadedBackSuccess()
                ))
                .toList();
    }

    @Override
    public List<PaDtos.ReportWorkflowSummaryItem> reportWorkflowSummary(String academicYear, PaLevel level, PaWorkType workType, String subjectName) {
        Map<String, List<PaReportVersion>> grouped = reportVersionRepository
                .findAllByAcademicYearAndLevelAndWorkType(academicYear, level, workType)
                .stream()
                .filter(v -> v.getScopeType() == PaScopeType.CLASS)
                .filter(v -> subjectName == null || subjectName.isBlank() || "ALL".equalsIgnoreCase(subjectName) || normalize(v.getSubjectName()).equals(normalize(subjectName)))
                .collect(Collectors.groupingBy(v -> normalize(v.getSubjectName()) + "|" + normalizeClass(v.getScopeValue())));

        List<PaDtos.ReportWorkflowSummaryItem> result = new ArrayList<>();
        grouped.forEach((key, versions) -> {
            PaReportVersion latestGenerated = versions.stream()
                    .filter(v -> "GENERATED".equalsIgnoreCase(v.getStatus()))
                    .sorted(Comparator.comparing(PaReportVersion::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .findFirst()
                    .orElse(null);
            PaReportVersion latestUploaded = versions.stream()
                    .filter(v -> "ACCEPTED".equalsIgnoreCase(v.getStatus()) && v.isUploadedBackSuccess())
                    .sorted(Comparator.comparing(PaReportVersion::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .findFirst()
                    .orElse(null);
            PaReportVersion sample = versions.get(0);
            result.add(new PaDtos.ReportWorkflowSummaryItem(
                    sample.getSubjectName(),
                    sample.getScopeValue(),
                    versions.stream().anyMatch(v -> "GENERATED".equalsIgnoreCase(v.getStatus())),
                    versions.stream().anyMatch(PaReportVersion::isDownloadedAtLeastOnce),
                    versions.stream().anyMatch(v -> "ACCEPTED".equalsIgnoreCase(v.getStatus()) && v.isUploadedBackSuccess()),
                    latestGenerated == null ? null : latestGenerated.getId(),
                    latestUploaded == null ? null : latestUploaded.getId()
            ));
        });
        return result;
    }

    @Override
    @Transactional
    public List<PaDtos.ReportUploadResult> uploadReports(String academicYear, List<MultipartFile> files) {
        List<PaDtos.ReportUploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                results.add(new PaDtos.ReportUploadResult(file == null ? "unknown" : file.getOriginalFilename(), "REJECTED", "Файл пустой", null, null, null, null));
                continue;
            }
            try (InputStream input = file.getInputStream(); Workbook wb = new XSSFWorkbook(input)) {
                Map<String, String> info = readInfoSheet(wb.getSheet("Информация"));
                String teacher = info.getOrDefault("учитель", "");
                String subject = info.getOrDefault("предмет", "");
                String scopeValue = info.getOrDefault("класс", "");
                String typeRaw = info.getOrDefault("тип", "");
                String yearInFile = info.getOrDefault("учебный год", "");
                String dateRaw = info.getOrDefault("дата написания работы", "");

                if (teacher.isBlank() || subject.isBlank() || scopeValue.isBlank() || typeRaw.isBlank()) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, "Не заполнены обязательные поля листа «Информация»"));
                    continue;
                }
                if (!yearInFile.isBlank() && !normalize(yearInFile).equals(normalize(academicYear))) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, "Учебный год в файле не совпадает с текущим"));
                    continue;
                }
                PaWorkType workType = parseWorkType(typeRaw);
                if (workType == null) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, "Недопустимый тип работы"));
                    continue;
                }
                LocalDate workDate = parseLocalDate(dateRaw);
                String normalizedTeacher = normalizeFio(teacher);
                var teacherEntry = teacherDirectoryRepository.findAll().stream()
                        .filter(t -> normalizeFio(t.getFioTeacher()).equals(normalizedTeacher))
                        .findFirst()
                        .orElse(null);
                if (teacherEntry == null) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, "Педагог не найден в кадрах"));
                    continue;
                }
                if (teacherEntry.getDismissalDate() != null && !teacherEntry.getDismissalDate().isAfter(LocalDate.now())) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, "Педагог уволен, отчёт не принят"));
                    continue;
                }
                String structureProblem = validateDataSheetStructure(wb.getSheet("Сбор информации"));
                if (structureProblem != null) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, structureProblem));
                    continue;
                }
                String studentsProblem = validateStudentsAgainstContingent(academicYear, scopeValue, wb.getSheet("Сбор информации"));
                if (studentsProblem != null) {
                    results.add(saveRejectedReport(academicYear, file.getOriginalFilename(), subject, scopeValue, typeRaw, studentsProblem));
                    continue;
                }

                PaScopeType scopeType = detectScopeType(scopeValue);
                PaLevel level = PaLevel.BASIC;
                List<PaReportVersion> sameKey = reportVersionRepository.findAllByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDate(
                        academicYear, subject, scopeType, scopeValue.trim().toUpperCase(Locale.ROOT), level, workType, workDate
                );
                sameKey.forEach(v -> v.setActiveVersion(false));
                if (!sameKey.isEmpty()) {
                    reportVersionRepository.saveAll(sameKey);
                }
                int nextVersion = reportVersionRepository.findMaxVersion(academicYear, subject, scopeType, scopeValue.trim().toUpperCase(Locale.ROOT), level, workType, workDate) + 1;
                PaReportVersion version = new PaReportVersion();
                version.setAcademicYear(academicYear);
                version.setSubjectName(subject.trim());
                version.setScopeType(scopeType);
                version.setScopeValue(scopeValue.trim().toUpperCase(Locale.ROOT));
                version.setLevel(level);
                version.setWorkType(workType);
                version.setWorkDate(workDate);
                version.setVersionNo(nextVersion);
                version.setActiveVersion(true);
                version.setStatus("ACCEPTED");
                version.setValidationMessage("Отчёт принят");
                version.setSourceFileName(file.getOriginalFilename());
                Path directory = Path.of(PA_REPORT_STORAGE_DIR, academicYear.replace("/", "-"), "uploaded");
                Files.createDirectories(directory);
                Path stored = directory.resolve(LocalDateTime.now().toString().replace(":", "-") + "_" + sanitizeFileName(file.getOriginalFilename()));
                Files.write(stored, file.getBytes());
                version.setSourceFilePath(stored.toString());
                version.setTeacherFio(teacher.trim());
                version.setTeacherFioNormalized(normalizedTeacher);
                version.setUploadedBackSuccess(true);
                version.setCreatedAt(LocalDateTime.now());
                reportVersionRepository.save(version);
                results.add(new PaDtos.ReportUploadResult(file.getOriginalFilename(), "ACCEPTED", "Отчёт принят", nextVersion, subject.trim(), scopeValue.trim(), workType));
            } catch (Exception e) {
                results.add(new PaDtos.ReportUploadResult(file.getOriginalFilename(), "REJECTED", "Ошибка чтения файла: " + e.getMessage(), null, null, null, null));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public void setParticipation(String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level, boolean participates) {
        PaParticipation entity = participationRepository.findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevel(
                        academicYear, subjectName, scopeType, scopeValue, level)
                .orElseGet(PaParticipation::new);
        entity.setAcademicYear(academicYear);
        entity.setSubjectName(subjectName);
        entity.setScopeType(scopeType);
        entity.setScopeValue(scopeValue);
        entity.setLevel(level);
        entity.setParticipates(participates);
        entity.setUpdatedAt(LocalDateTime.now());
        participationRepository.save(entity);
    }

    @Override
    @Transactional
    public PaDtos.ReportUploadResult generateReportTemplate(String academicYear, String subjectName, String className, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force) {
        PaSpecification spec = resolveSpecificationForClass(academicYear, subjectName, className, level, workType, workDate);
        if (spec == null) {
            return new PaDtos.ReportUploadResult("", "REJECTED", "Не найдена активная спецификация для генерации", null, subjectName, className, workType);
        }
        var snapshot = contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null);
        if (snapshot == null) {
            return new PaDtos.ReportUploadResult("", "REJECTED", "Нет актуального контингента для выбранного учебного года", null, subjectName, className, workType);
        }
        List<String> students = contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(s -> normalizeClass(s.getClassName()).equals(normalizeClass(className)))
                .map(s -> s.getFullName().trim())
                .filter(v -> !v.isBlank())
                .sorted(String::compareToIgnoreCase)
                .toList();
        if (students.isEmpty()) {
            return new PaDtos.ReportUploadResult("", "REJECTED", "Для класса нет учеников в контингенте", null, subjectName, className, workType);
        }
        List<PaSpecificationTask> tasks = taskRepository.findAllBySpecificationIdOrderByTaskNoAsc(spec.getId());
        if (tasks.isEmpty()) {
            return new PaDtos.ReportUploadResult("", "REJECTED", "В спецификации нет заданий для генерации шаблона", null, subjectName, className, workType);
        }
        String thresholdsError = validateThresholds(spec);
        if (thresholdsError != null) {
            return new PaDtos.ReportUploadResult("", "REJECTED", thresholdsError, null, subjectName, className, workType);
        }

        String teacherFio = resolveSingleTeacherFio(academicYear, subjectName, className);
        String safeTeacher = sanitizeFileName((teacherFio == null || teacherFio.isBlank()) ? "без_педагога" : teacherFio);
        String fileName = String.format("Отчет_%s_%s_%s_%s.xlsx",
                sanitizeFileName(subjectName),
                sanitizeFileName(className),
                safeTeacher,
                LocalDateTime.now().toString().replace(":", "-"));
        Path directory = Path.of(PA_REPORT_STORAGE_DIR, academicYear.replace("/", "-"));
        Path filePath = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);
            try (Workbook workbook = new XSSFWorkbook();
                 OutputStream outputStream = Files.newOutputStream(filePath)) {
                TemplateStyles styles = createTemplateStyles(workbook);
                createInfoSheet(workbook, academicYear, subjectName, className, teacherFio, level, workType, workDate, styles);
                createDataSheet(workbook, students, tasks, spec, styles);
                workbook.write(outputStream);
            }
        } catch (Exception e) {
            return new PaDtos.ReportUploadResult(fileName, "REJECTED", "Ошибка генерации файла: " + e.getMessage(), null, subjectName, className, workType);
        }

        List<PaReportVersion> sameKey = reportVersionRepository.findAllByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDate(
                academicYear, subjectName, PaScopeType.CLASS, className.toUpperCase(Locale.ROOT), level, workType, workDate
        );
        sameKey.forEach(v -> v.setActiveVersion(false));
        if (!sameKey.isEmpty()) reportVersionRepository.saveAll(sameKey);
        int versionNo = reportVersionRepository.findMaxVersion(academicYear, subjectName, PaScopeType.CLASS, className.toUpperCase(Locale.ROOT), level, workType, workDate) + 1;
        PaReportVersion version = new PaReportVersion();
        version.setAcademicYear(academicYear);
        version.setSubjectName(subjectName);
        version.setScopeType(PaScopeType.CLASS);
        version.setScopeValue(className.toUpperCase(Locale.ROOT));
        version.setLevel(level);
        version.setWorkType(workType);
        version.setWorkDate(workDate);
        version.setVersionNo(versionNo);
        version.setActiveVersion(true);
        version.setStatus("GENERATED");
        version.setValidationMessage("Шаблон отчёта сгенерирован");
        version.setSourceFileName(fileName);
        version.setSourceFilePath(filePath.toString());
        version.setCreatedAt(LocalDateTime.now());
        reportVersionRepository.save(version);
        return new PaDtos.ReportUploadResult(fileName, "ACCEPTED", "Шаблон отчёта сгенерирован", versionNo, subjectName, className, workType);
    }

    @Override
    @Transactional
    public List<PaDtos.ReportUploadResult> generateReportTemplatesByParallel(String academicYear, String subjectName, String parallel, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force) {
        List<String> classes = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .map(CurriculumPlanEntry::getClassName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .filter(v -> {
                    Integer p = parseParallel(v);
                    return p != null && String.valueOf(p).equals(parallel);
                })
                .distinct()
                .sorted()
                .toList();
        List<PaDtos.ReportUploadResult> results = new ArrayList<>();
        for (String className : classes) {
            if (resolveSpecificationForClass(academicYear, subjectName, className, level, workType, workDate) == null) {
                continue;
            }
            if (!force && hasActiveGeneratedTemplate(academicYear, subjectName, className, level, workType, workDate)) {
                results.add(new PaDtos.ReportUploadResult("", "SKIPPED", "Шаблон уже сгенерирован для класса", null, subjectName, className, workType));
                continue;
            }
            results.add(generateReportTemplate(academicYear, subjectName, className, level, workType, workDate, force));
        }
        if (results.isEmpty()) {
            results.add(new PaDtos.ReportUploadResult("", "SKIPPED", "Нет классов с доступной спецификацией для генерации", null, subjectName, "", workType));
        }
        return results;
    }

    @Override
    @Transactional
    public List<PaDtos.ReportUploadResult> generateAllReportTemplates(String academicYear, String subjectName, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force) {
        List<String> classes = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .map(CurriculumPlanEntry::getClassName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted()
                .toList();
        List<String> subjects = "ALL".equalsIgnoreCase(subjectName)
                ? specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(academicYear).stream()
                .filter(PaSpecification::isActiveVersion)
                .filter(s -> s.getLevel() == level && s.getWorkType() == workType)
                .map(PaSpecification::getSubjectName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
                : List.of(subjectName);
        List<PaDtos.ReportUploadResult> results = new ArrayList<>();
        for (String subject : subjects) {
            for (String className : classes) {
                if (resolveSpecificationForClass(academicYear, subject, className, level, workType, workDate) == null) {
                    continue;
                }
                boolean generatedExists = hasActiveGeneratedTemplate(academicYear, subject, className, level, workType, workDate);
                if (!force && generatedExists) {
                    continue;
                }
                if (force && "ALL".equalsIgnoreCase(subjectName) && !generatedExists) {
                    continue;
                }
                results.add(generateReportTemplate(academicYear, subject, className, level, workType, workDate, true));
            }
        }
        if (results.isEmpty()) {
            results.add(new PaDtos.ReportUploadResult("", "SKIPPED", "Нет классов с доступной спецификацией для генерации", null, subjectName, "", workType));
        }
        return results;
    }

    @Override
    @Transactional
    public int deleteGeneratedReports(String academicYear, String subjectName, String scopeValue, boolean byParallel, PaLevel level, PaWorkType workType, LocalDate workDate) {
        String normalizedScope = normalizeClass(scopeValue);
        List<PaReportVersion> candidates = reportVersionRepository.findAllByAcademicYearAndLevelAndWorkType(academicYear, level, workType).stream()
                .filter(v -> "GENERATED".equalsIgnoreCase(v.getStatus()))
                .filter(v -> byParallel ? parseParallel(v.getScopeValue()) != null && String.valueOf(parseParallel(v.getScopeValue())).equals(normalizedScope)
                        : normalizeClass(v.getScopeValue()).equals(normalizedScope))
                .filter(v -> "ALL".equalsIgnoreCase(subjectName) || normalize(v.getSubjectName()).equals(normalize(subjectName)))
                .filter(v -> Objects.equals(v.getWorkDate(), workDate) || workDate == null)
                .toList();
        if (candidates.isEmpty()) return 0;
        for (PaReportVersion version : candidates) {
            try {
                Path path = resolveReportFilePath(version);
                Files.deleteIfExists(path);
            } catch (Exception ignored) { }
        }
        reportVersionRepository.deleteAll(candidates);
        return candidates.size();
    }

    private boolean hasActiveGeneratedTemplate(String academicYear,
                                               String subjectName,
                                               String className,
                                               PaLevel level,
                                               PaWorkType workType,
                                               LocalDate workDate) {
        return reportVersionRepository.findAllByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDate(
                        academicYear,
                        subjectName,
                        PaScopeType.CLASS,
                        className.toUpperCase(Locale.ROOT),
                        level,
                        workType,
                        workDate)
                .stream()
                .anyMatch(v -> v.isActiveVersion() && "GENERATED".equalsIgnoreCase(v.getStatus()));
    }

    @Override
    public List<PaDtos.ReportFolderItem> reportFolderItems(String academicYear, PaWorkType workType) {
        return reportVersionRepository.findAll().stream()
                .filter(v -> Objects.equals(v.getAcademicYear(), academicYear))
                .filter(v -> v.getWorkType() == workType)
                .filter(v -> "GENERATED".equalsIgnoreCase(v.getStatus()))
                .filter(PaReportVersion::isActiveVersion)
                .filter(v -> v.getScopeType() == PaScopeType.CLASS)
                .map(v -> new PaDtos.ReportFolderItem(
                        v.getId(),
                        v.getSubjectName(),
                        Optional.ofNullable(parseParallel(v.getScopeValue())).map(String::valueOf).orElse("—"),
                        v.getScopeValue(),
                        v.getLevel(),
                        v.getSourceFileName(),
                        v.getCreatedAt()
                ))
                .sorted(Comparator
                        .comparing(PaDtos.ReportFolderItem::subjectName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PaDtos.ReportFolderItem::parallel, Comparator.nullsLast(String::compareTo))
                        .thenComparing(PaDtos.ReportFolderItem::className, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public byte[] loadReportFile(Long reportVersionId) throws IOException {
        PaReportVersion version = reportVersionRepository.findById(reportVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Версия отчёта не найдена"));
        Path path = resolveReportFilePath(version);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Файл версии не найден на диске");
        }
        if (version.getSourceFilePath() == null || version.getSourceFilePath().isBlank()) {
            version.setSourceFilePath(path.toString());
        }
        version.setDownloadedAtLeastOnce(true);
        reportVersionRepository.save(version);
        return Files.readAllBytes(path);
    }

    private Path resolveReportFilePath(PaReportVersion version) {
        if (version.getSourceFilePath() != null && !version.getSourceFilePath().isBlank()) {
            return Path.of(version.getSourceFilePath());
        }
        if (version.getAcademicYear() != null && version.getSourceFileName() != null && !version.getSourceFileName().isBlank()) {
            return Path.of(PA_REPORT_STORAGE_DIR, version.getAcademicYear().replace("/", "-"), version.getSourceFileName());
        }
        throw new IllegalArgumentException("Для версии не сохранён путь к файлу");
    }

    @Override
    public byte[] loadSpecificationFile(String academicYear, Long specificationId) throws IOException {
        PaSpecification specification = specificationRepository.findById(specificationId)
                .orElseThrow(() -> new IllegalArgumentException("Спецификация не найдена"));
        if (specification.getSourceFileName() == null || specification.getSourceFileName().isBlank()) {
            throw new IllegalArgumentException("У спецификации не указан исходный файл");
        }
        Path path = Path.of(PA_SPEC_STORAGE_DIR, academicYear.replace("/", "-"), specification.getSourceFileName());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Файл спецификации не найден на диске");
        }
        return Files.readAllBytes(path);
    }

    private PaSpecification resolveSpecificationForClass(String year, String subject, String className, PaLevel level, PaWorkType workType, LocalDate workDate) {
        String classScope = className.toUpperCase(Locale.ROOT);
        Integer parallel = parseParallel(className);
        String parallelScope = parallel == null ? null : String.valueOf(parallel);
        return specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(year).stream()
                .filter(PaSpecification::isActiveVersion)
                .filter(s -> normalize(s.getSubjectName()).equals(normalize(subject)))
                .filter(s -> s.getLevel() == level)
                .filter(s -> s.getWorkType() == workType)
                .filter(s -> Objects.equals(s.getWorkDate(), workDate) || s.getWorkDate() == null || workDate == null)
                .sorted((a, b) -> {
                    boolean aClass = a.getScopeType() == PaScopeType.CLASS && normalizeClass(a.getScopeValue()).equals(normalizeClass(classScope));
                    boolean bClass = b.getScopeType() == PaScopeType.CLASS && normalizeClass(b.getScopeValue()).equals(normalizeClass(classScope));
                    if (aClass == bClass) return 0;
                    return aClass ? -1 : 1;
                })
                .filter(s -> (s.getScopeType() == PaScopeType.CLASS && normalizeClass(s.getScopeValue()).equals(normalizeClass(classScope)))
                        || (parallelScope != null && s.getScopeType() == PaScopeType.PARALLEL && normalizeClass(s.getScopeValue()).equals(normalizeClass(parallelScope))))
                .findFirst()
                .orElse(null);
    }

    private void createInfoSheet(Workbook workbook,
                                 String year,
                                 String subject,
                                 String className,
                                 String teacherFio,
                                 PaLevel level,
                                 PaWorkType workType,
                                 LocalDate workDate,
                                 TemplateStyles styles) {
        Sheet info = workbook.createSheet("Информация");
        String[][] rows = {
                {"Учитель", teacherFio == null ? "" : teacherFio},
                {"Дата написания работы", workDate == null ? "" : workDate.toString()},
                {"Предмет", subject},
                {"Класс", className},
                {"Тип", workType == PaWorkType.ENTRY ? "Входная работа" : workType == PaWorkType.EXIT ? "Выходная работа" : "Промежуточная работа"},
                {"Уровень", level == PaLevel.ADVANCED ? "Углублённый" : "Базовый"},
                {"Школа", "ГБОУ №7"},
                {"Учебный год", year}
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = info.createRow(i);
            createStyledCell(row, 0, rows[i][0], styles.subHeader());
            createStyledCell(row, 1, rows[i][1], styles.body());
        }
        info.setColumnWidth(0, 5500);
        info.setColumnWidth(1, 9000);
    }

    private String resolveSingleTeacherFio(String academicYear, String subjectName, String className) {
        Set<String> teachers = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> normalize(row.getSubjectName()).equals(normalize(subjectName)))
                .filter(row -> normalizeClass(row.getClassName()).equals(normalizeClass(className)))
                .map(row -> String.valueOf(row.getFioTeacher()).trim())
                .filter(v -> !v.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return teachers.size() == 1 ? teachers.iterator().next() : "";
    }

    private void createDataSheet(Workbook workbook,
                                 List<String> students,
                                 List<PaSpecificationTask> tasks,
                                 PaSpecification specification,
                                 TemplateStyles styles) {
        Sheet data = workbook.createSheet("Сбор информации");
        List<Integer> taskMaxScores = resolveTaskMaxScores(tasks);
        int firstTaskCol = 4;
        int firstStudentRow = 3;
        int studentCount = students.size();
        int templateRowCount = studentCount;

        Row headerRow = data.createRow(0);
        createStyledCell(headerRow, 0, "№", styles.header());
        createStyledCell(headerRow, 1, "ФИО ученика", styles.header());
        createStyledCell(headerRow, 2, "Присутствие", styles.header());
        createStyledCell(headerRow, 3, "Вариант", styles.header());
        createStyledCell(headerRow, firstTaskCol, "Баллы за задания", styles.header());
        data.addMergedRegion(new CellRangeAddress(0, 0, firstTaskCol, firstTaskCol + tasks.size() - 1));
        applyMergedRegionStyle(data, new CellRangeAddress(0, 0, firstTaskCol, firstTaskCol + tasks.size() - 1), styles.header());
        int totalCol = firstTaskCol + tasks.size();
        int gradeCol = totalCol + 1;
        createStyledCell(headerRow, totalCol, "Итог", styles.header());
        createStyledCell(headerRow, gradeCol, "Отметка", styles.header());

        Row taskNoRow = data.createRow(1);
        for (int i = 0; i < 4; i++) createStyledCell(taskNoRow, i, "", styles.subHeader());
        for (int i = 0; i < tasks.size(); i++) {
            createStyledCell(taskNoRow, firstTaskCol + i, String.valueOf(tasks.get(i).getTaskNo() == null ? i + 1 : tasks.get(i).getTaskNo()), styles.subHeader());
        }
        createStyledCell(taskNoRow, totalCol, "", styles.subHeader());
        createStyledCell(taskNoRow, gradeCol, "", styles.subHeader());

        Row maxScoresRow = data.createRow(2);
        for (int i = 0; i < 4; i++) createStyledCell(maxScoresRow, i, "", styles.subHeader());
        for (int i = 0; i < taskMaxScores.size(); i++) {
            Cell cell = maxScoresRow.createCell(firstTaskCol + i);
            cell.setCellValue(taskMaxScores.get(i));
            cell.setCellStyle(styles.numericBody());
        }
        createStyledCell(maxScoresRow, totalCol, "", styles.subHeader());
        createStyledCell(maxScoresRow, gradeCol, "", styles.subHeader());

        for (int i = 0; i < studentCount; i++) {
            Row row = data.createRow(firstStudentRow + i);
            Cell numberCell = row.createCell(0);
            numberCell.setCellValue(i + 1);
            numberCell.setCellStyle(styles.numericBody());
            createStyledCell(row, 1, students.get(i), styles.body());
            createStyledCell(row, 2, "", styles.body());
            createStyledCell(row, 3, "", styles.body());
            for (int t = 0; t < tasks.size(); t++) {
                createStyledCell(row, firstTaskCol + t, "", styles.numericBody());
            }
            Cell totalCell = row.createCell(totalCol);
            totalCell.setCellFormula(createTotalFormula(firstTaskCol, firstStudentRow + i + 1, tasks.size()));
            totalCell.setCellStyle(styles.numericBody());
            Cell gradeCell = row.createCell(gradeCol);
            gradeCell.setCellFormula(createGradeFormula(firstTaskCol, tasks.size(), firstStudentRow + i + 1, specification, totalCol));
            gradeCell.setCellStyle(styles.numericBody());
        }

        setupTemplateValidation(data, firstStudentRow, studentCount, taskMaxScores, firstTaskCol);
        setupPresenceConditionalFormatting(data, firstStudentRow, studentCount, 2);

        data.setColumnWidth(0, 1200);
        data.setColumnWidth(1, 9000);
        data.setColumnWidth(2, 3200);
        data.setColumnWidth(3, 2600);
        for (int i = 0; i < tasks.size(); i++) data.setColumnWidth(firstTaskCol + i, 1500);
        data.setColumnWidth(totalCol, 2200);
        data.setColumnWidth(gradeCol, 2200);
        data.createFreezePane(0, 3);

        if (templateRowCount > 0) {
            data.setAutoFilter(new CellRangeAddress(0, firstStudentRow + templateRowCount - 1, 0, gradeCol));
        }
    }

    private String createGradeFormula(int firstTaskCol, int tasksSize, int excelRow, PaSpecification specification, int totalCol) {
        String totalCell = new CellReference(excelRow - 1, totalCol).formatAsString();
        String presenceCell = new CellReference(excelRow - 1, 2).formatAsString();
        String variantCell = new CellReference(excelRow - 1, 3).formatAsString();
        String maxRangeStart = new CellReference(2, firstTaskCol).formatAsString();
        String maxRangeEnd = new CellReference(2, firstTaskCol + tasksSize - 1).formatAsString();
        String maxSum = "SUM(" + maxRangeStart + ":" + maxRangeEnd + ")";
        return String.format(Locale.ROOT,
                "IF(OR(%s=\"\",%s=\"Не был\",AND(%s=\"\",%s=0)),\"\",IF(%s/%s*100>=%d,5,IF(%s/%s*100>=%d,4,IF(%s/%s*100>=%d,3,2))))",
                totalCell, presenceCell, variantCell, totalCell,
                totalCell, maxSum, specification.getGrade5Percent(),
                totalCell, maxSum, specification.getGrade4Percent(),
                totalCell, maxSum, specification.getGrade3Percent());
    }

    private TemplateStyles createTemplateStyles(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10);

        Font bodyFont = workbook.createFont();
        bodyFont.setFontHeightInPoints((short) 10);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setWrapText(true);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);

        CellStyle subHeader = workbook.createCellStyle();
        subHeader.cloneStyleFrom(header);
        subHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());

        CellStyle body = workbook.createCellStyle();
        body.setFont(bodyFont);
        body.setAlignment(HorizontalAlignment.LEFT);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setBorderTop(BorderStyle.THIN);
        body.setBorderBottom(BorderStyle.THIN);
        body.setBorderLeft(BorderStyle.THIN);
        body.setBorderRight(BorderStyle.THIN);

        CellStyle numericBody = workbook.createCellStyle();
        numericBody.cloneStyleFrom(body);
        numericBody.setAlignment(HorizontalAlignment.CENTER);

        return new TemplateStyles(header, subHeader, body, numericBody);
    }

    private Cell createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
        return cell;
    }

    private void applyMergedRegionStyle(Sheet sheet, CellRangeAddress mergedRegion, CellStyle style) {
        for (int rowNum = mergedRegion.getFirstRow(); rowNum <= mergedRegion.getLastRow(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) row = sheet.createRow(rowNum);
            for (int colNum = mergedRegion.getFirstColumn(); colNum <= mergedRegion.getLastColumn(); colNum++) {
                Cell cell = row.getCell(colNum);
                if (cell == null) cell = row.createCell(colNum);
                cell.setCellStyle(style);
            }
        }
    }

    private List<Integer> resolveTaskMaxScores(List<PaSpecificationTask> tasks) {
        return tasks.stream()
                .map(PaSpecificationTask::getMaxScore)
                .map(score -> score == null ? 0 : Math.max(0, score))
                .toList();
    }

    private void setupTemplateValidation(Sheet sheet, int firstStudentRow, int studentCount, List<Integer> taskMaxScores, int firstTaskCol) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        if (studentCount <= 0) return;
        DataValidationConstraint presenceConstraint = helper.createExplicitListConstraint(new String[]{"Был", "Не был"});
        CellRangeAddressList presenceRange = new CellRangeAddressList(firstStudentRow, firstStudentRow + studentCount - 1, 2, 2);
        DataValidation presenceValidation = helper.createValidation(presenceConstraint, presenceRange);
        presenceValidation.setShowErrorBox(true);
        presenceValidation.setEmptyCellAllowed(true);
        sheet.addValidationData(presenceValidation);

        List<String> variants = new ArrayList<>();
        for (int i = 1; i <= TEMPLATE_VARIANTS_COUNT; i++) variants.add("Вариант " + i);
        DataValidationConstraint variantConstraint = helper.createExplicitListConstraint(variants.toArray(new String[0]));
        CellRangeAddressList variantRange = new CellRangeAddressList(firstStudentRow, firstStudentRow + studentCount - 1, 3, 3);
        DataValidation variantValidation = helper.createValidation(variantConstraint, variantRange);
        variantValidation.setShowErrorBox(true);
        variantValidation.setEmptyCellAllowed(true);
        sheet.addValidationData(variantValidation);

        for (int i = 0; i < taskMaxScores.size(); i++) {
            int max = taskMaxScores.get(i);
            List<String> allowed = new ArrayList<>();
            for (int score = 0; score <= max; score++) allowed.add(String.valueOf(score));
            DataValidationConstraint scoreConstraint = helper.createExplicitListConstraint(allowed.toArray(new String[0]));
            CellRangeAddressList scoreRange = new CellRangeAddressList(firstStudentRow, firstStudentRow + studentCount - 1, firstTaskCol + i, firstTaskCol + i);
            DataValidation scoreValidation = helper.createValidation(scoreConstraint, scoreRange);
            scoreValidation.setShowErrorBox(true);
            scoreValidation.setEmptyCellAllowed(true);
            sheet.addValidationData(scoreValidation);
        }
    }

    private void setupPresenceConditionalFormatting(Sheet sheet, int firstStudentRow, int studentCount, int presenceCol) {
        if (studentCount <= 0) return;
        SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();
        int excelFirstRow = firstStudentRow + 1;
        String col = CellReference.convertNumToColString(presenceCol);
        ConditionalFormattingRule presentRule = scf.createConditionalFormattingRule("EXACT($" + col + excelFirstRow + ",\"Был\")");
        PatternFormatting presentPattern = presentRule.createPatternFormatting();
        presentPattern.setFillForegroundColor(PRESENCE_PRESENT_COLOR);
        presentPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule absentRule = scf.createConditionalFormattingRule("EXACT($" + col + excelFirstRow + ",\"Не был\")");
        PatternFormatting absentPattern = absentRule.createPatternFormatting();
        absentPattern.setFillForegroundColor(PRESENCE_ABSENT_COLOR);
        absentPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        CellRangeAddress[] ranges = { new CellRangeAddress(firstStudentRow, firstStudentRow + studentCount - 1, presenceCol, presenceCol) };
        scf.addConditionalFormatting(ranges, presentRule, absentRule);
    }

    private String createTotalFormula(int taskStartCol, int excelRowNum, int tasksCount) {
        StringBuilder formula = new StringBuilder("SUM(");
        for (int i = 0; i < tasksCount; i++) {
            if (i > 0) formula.append(",");
            formula.append(CellReference.convertNumToColString(taskStartCol + i)).append(excelRowNum);
        }
        formula.append(")");
        return formula.toString();
    }

    private String validateThresholds(PaSpecification spec) {
        if (spec.getGrade5Percent() == null || spec.getGrade4Percent() == null || spec.getGrade3Percent() == null) {
            return "Пороги в спецификации биты: заполните проценты для оценок 5/4/3";
        }
        int g5 = spec.getGrade5Percent();
        int g4 = spec.getGrade4Percent();
        int g3 = spec.getGrade3Percent();
        if (g5 < 0 || g5 > 100 || g4 < 0 || g4 > 100 || g3 < 0 || g3 > 100 || g5 < g4 || g4 < g3) {
            return "Пороги в спецификации биты: ожидается диапазон 0..100 и порядок 5 ≥ 4 ≥ 3";
        }
        return null;
    }

    private String sanitizeFileName(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("[^\\p{L}\\p{N}_-]+", "_");
    }

    private PaDtos.ReportUploadResult saveRejectedReport(String academicYear,
                                                         String fileName,
                                                         String subject,
                                                         String scopeValue,
                                                         String typeRaw,
                                                         String message) {
        PaWorkType workType = parseWorkType(typeRaw);
        PaReportVersion version = new PaReportVersion();
        version.setAcademicYear(academicYear);
        version.setSubjectName(subject == null ? "" : subject.trim());
        version.setScopeType(detectScopeType(scopeValue));
        version.setScopeValue(scopeValue == null ? "" : scopeValue.trim().toUpperCase(Locale.ROOT));
        version.setLevel(PaLevel.BASIC);
        version.setWorkType(workType == null ? PaWorkType.EXIT : workType);
        version.setVersionNo(0);
        version.setActiveVersion(false);
        version.setStatus("REJECTED");
        version.setValidationMessage(message);
        version.setSourceFileName(fileName);
        version.setCreatedAt(LocalDateTime.now());
        reportVersionRepository.save(version);
        return new PaDtos.ReportUploadResult(fileName, "REJECTED", message, null, version.getSubjectName(), version.getScopeValue(), version.getWorkType());
    }

    private Map<String, String> readInfoSheet(Sheet infoSheet) {
        if (infoSheet == null) return Map.of();
        Map<String, String> values = new HashMap<>();
        for (int r = 0; r <= infoSheet.getLastRowNum(); r++) {
            Row row = infoSheet.getRow(r);
            if (row == null) continue;
            String key = normalize(getCell(row, 0));
            if (key.isBlank()) continue;
            String value = getCell(row, 1);
            values.put(key, value);
        }
        return values;
    }

    private String validateDataSheetStructure(Sheet dataSheet) {
        if (dataSheet == null) return "Не найден лист «Сбор информации»";
        Row header = dataSheet.getRow(0);
        if (header == null) return "Лист «Сбор информации»: отсутствует строка заголовков";
        String col0 = normalize(getCell(header, 0));
        String col1 = normalize(getCell(header, 1));
        if (!col0.equals("№") && !col0.equals("no")) return "Лист «Сбор информации»: неверная структура (колонка 1 должна быть №)";
        if (!col1.contains("фио")) return "Лист «Сбор информации»: неверная структура (колонка 2 должна быть ФИО ученика)";
        return null;
    }

    private String validateStudentsAgainstContingent(String academicYear, String className, Sheet dataSheet) {
        var snapshot = contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null);
        if (snapshot == null) return "Нет контингента для текущего учебного года";
        Set<String> expected = contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(s -> normalizeClass(s.getClassName()).equals(normalizeClass(className)))
                .map(s -> normalizeFio(s.getFullName()))
                .collect(Collectors.toSet());
        if (expected.isEmpty()) return "В контингенте нет класса " + className;
        List<String> unknownStudents = new ArrayList<>();
        for (int r = 3; r <= dataSheet.getLastRowNum(); r++) {
            Row row = dataSheet.getRow(r);
            if (row == null) continue;
            String fio = getCell(row, 1).trim();
            if (fio.isBlank()) continue;
            if (!expected.contains(normalizeFio(fio))) {
                unknownStudents.add(fio);
                if (unknownStudents.size() >= 3) break;
            }
        }
        if (!unknownStudents.isEmpty()) {
            return "В отчёте есть ученики не из актуального контингента класса: " + String.join(", ", unknownStudents);
        }
        return null;
    }

    private String normalizeClass(String value) {
        return String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private LocalDate parseLocalDate(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            String[] parts = raw.split("\\.");
            if (parts.length == 3) {
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<PaDtos.SummaryCell> buildSummaryCells(Set<String> subjects,
                                                       List<PaSpecification> specs,
                                                       Map<String, PaParticipation> participationMap,
                                                       int parallelFrom,
                                                       int parallelTo) {
        List<PaDtos.SummaryCell> cells = new ArrayList<>();
        Map<String, Set<String>> entryScopes = specs.stream()
                .filter(s -> s.getWorkType() == PaWorkType.ENTRY)
                .collect(Collectors.groupingBy(PaSpecification::getSubjectName, Collectors.mapping(PaSpecification::getScopeValue, Collectors.toSet())));
        Map<String, Set<String>> exitScopes = specs.stream()
                .filter(s -> s.getWorkType() == PaWorkType.EXIT)
                .collect(Collectors.groupingBy(PaSpecification::getSubjectName, Collectors.mapping(PaSpecification::getScopeValue, Collectors.toSet())));
        for (String subject : subjects) {
            for (int p = parallelFrom; p <= parallelTo; p++) {
                String scope = String.valueOf(p);
                boolean hasEntrySpec = entryScopes.getOrDefault(subject, Set.of()).stream().anyMatch(v -> v.startsWith(scope));
                boolean hasExitSpec = exitScopes.getOrDefault(subject, Set.of()).stream().anyMatch(v -> v.startsWith(scope));
                PaParticipation participation = participationMap.get(participationKey(subject, scope, PaLevel.BASIC));
                boolean participates = participation == null || participation.isParticipates();
                cells.add(new PaDtos.SummaryCell(subject, scope, PaLevel.BASIC, participates, hasEntrySpec, hasExitSpec));
            }
        }
        return cells;
    }

    private String participationKey(PaParticipation p) {
        return participationKey(p.getSubjectName(), p.getScopeValue(), p.getLevel());
    }

    private String participationKey(String subject, String scopeValue, PaLevel level) {
        return (subject + "|" + scopeValue + "|" + level.name()).toLowerCase(Locale.ROOT);
    }

    private List<Integer> findRowsByCellValue(Sheet sheet, String expected) {
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cellValue(cell).equalsIgnoreCase(expected)) {
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    private int findColumnByCellValue(Row row, String expected) {
        if (row == null) return -1;
        for (Cell cell : row) {
            if (cellValue(cell).equalsIgnoreCase(expected)) return cell.getColumnIndex();
        }
        return -1;
    }

    private List<int[]> findCellsByValue(Sheet sheet, String expected) {
        List<int[]> coords = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cellValue(cell).equalsIgnoreCase(expected)) {
                    coords.add(new int[]{r, cell.getColumnIndex()});
                }
            }
        }
        return coords;
    }

    private String findValueNearLabel(Sheet sheet, int startRow, int startCol, int blockEndCol, String label) {
        int maxRow = Math.min(sheet.getLastRowNum(), startRow + 20);
        for (int r = startRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = Math.max(0, startCol - 1); c <= blockEndCol; c++) {
                if (sameLabel(getCell(row, c), label)) {
                    return firstNonBlank(sheet, r, c + 1, blockEndCol);
                }
            }
        }
        return "";
    }

    private String firstNonBlank(Sheet sheet, int row, int fromCol, int toCol) {
        Row r = sheet.getRow(row);
        if (r == null) return "";
        for (int c = fromCol; c <= toCol; c++) {
            String value = getCell(r, c);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String getCell(Row row, Integer colIdx) {
        if (row == null || colIdx == null || colIdx < 0) return "";
        return cellValue(row.getCell(colIdx)).trim();
    }

    private String cellValue(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private Integer parsePercent(String value) {
        if (value == null) return null;
        String cleaned = value.replace("%", "").replace(",", ".").trim();
        if (cleaned.isBlank()) return null;
        try {
            double v = Double.parseDouble(cleaned);
            if (v <= 1.0) v = v * 100.0;
            return (int) Math.round(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer resolveThresholdPercent(Sheet sheet, int startRow, int startCol, int blockEndCol, String label) {
        int maxRow = Math.min(sheet.getLastRowNum(), startRow + 25);

        int scaleRow = -1;
        for (int r = startRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            for (int c = Math.max(0, startCol - 1); c <= blockEndCol; c++) {
                if (containsNormalized(getCell(row, c), "Шкала оценивания")) {
                    scaleRow = r;
                    break;
                }
            }

            if (scaleRow >= 0) break;
        }

        if (scaleRow < 0) {
            return null;
        }

        String normalizedLabel = normalizeLabel(label);

        for (int r = scaleRow + 1; r <= Math.min(sheet.getLastRowNum(), scaleRow + 6); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            for (int c = Math.max(0, startCol - 1); c <= blockEndCol; c++) {
                String cell = getCell(row, c);
                String norm = normalizeLabel(cell);

                if (norm.equals(normalizedLabel)) {
                    Integer valueFromRight = parsePercent(firstNonBlank(sheet, r, c + 1, blockEndCol));
                    if (valueFromRight != null) {
                        return valueFromRight;
                    }
                }

                Matcher inline = Pattern.compile("^\\s*\"?" + Pattern.quote(label) + "\"?\\s*[:：]\\s*(.+)$")
                        .matcher(cell);

                if (inline.find()) {
                    Integer inlineValue = parsePercent(inline.group(1));
                    if (inlineValue != null) {
                        return inlineValue;
                    }
                }
            }
        }

        return null;
    }

    private PaWorkType parseWorkType(String raw) {
        String value = normalize(raw);
        if (value.contains("вход")) return PaWorkType.ENTRY;
        if (value.contains("выход")) return PaWorkType.EXIT;
        if (value.contains("промеж")) return PaWorkType.MID;
        return null;
    }

    private PaLevel parseLevel(String raw) {
        String value = normalize(raw);
        if (value.contains("углуб")) return PaLevel.ADVANCED;
        return PaLevel.BASIC;
    }

    private PaTaskKind parseTaskKind(String raw) {
        String value = normalize(raw);
        if (value.contains("повтор")) return PaTaskKind.REPEAT;
        if (value.contains("нов")) return PaTaskKind.NEW;
        return null;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private Integer parseInt(String value) {
        String cleaned = String.valueOf(value == null ? "" : value).trim().replace(",", ".");
        if (cleaned.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (Exception ignored) {
            return null;
        }
    }

    private PaScopeType detectScopeType(String scope) {
        String value = String.valueOf(scope == null ? "" : scope).trim().toUpperCase(Locale.ROOT);
        return value.matches("^\\d{1,2}$") ? PaScopeType.PARALLEL : PaScopeType.CLASS;
    }

    private String buildPairKey(String academicYear, String subject, String scope, PaLevel level, PaWorkType workType, String sheetName) {
        return String.join("|",
                String.valueOf(academicYear),
                normalize(subject),
                normalize(scope),
                level.name(),
                workType.name(),
                normalize(sheetName)
        );
    }

    private int detectBlockEndCol(Sheet sheet, int subjectRow, int subjectCol) {
        Row subjectRowObj = sheet.getRow(subjectRow);
        if (subjectRowObj == null) {
            return subjectCol + 4;
        }
        int nextSubjectCol = findNextSubjectColOnRow(subjectRowObj, subjectCol);
        if (nextSubjectCol > subjectCol) {
            return nextSubjectCol - 1;
        }

        int maxCol = subjectCol + 4;
        int maxRow = Math.min(sheet.getLastRowNum(), subjectRow + 200);
        for (int r = subjectRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                int col = cell.getColumnIndex();
                if (col < subjectCol) {
                    continue;
                }
                String value = cellValue(cell);
                if (!value.isBlank()) {
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        return maxCol;
    }

    private int findNextSubjectColOnRow(Row row, int subjectCol) {
        if (row == null) return -1;
        for (Cell cell : row) {
            int col = cell.getColumnIndex();
            if (col <= subjectCol) continue;
            if (sameLabel(cellValue(cell), "Предмет")) return col;
        }
        return -1;
    }

    private boolean sameLabel(String actual, String expected) {
        return normalizeLabel(actual).equals(normalizeLabel(expected));
    }

    private boolean containsNormalized(String actual, String expected) {
        return normalizeForSearch(actual).contains(normalizeForSearch(expected));
    }

    private String normalizeLabel(String value) {
        return normalize(value)
                .replaceAll("[\\s:\"'«»“”„]+", "")
                .replaceAll(":+$", "")
                .trim();
    }

    private String normalizeForSearch(String value) {
        return normalize(value)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Integer parseRepeatFromTaskNo(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isBlank()) {
            return null;
        }

        Integer direct = parseInt(raw);
        if (direct != null) {
            return direct;
        }

        Matcher matcher = Pattern.compile("(?i)задани[ея]\\s*(\\d{1,2})").matcher(raw);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseParallel(String className) {
        if (className == null) return null;
        Matcher matcher = PARALLEL_PATTERN.matcher(className.trim());
        if (!matcher.matches()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeFio(String fio) {
        return String.valueOf(fio == null ? "" : fio)
                .trim()
                .replace('ё', 'е')
                .replace('Ё', 'Е')
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
