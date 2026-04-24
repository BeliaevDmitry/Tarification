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
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaServiceImpl implements PaService {

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^(\\d{1,2}).*");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));

    private final PaSpecificationRepository specificationRepository;
    private final PaSpecificationTaskRepository taskRepository;
    private final PaParticipationRepository participationRepository;
    private final PaReportVersionRepository reportVersionRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;

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
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    importedSpecs += importSheet(academicYear, file.getOriginalFilename(), sheet, warnings);
                }
            } catch (Exception e) {
                warnings.add("Ошибка чтения файла: " + e.getMessage());
            }
            if (warnings.stream().noneMatch(w -> w.startsWith("Ошибка"))) {
                importedTasks = specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(academicYear).stream()
                        .filter(s -> Objects.equals(s.getSourceFileName(), file.getOriginalFilename()))
                        .mapToInt(s -> taskRepository.findAllBySpecificationIdOrderByTaskNoAsc(s.getId()).size())
                        .sum();
            }
            results.add(new PaDtos.ImportResult(file.getOriginalFilename(), importedSpecs, importedTasks, warnings));
        }
        return results;
    }

    private int importSheet(String academicYear, String sourceFileName, Sheet sheet, List<String> warnings) {
        List<Integer> subjectRows = findRowsByCellValue(sheet, "Предмет");
        if (subjectRows.isEmpty()) {
            warnings.add("Лист " + sheet.getSheetName() + ": не найден блок 'Предмет'");
            return 0;
        }
        int imported = 0;
        for (Integer subjectRow : subjectRows) {
            int subjectCol = findColumnByCellValue(sheet.getRow(subjectRow), "Предмет");
            if (subjectCol < 0) continue;
            String subjectName = firstNonBlank(sheet, subjectRow, subjectCol + 1, subjectCol + 6);
            if (subjectName.isBlank()) continue;
            PaSpecification spec = parseBlock(academicYear, sourceFileName, sheet, subjectRow, subjectCol, warnings);
            if (spec == null) continue;
            spec.setCreatedAt(LocalDateTime.now());
            spec.setVersionNo(specificationRepository.findMaxVersion(
                    spec.getAcademicYear(),
                    spec.getSubjectName(),
                    spec.getScopeType(),
                    spec.getScopeValue(),
                    spec.getLevel(),
                    spec.getWorkType(),
                    spec.getWorkDate()
            ) + 1);
            PaSpecification saved = specificationRepository.save(spec);
            List<PaSpecificationTask> tasks = parseTasks(sheet, subjectRow, subjectCol, saved);
            if (!tasks.isEmpty()) {
                taskRepository.saveAll(tasks);
            }
            imported += 1;
        }
        return imported;
    }

    private PaSpecification parseBlock(String academicYear, String sourceFileName, Sheet sheet, int baseRow, int baseCol, List<String> warnings) {
        String subject = firstNonBlank(sheet, baseRow, baseCol + 1, baseCol + 6);
        String scope = findValueNearLabel(sheet, baseRow, baseCol, "Параллель/Класс");
        String workTypeRaw = findValueNearLabel(sheet, baseRow, baseCol, "Тип");
        if (subject.isBlank() || scope.isBlank() || workTypeRaw.isBlank()) {
            warnings.add("Лист " + sheet.getSheetName() + ": пропущены обязательные поля (предмет/параллель/тип)");
            return null;
        }
        PaWorkType workType = parseWorkType(workTypeRaw);
        if (workType == null) {
            warnings.add("Лист " + sheet.getSheetName() + ": неизвестный тип работы '" + workTypeRaw + "'");
            return null;
        }

        String levelRaw = findValueNearLabel(sheet, baseRow, baseCol, "Уровень");
        PaLevel level = parseLevel(levelRaw);

        PaSpecification spec = new PaSpecification();
        spec.setAcademicYear(academicYear);
        spec.setSubjectName(subject.trim());
        spec.setScopeValue(scope.trim().toUpperCase(Locale.ROOT));
        spec.setScopeType(detectScopeType(scope));
        spec.setWorkType(workType);
        spec.setLevel(level);
        spec.setSchoolName(findValueNearLabel(sheet, baseRow, baseCol, "Школа"));
        spec.setTeacherFio(findValueNearLabel(sheet, baseRow, baseCol, "Учитель"));
        spec.setTeacherFioNormalized(normalizeFio(spec.getTeacherFio()));
        spec.setGrade5Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, "\"5\"")));
        spec.setGrade4Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, "\"4\"")));
        spec.setGrade3Percent(parsePercent(findValueNearLabel(sheet, baseRow, baseCol, "\"3\"")));
        spec.setSourceFileName(sourceFileName);
        spec.setPairKey(buildPairKey(academicYear, subject, scope, level, sheet.getSheetName()));
        spec.setActiveVersion(true);
        return spec;
    }

    private List<PaSpecificationTask> parseTasks(Sheet sheet, int baseRow, int baseCol, PaSpecification specification) {
        int headerRow = -1;
        int maxRow = Math.min(sheet.getLastRowNum(), baseRow + 200);
        for (int r = baseRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (containsValue(row, "№ задания")) {
                headerRow = r;
                break;
            }
        }
        if (headerRow < 0) return List.of();

        Row header = sheet.getRow(headerRow);
        Map<String, Integer> colMap = new HashMap<>();
        for (Cell cell : header) {
            String value = cellValue(cell).toLowerCase(Locale.ROOT);
            if (value.contains("№ задания")) colMap.put("task", cell.getColumnIndex());
            if (value.contains("тема")) colMap.put("topic", cell.getColumnIndex());
            if (value.contains("навык")) colMap.put("skill", cell.getColumnIndex());
            if (value.contains("тип задания")) colMap.put("kind", cell.getColumnIndex());
            if (value.contains("если повторение")) colMap.put("repeat", cell.getColumnIndex());
            if (value.contains("балл")) colMap.put("score", cell.getColumnIndex());
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
            String maxScoreRaw = getCell(row, colMap.get("score"));
            if (taskNoRaw.isBlank() && topic.isBlank() && maxScoreRaw.isBlank()) {
                emptyStreak++;
                if (emptyStreak >= 3) break;
                continue;
            }
            emptyStreak = 0;
            Integer taskNo = parseInt(taskNoRaw);
            if (taskNo == null) continue;

            PaSpecificationTask task = new PaSpecificationTask();
            task.setSpecification(specification);
            task.setTaskNo(taskNo);
            task.setTopic(topic);
            task.setSkill(getCell(row, colMap.get("skill")));
            task.setTaskKind(parseTaskKind(getCell(row, colMap.get("kind"))));
            task.setRepeatFromTaskNo(parseInt(getCell(row, colMap.get("repeat"))));
            task.setMaxScore(parseInt(maxScoreRaw));
            tasks.add(task);
        }
        return tasks;
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
        Map<String, Set<String>> subjectToScopes = specs.stream()
                .collect(Collectors.groupingBy(PaSpecification::getSubjectName, Collectors.mapping(PaSpecification::getScopeValue, Collectors.toSet())));
        for (String subject : subjects) {
            for (int p = parallelFrom; p <= parallelTo; p++) {
                String scope = String.valueOf(p);
                boolean hasSpec = subjectToScopes.getOrDefault(subject, Set.of()).stream().anyMatch(v -> v.startsWith(scope));
                PaParticipation participation = participationMap.get(participationKey(subject, scope, PaLevel.BASIC));
                boolean participates = participation == null || participation.isParticipates();
                cells.add(new PaDtos.SummaryCell(subject, scope, PaLevel.BASIC, participates, hasSpec));
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

    private String findValueNearLabel(Sheet sheet, int startRow, int startCol, String label) {
        int maxRow = Math.min(sheet.getLastRowNum(), startRow + 20);
        for (int r = startRow; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = Math.max(0, startCol - 1); c <= startCol + 4; c++) {
                if (label.equalsIgnoreCase(getCell(row, c))) {
                    return firstNonBlank(sheet, r, c + 1, c + 6);
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

    private boolean containsValue(Row row, String value) {
        for (Cell cell : row) {
            if (cellValue(cell).toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
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

    private String buildPairKey(String academicYear, String subject, String scope, PaLevel level, String sheetName) {
        return String.join("|",
                String.valueOf(academicYear),
                normalize(subject),
                normalize(scope),
                level.name(),
                normalize(sheetName)
        );
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
