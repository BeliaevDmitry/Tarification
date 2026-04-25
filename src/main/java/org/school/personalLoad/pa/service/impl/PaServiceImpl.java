package org.school.personalLoad.pa.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaParticipationRepository;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
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
    private record SheetImportStats(int specs, int tasks) {}

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^(\\d{1,2}).*");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));
    private static final String PA_REPORT_STORAGE_DIR = "pa-reports";
    private static final String PA_SPEC_STORAGE_DIR = "pa-specifications";
    private static final int TEMPLATE_MAX_STUDENTS = 500;
    private static final int TEMPLATE_VARIANTS_COUNT = 6;

    private final PaSpecificationRepository specificationRepository;
    private final PaSpecificationTaskRepository taskRepository;
    private final PaParticipationRepository participationRepository;
    private final PaReportVersionRepository reportVersionRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    @Override
    @Transactional
    public List<PaDtos.ImportResult> importSpecifications(String academicYear, List<MultipartFile> files) {
        List<PaDtos.ImportResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                results.add(new PaDtos.ImportResult(file == null ? "unknown" : file.getOriginalFilename(), 0, 0, List.of("Файл пустой")));
                continue;
            }
            List<String> warnings = new ArrayList<>();
            int importedSpecs = 0;
            int importedTasks = 0;
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
                }
            } catch (Exception e) {
                warnings.add("Ошибка чтения файла: " + e.getMessage());
            }
            results.add(new PaDtos.ImportResult(file.getOriginalFilename(), importedSpecs, importedTasks, warnings));
        }
        return results;
    }

    private SheetImportStats importSheet(String academicYear, String sourceFileName, Sheet sheet, List<String> warnings) {
        List<int[]> subjectCells = findCellsByValue(sheet, "Предмет");
        if (subjectCells.isEmpty()) {
            warnings.add("Лист " + sheet.getSheetName() + ": не найден блок 'Предмет'");
            return new SheetImportStats(0, 0);
        }
        int importedSpecs = 0;
        int importedTasks = 0;
        for (int[] cellPos : subjectCells) {
            int subjectRow = cellPos[0];
            int subjectCol = cellPos[1];
            String subjectName = firstNonBlank(sheet, subjectRow, subjectCol + 1, subjectCol + 6);
            if (subjectName.isBlank()) continue;
            int blockEndCol = detectBlockEndCol(sheet, subjectRow, subjectCol);
            PaSpecification spec = parseBlock(academicYear, sourceFileName, sheet, subjectRow, subjectCol, blockEndCol, warnings);
            if (spec == null) continue;
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
            List<PaSpecificationTask> tasks = parseTasks(sheet, subjectRow, subjectCol, blockEndCol, saved);
            if (tasks.isEmpty()) {
                specificationRepository.delete(saved);
                warnings.add("Лист " + sheet.getSheetName() + ": спецификация '" + subjectName + "' не загружена — нет ни одной темы");
                continue;
            }
            taskRepository.saveAll(tasks);
            importedSpecs += 1;
            importedTasks += tasks.size();
        }
        return new SheetImportStats(importedSpecs, importedTasks);
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
        spec.setGrade5Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "5")));
        spec.setGrade4Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "4")));
        spec.setGrade3Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, blockEndCol, "3")));
        spec.setSourceFileName(sourceFileName);
        spec.setPairKey(buildPairKey(academicYear, subject, scope, level, workType, sheet.getSheetName()));
        spec.setActiveVersion(true);
        return spec;
    }

    private List<PaSpecificationTask> parseTasks(Sheet sheet, int baseRow, int baseCol, int blockEndCol, PaSpecification specification) {
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
    public PaDtos.ReportUploadResult generateReportTemplate(String academicYear, String subjectName, String className, PaLevel level, PaWorkType workType, LocalDate workDate) {
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
                createInfoSheet(workbook, academicYear, subjectName, className, teacherFio, level, workType, workDate);
                createDataSheet(workbook, students, tasks);
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
    public List<PaDtos.ReportUploadResult> generateReportTemplatesByParallel(String academicYear, String subjectName, String parallel, PaLevel level, PaWorkType workType, LocalDate workDate) {
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
            if (hasActiveGeneratedTemplate(academicYear, subjectName, className, level, workType, workDate)) {
                results.add(new PaDtos.ReportUploadResult("", "SKIPPED", "Шаблон уже сгенерирован для класса", null, subjectName, className, workType));
                continue;
            }
            results.add(generateReportTemplate(academicYear, subjectName, className, level, workType, workDate));
        }
        return results;
    }

    @Override
    @Transactional
    public List<PaDtos.ReportUploadResult> generateAllReportTemplates(String academicYear, String subjectName, PaLevel level, PaWorkType workType, LocalDate workDate) {
        List<String> classes = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .map(CurriculumPlanEntry::getClassName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted()
                .toList();
        List<PaDtos.ReportUploadResult> results = new ArrayList<>();
        for (String className : classes) {
            if (hasActiveGeneratedTemplate(academicYear, subjectName, className, level, workType, workDate)) {
                results.add(new PaDtos.ReportUploadResult("", "SKIPPED", "Шаблон уже сгенерирован для класса", null, subjectName, className, workType));
                continue;
            }
            results.add(generateReportTemplate(academicYear, subjectName, className, level, workType, workDate));
        }
        return results;
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
        if (version.getSourceFilePath() == null || version.getSourceFilePath().isBlank()) {
            throw new IllegalArgumentException("Для версии не сохранён путь к файлу");
        }
        Path path = Path.of(version.getSourceFilePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Файл версии не найден на диске");
        }
        version.setDownloadedAtLeastOnce(true);
        reportVersionRepository.save(version);
        return Files.readAllBytes(path);
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
                                 LocalDate workDate) {
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
            row.createCell(0).setCellValue(rows[i][0]);
            row.createCell(1).setCellValue(rows[i][1]);
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

    private void createDataSheet(Workbook workbook, List<String> students, List<PaSpecificationTask> tasks) {
        Sheet data = workbook.createSheet("Сбор информации");
        List<Integer> taskMaxScores = resolveTaskMaxScores(tasks);
        int firstTaskCol = 4;
        int firstStudentRow = 3;
        int studentCount = students.size();
        int templateRowCount = Math.max(TEMPLATE_MAX_STUDENTS, studentCount);

        Row headerRow = data.createRow(0);
        headerRow.createCell(0).setCellValue("№");
        headerRow.createCell(1).setCellValue("ФИО ученика");
        headerRow.createCell(2).setCellValue("Присутствие");
        headerRow.createCell(3).setCellValue("Вариант");
        headerRow.createCell(firstTaskCol).setCellValue("Баллы за задания");
        data.addMergedRegion(new CellRangeAddress(0, 0, firstTaskCol, firstTaskCol + tasks.size() - 1));
        headerRow.createCell(firstTaskCol + tasks.size()).setCellValue("Итог");

        Row taskNoRow = data.createRow(1);
        for (int i = 0; i < 4; i++) taskNoRow.createCell(i).setCellValue("");
        for (int i = 0; i < tasks.size(); i++) {
            taskNoRow.createCell(firstTaskCol + i).setCellValue(tasks.get(i).getTaskNo() == null ? i + 1 : tasks.get(i).getTaskNo());
        }
        taskNoRow.createCell(firstTaskCol + tasks.size()).setCellValue("");

        Row maxScoresRow = data.createRow(2);
        for (int i = 0; i < 4; i++) maxScoresRow.createCell(i).setCellValue("");
        for (int i = 0; i < taskMaxScores.size(); i++) {
            maxScoresRow.createCell(firstTaskCol + i).setCellValue(taskMaxScores.get(i));
        }
        maxScoresRow.createCell(firstTaskCol + tasks.size()).setCellValue("");

        for (int i = 0; i < studentCount; i++) {
            Row row = data.createRow(firstStudentRow + i);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(students.get(i));
            row.createCell(2).setCellValue("");
            row.createCell(3).setCellValue("");
            for (int t = 0; t < tasks.size(); t++) {
                row.createCell(firstTaskCol + t).setCellValue("");
            }
            Cell totalCell = row.createCell(firstTaskCol + tasks.size());
            totalCell.setCellFormula(createTotalFormula(firstTaskCol, firstStudentRow + i + 1, tasks.size()));
        }

        for (int i = studentCount; i < templateRowCount; i++) {
            Row row = data.createRow(firstStudentRow + i);
            row.createCell(0).setCellValue(i + 1);
            for (int c = 1; c <= firstTaskCol + tasks.size(); c++) {
                row.createCell(c).setCellValue("");
            }
        }

        setupTemplateValidation(data, firstStudentRow, studentCount, taskMaxScores, firstTaskCol);
        setupPresenceConditionalFormatting(data, firstStudentRow, studentCount, 2);

        data.setColumnWidth(0, 1200);
        data.setColumnWidth(1, 9000);
        data.setColumnWidth(2, 3200);
        data.setColumnWidth(3, 2600);
        for (int i = 0; i < tasks.size(); i++) data.setColumnWidth(firstTaskCol + i, 1500);
        data.setColumnWidth(firstTaskCol + tasks.size(), 2200);
        data.createFreezePane(0, 3);
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
        presentPattern.setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        presentPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule absentRule = scf.createConditionalFormattingRule("EXACT($" + col + excelFirstRow + ",\"Не был\")");
        PatternFormatting absentPattern = absentRule.createPatternFormatting();
        absentPattern.setFillBackgroundColor(IndexedColors.RED.getIndex());
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
        Row row = sheet.getRow(subjectRow);
        if (row == null) return subjectCol + 4;
        int nextSubjectCol = -1;
        for (Cell cell : row) {
            int col = cell.getColumnIndex();
            if (col <= subjectCol) continue;
            if (sameLabel(cellValue(cell), "Предмет")) {
                nextSubjectCol = col;
                break;
            }
        }
        if (nextSubjectCol > subjectCol) {
            return nextSubjectCol - 1;
        }
        return Math.max(subjectCol + 4, row.getLastCellNum() - 1);
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
                .replaceAll("[\\s\\n\\r\\t]+", " ")
                .trim();
    }

    private Integer parseRepeatFromTaskNo(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isBlank()) return null;
        Integer direct = parseInt(raw);
        if (direct != null) return direct;
        Matcher matcher = Pattern.compile("(?i)(?:задани[ея]\\s*)?(\\d{1,2})").matcher(raw);
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
