package org.school.personalLoad.oge.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.oge.dto.OgeDtos;
import org.school.personalLoad.oge.model.OgeGiaParticipant;
import org.school.personalLoad.oge.model.OgeGiaVersion;
import org.school.personalLoad.oge.model.OgeScoreScaleEntry;
import org.school.personalLoad.oge.model.OgeWorkResult;
import org.school.personalLoad.oge.model.OgeTeacherBinding;
import org.school.personalLoad.oge.model.OgeTaskScaleEntry;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.oge.repository.OgeGiaVersionRepository;
import org.school.personalLoad.oge.repository.OgeScoreScaleRepository;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.oge.repository.OgeTeacherBindingRepository;
import org.school.personalLoad.oge.repository.OgeTaskScaleRepository;
import org.school.personalLoad.oge.repository.SubjectAliasRepository;
import org.school.personalLoad.oge.model.SubjectAlias;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OgeService {
    private static final Pattern EXTERNAL_DATE_PATTERN = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2}");
    private static final Map<String, List<Integer>> MANUAL_MAX_SCORES = new LinkedHashMap<>();

    static {
        MANUAL_MAX_SCORES.put("математика", List.of(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2));
        MANUAL_MAX_SCORES.put("русский язык", List.of(6,1,1,1,1,1,1,1,1,1,1,1,7,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1));
        MANUAL_MAX_SCORES.put("обществознание", List.of(2,1,1,1,3,2,1,1,1,1,1,4,1,1,2,1,1,1,1,1,2,2,3,2,1));
        MANUAL_MAX_SCORES.put("физика", List.of(2,2,1,2,1,1,1,1,1,1,1,2,2,2,1,2,3,2,2,3,3,3));
        MANUAL_MAX_SCORES.put("химия", List.of(1,1,1,2,1,1,1,1,2,2,1,2,1,1,1,1,2,1,1,3,3,3,5,1,1,1));
        MANUAL_MAX_SCORES.put("биология", List.of(1,1,1,2,2,1,2,1,2,2,2,1,3,1,1,2,2,2,2,1,2,2,2,3,3,3,3));
        MANUAL_MAX_SCORES.put("история", List.of(2,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,2,2,2,2,2,3,3,1,1,1,1,1,1,1,1,1,1,1,1,1,1));
        MANUAL_MAX_SCORES.put("география", List.of(1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1));
        MANUAL_MAX_SCORES.put("информатика и икт", List.of(1,1,1,1,1,1,1,1,1,1,1,1,2,3,2,2));
        MANUAL_MAX_SCORES.put("литература", List.of(5,5,5,8,17));
        MANUAL_MAX_SCORES.put("английский язык", List.of(1,1,1,1,5,1,1,1,1,1,1,6,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,10,2,6,7));
        MANUAL_MAX_SCORES.put("немецкий язык", List.of(1,1,1,1,5,1,1,1,1,1,1,6,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,10,2,6,7));
        MANUAL_MAX_SCORES.put("французский язык", List.of(1,1,1,1,5,1,1,1,1,1,1,6,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,10,2,6,7));
        MANUAL_MAX_SCORES.put("испанский язык", List.of(1,1,1,1,5,1,1,1,1,1,1,6,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,10,2,6,7));
    }

    private final OgeGiaVersionRepository giaVersionRepository;
    private final OgeWorkResultRepository workResultRepository;
    private final OgeScoreScaleRepository scoreScaleRepository;
    private final OgeTeacherBindingRepository teacherBindingRepository;
    private final OgeTaskScaleRepository taskScaleRepository;
    private final SubjectAliasRepository subjectAliasRepository;
    private final ContingentSnapshotRepository contingentSnapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    @Transactional
    public List<OgeDtos.ImportFileResult> importGia(String academicYear, List<MultipartFile> files) {
        List<OgeDtos.ImportFileResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.xlsx");
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                results.add(new OgeDtos.ImportFileResult(fileName, false, "Поддерживается только .xlsx", 0));
                continue;
            }
            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet sheet = workbook.getSheet("ГИА-9");
                if (sheet == null) sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                if (sheet == null) {
                    results.add(new OgeDtos.ImportFileResult(fileName, false, "Не найден лист ГИА-9", 0));
                    continue;
                }
                OgeGiaVersion version = new OgeGiaVersion();
                version.setSourceFileName(fileName);
                version.setAcademicYear(academicYear);
                version.setUploadedAt(LocalDateTime.now());

                int loaded = 0;
                for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isEmptyRow(row, 0, 60)) continue;
                    String fio = OgeSubjects.normalizeFio(getText(row.getCell(0)));
                    if (fio.isBlank()) continue;
                    OgeGiaParticipant p = new OgeGiaParticipant();
                    p.setVersion(version);
                    p.setFullName(fio);
                    p.setSnils(OgeSubjects.normalizeSnils(getText(row.getCell(3))));
                    p.setDocument(getText(row.getCell(2)).trim());
                    p.setClassName(OgeSubjects.normalizeClassName(getText(row.getCell(5))));
                    p.setAcademicYear(academicYear);
                    p.setExamCount(parseInt(getText(row.getCell(28))));

                    Set<String> selected = new LinkedHashSet<>();
                    Map<Integer, Integer> flags = new HashMap<>();
                    for (int c = 29; c <= 60; c++) {
                        Integer code = OgeSubjects.SUBJECT_CODES_ORDER.get(c - 29);
                        Integer flag = parseInt(getText(row.getCell(c)));
                        if (flag != null) flags.put(code, flag);
                        if (flag != null && flag == 1) {
                            String core = OgeSubjects.toCoreSubject(code);
                            if (core != null) selected.add(core);
                        }
                    }
                    p.setSelectedSubjects(selected);
                    p.setDataIssue(detectLanguageMismatch(flags));
                    version.getParticipants().add(p);
                    loaded++;
                }
                giaVersionRepository.save(version);
                results.add(new OgeDtos.ImportFileResult(fileName, true, "OK", loaded));
            } catch (Exception ex) {
                results.add(new OgeDtos.ImportFileResult(fileName, false, ex.getMessage(), 0));
            }
        }
        return results;
    }

    public List<OgeDtos.GiaVersionView> versions(String academicYear) {
        return giaVersionRepository.findAllByAcademicYearOrderByUploadedAtDescIdDesc(academicYear).stream()
                .map(v -> new OgeDtos.GiaVersionView(v.getId(), v.getSourceFileName(), v.getUploadedAt(), v.getParticipants().size()))
                .toList();
    }

    public List<OgeDtos.GiaParticipantView> latestParticipants(String academicYear) {
        OgeGiaVersion latest = latestVersion(academicYear);
        if (latest == null) return List.of();
        return latest.getParticipants().stream()
                .sorted(Comparator.comparing(OgeGiaParticipant::getClassName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(OgeGiaParticipant::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new OgeDtos.GiaParticipantView(
                        blank(p.getClassName()), p.getFullName(), blank(p.getSnils()),
                        p.getExamCount() == null ? p.getSelectedSubjects().size() : p.getExamCount(),
                        p.getSelectedSubjects().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
                ))
                .toList();
    }

    public OgeDtos.GiaStatsResponse giaStats(String academicYear) {
        OgeGiaVersion latest = latestVersion(academicYear);
        if (latest == null) {
            return new OgeDtos.GiaStatsResponse(OgeSubjects.CORE_SUBJECTS, List.of(), Map.of(), Map.of());
        }
        Map<String, Map<String, Integer>> byClass = new TreeMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        OgeSubjects.CORE_SUBJECTS.forEach(s -> totals.put(s, 0));
        Map<Integer, Integer> examDist = new TreeMap<>();

        for (OgeGiaParticipant p : latest.getParticipants()) {
            String cls = blank(p.getClassName()).isBlank() ? "Не указан" : p.getClassName();
            Map<String, Integer> row = byClass.computeIfAbsent(cls, k -> new LinkedHashMap<>());
            OgeSubjects.CORE_SUBJECTS.forEach(s -> row.putIfAbsent(s, 0));
            for (String subject : p.getSelectedSubjects()) {
                row.computeIfPresent(subject, (k, v) -> v + 1);
                totals.computeIfPresent(subject, (k, v) -> v + 1);
            }
            int examCount = p.getExamCount() != null ? p.getExamCount() : p.getSelectedSubjects().size();
            examDist.merge(examCount, 1, Integer::sum);
        }

        List<OgeDtos.GiaClassStatsRow> rows = byClass.entrySet().stream()
                .map(e -> new OgeDtos.GiaClassStatsRow(e.getKey(), e.getValue()))
                .toList();

        return new OgeDtos.GiaStatsResponse(OgeSubjects.CORE_SUBJECTS, rows, totals, examDist);
    }

    public OgeDtos.GiaChangesResponse changesBetweenLastTwo(String academicYear) {
        List<OgeGiaVersion> versions = giaVersionRepository.findTop2ByAcademicYearOrderByUploadedAtDescIdDesc(academicYear);
        if (versions.size() < 2) return new OgeDtos.GiaChangesResponse(List.of());
        OgeGiaVersion current = versions.get(0);
        OgeGiaVersion previous = versions.get(1);

        Map<String, OgeGiaParticipant> cur = indexByKey(current.getParticipants());
        Map<String, OgeGiaParticipant> prev = indexByKey(previous.getParticipants());

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(cur.keySet());
        allKeys.addAll(prev.keySet());

        List<OgeDtos.GiaChangeItem> changes = new ArrayList<>();
        for (String key : allKeys) {
            OgeGiaParticipant c = cur.get(key);
            OgeGiaParticipant p = prev.get(key);
            if (p == null) {
                changes.add(new OgeDtos.GiaChangeItem("ДОБАВЛЕН УЧЕНИК", displayKey(c), "", toSubjects(c)));
                continue;
            }
            if (c == null) {
                changes.add(new OgeDtos.GiaChangeItem("УДАЛЁН УЧЕНИК", displayKey(p), toSubjects(p), ""));
                continue;
            }
            if (!Objects.equals(blank(c.getClassName()), blank(p.getClassName()))) {
                changes.add(new OgeDtos.GiaChangeItem("ИЗМЕНЁН КЛАСС", displayKey(c), blank(p.getClassName()), blank(c.getClassName())));
            }
            if (!Objects.equals(c.getFullName(), p.getFullName())) {
                changes.add(new OgeDtos.GiaChangeItem("ИЗМЕНЕНИЕ ДАННЫХ", displayKey(c), p.getFullName(), c.getFullName()));
            }

            Set<String> added = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            added.addAll(c.getSelectedSubjects());
            added.removeAll(p.getSelectedSubjects());
            for (String sub : added) {
                changes.add(new OgeDtos.GiaChangeItem("ДОБАВЛЕН ПРЕДМЕТ", displayKey(c), "", sub));
            }
            Set<String> removed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            removed.addAll(p.getSelectedSubjects());
            removed.removeAll(c.getSelectedSubjects());
            for (String sub : removed) {
                changes.add(new OgeDtos.GiaChangeItem("УДАЛЁН ПРЕДМЕТ", displayKey(c), sub, ""));
            }
        }

        changes.addAll(findOralMismatchErrors(current));

        return new OgeDtos.GiaChangesResponse(changes);
    }

    private List<OgeDtos.GiaChangeItem> findOralMismatchErrors(OgeGiaVersion version) {
        List<OgeDtos.GiaChangeItem> errors = new ArrayList<>();
        for (OgeGiaParticipant p : version.getParticipants()) {
            if (p.getDataIssue() != null && !p.getDataIssue().isBlank()) {
                errors.add(new OgeDtos.GiaChangeItem("ОШИБКА", displayKey(p), p.getDataIssue(), ""));
            }
        }
        return errors;
    }

    private String detectLanguageMismatch(Map<Integer, Integer> flags) {
        List<String> issues = new ArrayList<>();
        checkPair(flags, 9, 29, "Английский язык", issues);
        checkPair(flags, 10, 30, "Немецкий язык", issues);
        checkPair(flags, 11, 31, "Французский язык", issues);
        checkPair(flags, 13, 33, "Испанский язык", issues);
        return String.join("; ", issues);
    }

    private void checkPair(Map<Integer, Integer> flags, int writtenCode, int oralCode, String subject, List<String> issues) {
        int written = flags.getOrDefault(writtenCode, 0);
        int oral = flags.getOrDefault(oralCode, 0);
        if (written != oral) {
            if (written == 1) issues.add(subject + ": выбрана письменная часть без устной");
            if (oral == 1) issues.add(subject + ": выбрана устная часть без письменной");
        }
    }

    @Transactional
    public void upsertScoreScale(String academicYear, List<OgeDtos.ScoreScaleRow> rows) {
        scoreScaleRepository.deleteAllByAcademicYear(academicYear);
        List<OgeScoreScaleEntry> entities = new ArrayList<>();
        for (OgeDtos.ScoreScaleRow row : rows) {
            for (String subject : OgeSubjects.CORE_SUBJECTS) {
                OgeScoreScaleEntry entry = new OgeScoreScaleEntry();
                entry.setAcademicYear(academicYear);
                entry.setScore(row.score());
                entry.setSubjectName(subject);
                entry.setGrade(row.gradesBySubject().get(subject));
                entities.add(entry);
            }
        }
        scoreScaleRepository.saveAll(entities);
    }

    @Transactional
    public void ensureDefaultScale(String academicYear) {
        if (scoreScaleRepository.countByAcademicYear(academicYear) > 0) return;
        List<OgeDtos.ScoreScaleRow> defaults = OgeDefaultScale.defaultRows();
        upsertScoreScale(academicYear, defaults);
    }

    public List<OgeDtos.ScoreScaleRow> scoreScale(String academicYear) {
        ensureDefaultScale(academicYear);
        Map<Integer, Map<String, Integer>> out = new TreeMap<>();
        for (OgeScoreScaleEntry entry : scoreScaleRepository.findAllByAcademicYearOrderByScoreAscSubjectNameAsc(academicYear)) {
            out.computeIfAbsent(entry.getScore(), k -> new LinkedHashMap<>()).put(entry.getSubjectName(), entry.getGrade());
        }
        return out.entrySet().stream().map(e -> new OgeDtos.ScoreScaleRow(e.getKey(), e.getValue())).toList();
    }

    public List<OgeDtos.EvaluationRow> evaluationRows(String academicYear) {
        ensureDefaultTaskScale(academicYear);
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (OgeTaskScaleEntry e : taskScaleRepository.findAllByAcademicYearOrderBySubjectNameAscTaskNumberAsc(academicYear)) {
            map.computeIfAbsent(e.getSubjectName(), k -> new ArrayList<>());
            map.get(e.getSubjectName()).add(e.getMaxScore());
        }
        return map.entrySet().stream()
                .map(e -> new OgeDtos.EvaluationRow(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public void upsertEvaluationRows(String academicYear, List<OgeDtos.EvaluationRow> rows) {
        taskScaleRepository.deleteAllByAcademicYear(academicYear);
        List<OgeTaskScaleEntry> entities = new ArrayList<>();
        for (OgeDtos.EvaluationRow row : rows) {
            List<Integer> scores = row.maxScores() == null ? List.of() : row.maxScores();
            for (int i = 0; i < scores.size(); i++) {
                Integer max = scores.get(i);
                if (max == null) continue;
                OgeTaskScaleEntry entry = new OgeTaskScaleEntry();
                entry.setAcademicYear(academicYear);
                entry.setSubjectName(row.subject());
                entry.setTaskNumber(i + 1);
                entry.setMaxScore(max);
                entities.add(entry);
            }
        }
        taskScaleRepository.saveAll(entities);
    }

    @Transactional
    public List<OgeDtos.ImportFileResult> importWorks(String academicYear, List<MultipartFile> files, String source) {
        ensureDefaultScale(academicYear);
        ensureDefaultSubjectAliases();
        Map<String, Map<Integer, Integer>> scale = scaleMap(academicYear);
        List<OgeDtos.ImportFileResult> out = new ArrayList<>();
        Set<String> contingentFio9 = loadContingent9Fio(academicYear);
        Map<String, List<ContingentStudent>> contingentByFioClass = loadContingentByFioClass(academicYear);

        for (MultipartFile file : files) {
            String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("work.xlsx");
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                out.add(new OgeDtos.ImportFileResult(fileName, false, "Поддерживается только .xlsx", 0));
                continue;
            }
            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                String sourceKey = normalizeSource(source);
                Sheet data;
                String subject;
                String workType;
                String workDate;
                HeaderPos pos;
                if ("EXTERNAL_TRYOUT".equals(sourceKey)) {
                    data = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                    if (data == null) throw new IllegalArgumentException("Пустой файл");
                    ExternalMeta meta = extractExternalMeta(data);
                    subject = meta.subject();
                    workType = "Промежуточная";
                    workDate = meta.workDate();
                    pos = detectExternalHeader(data);
                    if (pos == null) throw new IllegalArgumentException("Не найдены заголовки внешнего протокола");
                } else {
                    Sheet info = workbook.getSheet("Информация");
                    data = workbook.getSheet("Сбор информации");
                    if (info == null || data == null) throw new IllegalArgumentException("Нет листов 'Информация'/'Сбор информации'");
                    subject = extractSubject(info);
                    if (!OgeSubjects.CORE_SUBJECTS.contains(subject)) throw new IllegalArgumentException("Не определён предмет");
                    workType = "Входная";
                    workDate = "";
                    pos = detectHeader(data);
                    if (pos == null) throw new IllegalArgumentException("Не найдены заголовки ФИО/Итог");
                }
                int count = 0;
                List<String> sumErrors = new ArrayList<>();
                Set<String> unknownFio = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                List<Integer> expectedMaxScores = taskScoresForSubject(academicYear, subject);
                for (int r = pos.dataStartRow; r <= data.getLastRowNum(); r++) {
                    Row row = data.getRow(r);
                    if (row == null) continue;
                    String fio = OgeSubjects.normalizeFio(buildFio(row, pos));
                    if (fio.isBlank()) continue;
                    if (isExternalTotalRow(row, pos)) continue;
                    if (!contingentFio9.contains(normalizeFioCompare(fio))) {
                        unknownFio.add(fio);
                        continue;
                    }
                    Integer score = parseInt(getText(row.getCell(pos.scoreCol)));
                    if (!isMeaningfulResultRow(row, pos, score)) {
                        continue;
                    }
                    String className = "EXTERNAL_TRYOUT".equals(sourceKey)
                            ? OgeSubjects.normalizeClassName(getText(row.getCell(pos.classCol)))
                            : resolveClassFromGia(academicYear, fio);
                    if (className.isBlank()) className = resolveClassFromGia(academicYear, fio);
                    if (className.isBlank()) className = "9";
                    String normalizedClass = normalizeClassLoose(className);

                    Map<Integer, Integer> taskScores = extractTaskScores(row, pos);
                    if (!expectedMaxScores.isEmpty() && taskScores.size() != expectedMaxScores.size()) {
                        sumErrors.add("строка " + (r + 1) + " (" + fio + "): количество заданий " + taskScores.size() + ", ожидается " + expectedMaxScores.size());
                        continue;
                    }
                    int sumByTasks = taskScores.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
                    if (score != null && sumByTasks != score) {
                        sumErrors.add("строка " + (r + 1) + " (" + fio + "): Итог=" + score + ", сумма по заданиям=" + sumByTasks);
                        continue;
                    }

                    Integer grade = "EXTERNAL_TRYOUT".equals(sourceKey)
                            ? parseLeadingDigit(getText(row.getCell(pos.externalMarkCol())))
                            : (score == null ? null : scale.getOrDefault(subject, Map.of()).get(score));
                    if (score != null && grade == null) {
                        throw new IllegalArgumentException("Нет оценки в таблице баллов для предмета '" + subject + "' и балла " + score);
                    }

                    List<ContingentStudent> matchedStudents = contingentByFioClass.getOrDefault(
                            normalizeFioCompare(fio) + "|" + normalizedClass, List.of());
                    String birthDate = "";
                    String snils = "";
                    boolean needsManualStudentMatch = false;
                    String sourceIssue = null;
                    if (matchedStudents.size() == 1) {
                        ContingentStudent matched = matchedStudents.get(0);
                        birthDate = blank(matched.getBirthDate());
                        snils = OgeSubjects.normalizeSnils(matched.getPensionInsurance());
                    } else if (matchedStudents.isEmpty()) {
                        needsManualStudentMatch = true;
                        sourceIssue = "Не найден ученик в контингенте по ФИО+класс";
                    } else {
                        needsManualStudentMatch = true;
                        sourceIssue = "Найдено несколько учеников в контингенте по ФИО+класс";
                    }

                    OgeWorkResult entity = workResultRepository
                            .findByAcademicYearAndFullNameAndBirthDateAndSnilsAndSubjectNameAndWorkSourceAndWorkTypeAndWorkDate(
                                    academicYear, fio, birthDate, snils, subject, sourceKey, workType, workDate)
                            .orElseGet(OgeWorkResult::new);
                    entity.setAcademicYear(academicYear);
                    entity.setClassName(className);
                    entity.setFullName(fio);
                    entity.setBirthDate(birthDate);
                    entity.setSnils(snils);
                    entity.setSubjectName(subject);
                    entity.setWorkSource(sourceKey);
                    entity.setWorkType(workType);
                    entity.setWorkDate(workDate);
                    entity.setTaskScoresJson(toJson(taskScores));
                    if (entity.getId() != null && entity.getTestScore() != null && score == null) {
                        // Не затираем уже рассчитанный итог частично заполненным отчетом.
                    } else {
                        entity.setTestScore(score);
                        entity.setGrade(grade);
                    }
                    entity.setSourceFile(fileName);
                    entity.setUpdatedAt(LocalDateTime.now());
                    if (entity.getTeacherFio() == null || entity.getTeacherFio().isBlank()) {
                        entity.setNeedsTeacherBinding(true);
                    }
                    entity.setNeedsManualStudentMatch(needsManualStudentMatch);
                    entity.setSourceIssue(sourceIssue);
                    if (entity.getTeacherFio() == null || entity.getTeacherFio().isBlank()) {
                        String bindingTeacher = findTeacherBinding(academicYear, className, fio, subject);
                        if (!bindingTeacher.isBlank()) {
                            entity.setTeacherFio(bindingTeacher);
                            entity.setNeedsTeacherBinding(false);
                        }
                    }
                    workResultRepository.save(entity);
                    count++;
                }
                StringBuilder message = new StringBuilder("OK");
                if (!unknownFio.isEmpty()) {
                    message.append(". Пропущены ФИО (нет в контингенте 9-х): ").append(String.join(", ", unknownFio));
                }
                if (!sumErrors.isEmpty()) {
                    message.append(". Ошибки по сумме заданий/итога: ").append(String.join(" | ", sumErrors));
                }
                out.add(new OgeDtos.ImportFileResult(fileName, true, message.toString(), count));
            } catch (Exception e) {
                out.add(new OgeDtos.ImportFileResult(fileName, false, e.getMessage(), 0));
            }
        }
        return out;
    }

    public OgeDtos.WorkDatasetResponse workDataset(String academicYear, String source) {
        String sourceKey = normalizeSource(source);
        OgeGiaVersion latest = latestVersion(academicYear);
        List<OgeWorkResult> work = workResultRepository.findAllByAcademicYearOrderByClassNameAscFullNameAscSubjectNameAsc(academicYear).stream()
                .filter(w -> sourceKey.equalsIgnoreCase(blank(w.getWorkSource())))
                .toList();
        Map<String, OgeWorkResult> workByKey = work.stream().collect(Collectors.toMap(
                w -> key(w.getFullName(), w.getSubjectName()), Function.identity(), (a, b) -> b, LinkedHashMap::new));
        Map<String, String> teacherByKey = teacherBindingRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAscFullNameAsc(academicYear).stream()
                .collect(Collectors.toMap(b -> key(b.getFullName(), b.getSubjectName()), b -> blank(b.getTeacherFio()), (a, b) -> b, LinkedHashMap::new));

        Map<String, OgeGiaParticipant> expected = new LinkedHashMap<>();
        if (latest != null) {
            for (OgeGiaParticipant p : latest.getParticipants()) {
                for (String subject : p.getSelectedSubjects()) {
                    expected.put(key(p.getFullName(), subject), p);
                }
            }
        }

        List<OgeDtos.WorkResultRow> resultRows = new ArrayList<>();
        for (OgeWorkResult wr : work) {
            boolean expectedNow = expected.containsKey(key(wr.getFullName(), wr.getSubjectName()));
            String status = "ok";
            if (!expectedNow) status = "gray";
            else if (wr.getGrade() != null && wr.getGrade() == 2) status = "red";
            String teacher = blank(wr.getTeacherFio());
            if (teacher.isBlank()) teacher = blank(teacherByKey.get(key(wr.getFullName(), wr.getSubjectName())));
            if (wr.getTestScore() != null && teacher.isBlank()) status = "orange";
            resultRows.add(new OgeDtos.WorkResultRow(
                    wr.getClassName(), wr.getFullName(), wr.getSubjectName(), wr.getTestScore(), wr.getGrade(), expectedNow, status,
                    teacher, wr.isNeedsTeacherBinding(), wr.isNeedsManualStudentMatch(), blank(wr.getWorkSource()), blank(wr.getWorkType()), blank(wr.getWorkDate())));
        }

        List<OgeDtos.WorkResultRow> missing = expected.entrySet().stream()
                .filter(e -> !workByKey.containsKey(e.getKey()))
                .map(e -> new OgeDtos.WorkResultRow(e.getValue().getClassName(), e.getValue().getFullName(), splitKeySubject(e.getKey()), null, null, true, "yellow", blank(teacherByKey.get(e.getKey())), true, false, sourceKey, "", ""))
                .sorted(Comparator.comparing(OgeDtos.WorkResultRow::subject).thenComparing(OgeDtos.WorkResultRow::className).thenComparing(OgeDtos.WorkResultRow::fullName))
                .toList();
        resultRows.addAll(missing);

        Map<String, Map<String, int[]>> agg = new TreeMap<>();
        for (OgeWorkResult wr : work) {
            if (wr.getGrade() == null || wr.getGrade() < 2 || wr.getGrade() > 5) continue;
            agg.computeIfAbsent(wr.getClassName(), k -> new TreeMap<>())
                    .computeIfAbsent(wr.getSubjectName(), k -> new int[6])[wr.getGrade()]++;
        }
        List<OgeDtos.WorkStatsRow> stats = new ArrayList<>();
        agg.forEach((cls, bySub) -> bySub.forEach((sub, counts) -> stats.add(new OgeDtos.WorkStatsRow(cls, sub, counts[2], counts[3], counts[4], counts[5]))));

        List<String> errors = work.stream().map(OgeWorkResult::getSourceIssue)
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
        return new OgeDtos.WorkDatasetResponse(resultRows, missing, stats, errors);
    }

    public byte[] exportWorksWorkbook(String academicYear, String source) throws IOException {
        OgeDtos.WorkDatasetResponse data = workDataset(academicYear, source);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet results = wb.createSheet("Результаты");
            writeWorkResults(results, data.results());
            Sheet missing = wb.createSheet("Не сдали");
            writeMissing(missing, data.missing());
            Sheet stats = wb.createSheet("Статистика");
            writeStats(stats, data.statistics());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    public List<String> teachers() {
        return teacherDirectoryRepository.findAll().stream()
                .map(TeacherDirectoryEntry::getFioTeacher)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<OgeDtos.TeacherBindingRow> teacherBindings(String academicYear, boolean onlyUnbound) {
        OgeGiaVersion latest = latestVersion(academicYear);
        if (latest == null) return List.of();
        Map<String, String> existing = teacherBindingRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAscFullNameAsc(academicYear).stream()
                .collect(Collectors.toMap(b -> bindingKey(b.getClassName(), b.getFullName(), b.getSubjectName()), b -> blank(b.getTeacherFio()), (a, b) -> b));
        return latest.getParticipants().stream()
                .flatMap(p -> p.getSelectedSubjects().stream()
                        .map(subject -> new OgeDtos.TeacherBindingRow(blank(p.getClassName()), p.getFullName(), subject,
                                blank(existing.get(bindingKey(p.getClassName(), p.getFullName(), subject))))))
                .filter(r -> !onlyUnbound || blank(r.teacherFio()).isBlank())
                .sorted(Comparator.comparing(OgeDtos.TeacherBindingRow::className).thenComparing(OgeDtos.TeacherBindingRow::subject).thenComparing(OgeDtos.TeacherBindingRow::fullName))
                .toList();
    }

    @Transactional
    public void updateTeacherBindings(String academicYear, List<OgeDtos.TeacherBindingUpdate> updates) {
        for (OgeDtos.TeacherBindingUpdate update : updates) {
            String className = OgeSubjects.normalizeClassName(update.className());
            String fullName = OgeSubjects.normalizeFio(update.fullName());
            String subject = blank(update.subject());
            if (className.isBlank() || fullName.isBlank() || subject.isBlank()) continue;
            OgeTeacherBinding binding = teacherBindingRepository
                    .findByAcademicYearAndClassNameAndFullNameAndSubjectName(academicYear, className, fullName, subject)
                    .orElseGet(OgeTeacherBinding::new);
            binding.setAcademicYear(academicYear);
            binding.setClassName(className);
            binding.setFullName(fullName);
            binding.setSubjectName(subject);
            binding.setTeacherFio(blank(update.teacherFio()).trim());
            binding.setUpdatedAt(LocalDateTime.now());
            teacherBindingRepository.save(binding);
        }
        List<OgeWorkResult> rows = workResultRepository.findAllByAcademicYearOrderByClassNameAscFullNameAscSubjectNameAsc(academicYear);
        for (OgeWorkResult row : rows) {
            String teacher = findTeacherBinding(academicYear, row.getClassName(), row.getFullName(), row.getSubjectName());
            row.setTeacherFio(teacher);
            row.setNeedsTeacherBinding(teacher.isBlank());
        }
        workResultRepository.saveAll(rows);
    }

    @Transactional
    public int bindTeachersFromLoad(String academicYear) {
        ensureDefaultSubjectAliases();
        Map<String, List<String>> byClassSubject = new HashMap<>();
        for (ManualLoadEntry entry : manualLoadEntryRepository.findAllByAcademicYear(academicYear)) {
            String cls = normalizeClassLoose(entry.getClassName());
            String sub = normalizeSubjectKey(mapSubjectForLoad(entry.getSubjectName()));
            String key = cls + "|" + sub;
            byClassSubject.computeIfAbsent(key, k -> new ArrayList<>()).add(blank(entry.getFioTeacher()).trim());
        }
        int updated = 0;
        for (OgeDtos.TeacherBindingRow row : teacherBindings(academicYear, false)) {
            String cls = normalizeClassLoose(row.className());
            String sub = normalizeSubjectKey(mapSubjectForLoad(row.subject()));
            List<String> teachers = byClassSubject.getOrDefault(cls + "|" + sub, List.of()).stream()
                    .filter(s -> !s.isBlank()).distinct().toList();
            if (teachers.size() == 1) {
                OgeTeacherBinding binding = teacherBindingRepository
                        .findByAcademicYearAndClassNameAndFullNameAndSubjectName(academicYear, row.className(), row.fullName(), row.subject())
                        .orElseGet(OgeTeacherBinding::new);
                binding.setAcademicYear(academicYear);
                binding.setClassName(row.className());
                binding.setFullName(row.fullName());
                binding.setSubjectName(row.subject());
                binding.setTeacherFio(teachers.get(0));
                binding.setUpdatedAt(LocalDateTime.now());
                teacherBindingRepository.save(binding);
                updated++;
            }
        }
        List<OgeWorkResult> rows = workResultRepository.findAllByAcademicYearOrderByClassNameAscFullNameAscSubjectNameAsc(academicYear);
        for (OgeWorkResult row : rows) {
            String teacher = findTeacherBinding(academicYear, row.getClassName(), row.getFullName(), row.getSubjectName());
            row.setTeacherFio(teacher);
            row.setNeedsTeacherBinding(teacher.isBlank());
        }
        workResultRepository.saveAll(rows);
        return updated;
    }

    public byte[] exportMismatchesWorkbook(String academicYear) throws IOException {
        OgeDtos.GiaMismatchResponse mismatches = giaMismatches(academicYear);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Нестыковки");
            writeMismatches(sheet, mismatches.rows());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    public OgeDtos.GiaMismatchResponse giaMismatches(String academicYear) {
        OgeGiaVersion latestGia = latestVersion(academicYear);
        ContingentSnapshot snapshot = contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElse(null);
        if (latestGia == null && snapshot == null) {
            return new OgeDtos.GiaMismatchResponse(List.of(), "Пусто: нет выгрузки ГИА и нет снимка контингента за " + academicYear + ".");
        }
        if (latestGia == null) {
            return new OgeDtos.GiaMismatchResponse(List.of(), "Пусто: нет выгрузки ГИА за " + academicYear + ".");
        }
        if (snapshot == null) {
            return new OgeDtos.GiaMismatchResponse(List.of(), "Пусто: нет снимка контингента за " + academicYear + ".");
        }

        List<ContingentStudent> ninth = contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(s -> normalizeClassLoose(s.getClassName()).startsWith("9"))
                .toList();

        Map<String, OgeGiaParticipant> giaByFioClass = latestGia.getParticipants().stream()
                .filter(p -> normalizeClassLoose(p.getClassName()).startsWith("9"))
                .collect(Collectors.toMap(
                        p -> normalizeFioCompare(p.getFullName()) + "|" + normalizeClassLoose(p.getClassName()),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        Map<String, ContingentStudent> contByFioClass = ninth.stream()
                .collect(Collectors.toMap(
                        c -> normalizeFioCompare(c.getFullName()) + "|" + normalizeClassLoose(c.getClassName()),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<OgeDtos.GiaMismatchRow> rows = new ArrayList<>();
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(giaByFioClass.keySet());
        allKeys.addAll(contByFioClass.keySet());
        for (String key : allKeys) {
            OgeGiaParticipant g = giaByFioClass.get(key);
            ContingentStudent c = contByFioClass.get(key);
            if (g == null) {
                rows.add(new OgeDtos.GiaMismatchRow("ОГЭ/Нестыковка", c.getClassName(), "", c.getFullName(), "", c.getPassport(), "Не найден в выгрузке ГИА"));
                continue;
            }
            if (c == null) {
                rows.add(new OgeDtos.GiaMismatchRow("ОГЭ/Нестыковка", g.getClassName(), g.getFullName(), "", g.getDocument(), "", "Не найден в контингенте"));
                continue;
            }
            String docG = normalizePassport(g.getDocument());
            String docC = normalizePassport(c.getPassport());
            if (!docG.equals(docC)) {
                rows.add(new OgeDtos.GiaMismatchRow("ОГЭ/Нестыковка", g.getClassName(), g.getFullName(), c.getFullName(), g.getDocument(), c.getPassport(), "Не совпадает документ"));
            }
        }

        Map<String, OgeGiaParticipant> giaByDocClass = latestGia.getParticipants().stream()
                .filter(p -> normalizeClassLoose(p.getClassName()).startsWith("9"))
                .filter(p -> !normalizePassport(p.getDocument()).isBlank())
                .collect(Collectors.toMap(
                        p -> normalizePassport(p.getDocument()) + "|" + normalizeClassLoose(p.getClassName()),
                        Function.identity(),
                        (a, b) -> a
                ));
        for (ContingentStudent c : ninth) {
            String key = normalizePassport(c.getPassport()) + "|" + normalizeClassLoose(c.getClassName());
            OgeGiaParticipant g = giaByDocClass.get(key);
            if (g != null && !normalizeFioCompare(g.getFullName()).equals(normalizeFioCompare(c.getFullName()))) {
                rows.add(new OgeDtos.GiaMismatchRow("ОГЭ/Нестыковка", c.getClassName(), g.getFullName(), c.getFullName(), g.getDocument(), c.getPassport(), "Не совпадает ФИО"));
            }
        }
        return new OgeDtos.GiaMismatchResponse(rows, rows.isEmpty() ? "Нестыковки не найдены." : "");
    }

    public byte[] exportGiaWorkbook(String academicYear) throws IOException {
        List<OgeDtos.GiaParticipantView> participants = latestParticipants(academicYear);
        OgeDtos.GiaChangesResponse changes = changesBetweenLastTwo(academicYear);
        OgeDtos.GiaStatsResponse stats = giaStats(academicYear);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet who = wb.createSheet("Кто что сдаёт");
            Row h = who.createRow(0);
            h.createCell(0).setCellValue("Класс");
            h.createCell(1).setCellValue("ФИО");
            h.createCell(2).setCellValue("Предметы");
            int r = 1;
            for (OgeDtos.GiaParticipantView p : participants) {
                Row row = who.createRow(r++);
                row.createCell(0).setCellValue(p.className());
                row.createCell(1).setCellValue(p.fullName());
                row.createCell(2).setCellValue(String.join(", ", p.selectedSubjects()));
            }

            Sheet changesSheet = wb.createSheet("Изменения");
            Row hc = changesSheet.createRow(0);
            hc.createCell(0).setCellValue("Тип");
            hc.createCell(1).setCellValue("Ключ");
            hc.createCell(2).setCellValue("Было");
            hc.createCell(3).setCellValue("Стало");
            int rc = 1;
            for (OgeDtos.GiaChangeItem item : changes.changes()) {
                Row row = changesSheet.createRow(rc++);
                row.createCell(0).setCellValue(item.type());
                row.createCell(1).setCellValue(item.key());
                row.createCell(2).setCellValue(item.wasValue());
                row.createCell(3).setCellValue(item.becameValue());
            }

            Sheet statsSheet = wb.createSheet("Статистика GIA");
            Row hs = statsSheet.createRow(0);
            hs.createCell(0).setCellValue("Класс");
            int c = 1;
            for (String subject : stats.subjects()) hs.createCell(c++).setCellValue(subject);
            int rowNum = 1;
            for (OgeDtos.GiaClassStatsRow row : stats.classes()) {
                Row rr = statsSheet.createRow(rowNum++);
                rr.createCell(0).setCellValue(row.className());
                c = 1;
                for (String subject : stats.subjects()) rr.createCell(c++).setCellValue(row.counts().getOrDefault(subject, 0));
            }

            Row total = statsSheet.createRow(rowNum++);
            total.createCell(0).setCellValue("ИТОГО");
            c = 1;
            for (String subject : stats.subjects()) total.createCell(c++).setCellValue(stats.totalsBySubject().getOrDefault(subject, 0));

            Row distTitle = statsSheet.createRow(rowNum + 1);
            distTitle.createCell(0).setCellValue("Распределение по числу экзаменов");
            int d = rowNum + 2;
            for (Map.Entry<Integer, Integer> e : stats.examCountDistribution().entrySet()) {
                Row dr = statsSheet.createRow(d++);
                dr.createCell(0).setCellValue(e.getKey() + " экзаменов");
                dr.createCell(1).setCellValue(e.getValue());
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private void writeWorkResults(Sheet sheet, List<OgeDtos.WorkResultRow> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Класс");
        h.createCell(1).setCellValue("ФИО");
        h.createCell(2).setCellValue("Предмет");
        h.createCell(3).setCellValue("Балл");
        h.createCell(4).setCellValue("Оценка");
        h.createCell(5).setCellValue("Статус");
        Workbook wb = sheet.getWorkbook();
        CellStyle red = color(wb, IndexedColors.ROSE.getIndex());
        CellStyle gray = color(wb, IndexedColors.GREY_25_PERCENT.getIndex());
        int r = 1;
        for (OgeDtos.WorkResultRow row : rows) {
            Row rr = sheet.createRow(r++);
            rr.createCell(0).setCellValue(row.className());
            rr.createCell(1).setCellValue(row.fullName());
            rr.createCell(2).setCellValue(row.subject());
            if (row.score() != null) rr.createCell(3).setCellValue(row.score());
            if (row.grade() != null) rr.createCell(4).setCellValue(row.grade());
            rr.createCell(5).setCellValue(row.status());
            if ("red".equals(row.status())) {
                rr.getCell(3).setCellStyle(red);
                rr.getCell(4).setCellStyle(red);
            } else if ("gray".equals(row.status())) {
                rr.getCell(3).setCellStyle(gray);
                rr.getCell(4).setCellStyle(gray);
            }
        }
    }

    private void writeMissing(Sheet sheet, List<OgeDtos.WorkResultRow> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Предмет");
        h.createCell(1).setCellValue("Класс");
        h.createCell(2).setCellValue("ФИО");
        int r = 1;
        for (OgeDtos.WorkResultRow row : rows) {
            Row rr = sheet.createRow(r++);
            rr.createCell(0).setCellValue(row.subject());
            rr.createCell(1).setCellValue(row.className());
            rr.createCell(2).setCellValue(row.fullName());
        }
    }

    private void writeStats(Sheet sheet, List<OgeDtos.WorkStatsRow> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Класс");
        h.createCell(1).setCellValue("Предмет");
        h.createCell(2).setCellValue("Двоек");
        h.createCell(3).setCellValue("Троек");
        h.createCell(4).setCellValue("Четверок");
        h.createCell(5).setCellValue("Пятерок");
        int r = 1;
        for (OgeDtos.WorkStatsRow row : rows) {
            Row rr = sheet.createRow(r++);
            rr.createCell(0).setCellValue(row.className());
            rr.createCell(1).setCellValue(row.subject());
            rr.createCell(2).setCellValue(row.count2());
            rr.createCell(3).setCellValue(row.count3());
            rr.createCell(4).setCellValue(row.count4());
            rr.createCell(5).setCellValue(row.count5());
        }
    }

    private void writeMismatches(Sheet sheet, List<OgeDtos.GiaMismatchRow> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Тип");
        h.createCell(1).setCellValue("Класс");
        h.createCell(2).setCellValue("ФИО (ГИА)");
        h.createCell(3).setCellValue("ФИО (Контингент)");
        h.createCell(4).setCellValue("Документ (ГИА)");
        h.createCell(5).setCellValue("Документ (Контингент)");
        h.createCell(6).setCellValue("Причина");
        int r = 1;
        for (OgeDtos.GiaMismatchRow row : rows) {
            Row rr = sheet.createRow(r++);
            rr.createCell(0).setCellValue(row.type());
            rr.createCell(1).setCellValue(row.className());
            rr.createCell(2).setCellValue(row.fioGia());
            rr.createCell(3).setCellValue(row.fioContingent());
            rr.createCell(4).setCellValue(row.documentGia());
            rr.createCell(5).setCellValue(row.documentContingent());
            rr.createCell(6).setCellValue(row.reason());
        }
    }

    private CellStyle color(Workbook wb, short color) {
        CellStyle style = wb.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFillForegroundColor(color);
        return style;
    }

    private OgeGiaVersion latestVersion(String academicYear) {
        return giaVersionRepository.findTop2ByAcademicYearOrderByUploadedAtDescIdDesc(academicYear).stream().findFirst().orElse(null);
    }

    private String resolveClassFromGia(String academicYear, String fio) {
        OgeGiaVersion latest = latestVersion(academicYear);
        if (latest == null) return "";
        return latest.getParticipants().stream()
                .filter(p -> OgeSubjects.normalizeFio(p.getFullName()).equalsIgnoreCase(fio))
                .map(OgeGiaParticipant::getClassName)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");
    }

    private String findTeacherBinding(String academicYear, String className, String fullName, String subject) {
        return teacherBindingRepository.findByAcademicYearAndClassNameAndFullNameAndSubjectName(
                        academicYear, OgeSubjects.normalizeClassName(className), OgeSubjects.normalizeFio(fullName), blank(subject))
                .map(OgeTeacherBinding::getTeacherFio)
                .map(this::blank)
                .orElse("");
    }

    private String bindingKey(String className, String fullName, String subject) {
        return OgeSubjects.normalizeClassName(className) + "|" + OgeSubjects.normalizeFio(fullName).toUpperCase(Locale.ROOT) + "|" + blank(subject);
    }

    private Set<String> loadContingent9Fio(String academicYear) {
        ContingentSnapshot snapshot = contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElse(null);
        if (snapshot == null) {
            throw new IllegalStateException("Нет снимка контингента за учебный год " + academicYear + ". Загрузите контингент этого года.");
        }
        return contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(s -> normalizeClassLoose(s.getClassName()).startsWith("9"))
                .map(ContingentStudent::getFullName)
                .map(this::normalizeFioCompare)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    private Map<String, List<ContingentStudent>> loadContingentByFioClass(String academicYear) {
        ContingentSnapshot snapshot = contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElse(null);
        if (snapshot == null) return Map.of();
        return contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(s -> normalizeClassLoose(s.getClassName()).startsWith("9"))
                .collect(Collectors.groupingBy(
                        s -> normalizeFioCompare(s.getFullName()) + "|" + normalizeClassLoose(s.getClassName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private String extractSubject(Sheet info) {
        Row row = info.getRow(2);
        if (row == null) return null;
        String text = getText(row.getCell(1)).toLowerCase(Locale.ROOT);
        for (String subject : OgeSubjects.CORE_SUBJECTS) {
            if (text.contains(subject.toLowerCase(Locale.ROOT))) return subject;
        }
        return null;
    }

    private HeaderPos detectHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) return null;
        int fioCol = -1;
        int classCol = -1;
        int presenceCol = -1;
        int variantCol = -1;
        int scoreCol = -1;
        for (int c = 0; c <= Math.min(80, header.getLastCellNum()); c++) {
            String valRaw = getText(header.getCell(c)).toLowerCase(Locale.ROOT);
            String val = valRaw.replaceAll("\\s+", "");
            if (valRaw.contains("фио")) fioCol = c;
            if (valRaw.contains("класс")) classCol = c;
            if (valRaw.contains("присутств")) presenceCol = c;
            if (valRaw.contains("вариант")) variantCol = c;
            if ("итог".equals(val) || "итого".equals(val)) scoreCol = c;
        }
        return fioCol >= 0 && scoreCol >= 0 ? new HeaderPos(fioCol, classCol < 0 ? 0 : classCol, presenceCol, variantCol, scoreCol, 3) : null;
    }

    private HeaderPos detectExternalHeader(Sheet sheet) {
        for (int r = 0; r <= Math.min(30, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int numberCol = -1, classCol = -1, fioCol = -1, scoreCol = -1, markCol = -1;
            int lastNameCol = -1, firstNameCol = -1, middleNameCol = -1;
            int shortCol = -1, detailedCol = -1;
            for (int c = 0; c <= row.getLastCellNum(); c++) {
                String raw = getText(row.getCell(c)).trim().toLowerCase(Locale.ROOT);
                if (raw.startsWith("№")) numberCol = c;
                if (raw.equals("класс")) classCol = c;
                if (raw.contains("фио")) fioCol = c;
                if (raw.equals("фамилия")) lastNameCol = c;
                if (raw.equals("имя")) firstNameCol = c;
                if (raw.equals("отчество")) middleNameCol = c;
                if (raw.contains("кратким ответ")) shortCol = c;
                if (raw.contains("развернут")) detailedCol = c;
                if (raw.contains("первичный балл")) scoreCol = c;
                if (raw.equals("отметка")) markCol = c;
            }
            if (numberCol >= 0 && classCol >= 0 && scoreCol >= 0 && markCol >= 0 && shortCol >= 0 && detailedCol >= 0) {
                if (fioCol < 0 && lastNameCol >= 0 && firstNameCol >= 0) fioCol = lastNameCol;
                if (fioCol < 0) continue;
                return new HeaderPos(fioCol, classCol, -1, shortCol, scoreCol, r + 1, firstNameCol, middleNameCol, detailedCol, markCol);
            }
        }
        return null;
    }

    private ExternalMeta extractExternalMeta(Sheet sheet) {
        String subject = null;
        String date = "";
        for (int r = 0; r <= Math.min(20, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String line = getText(row.getCell(0));
            if (line.isBlank()) continue;
            String normalized = normalizeSubject(line);
            if (normalized != null) subject = normalized;
            Matcher m = EXTERNAL_DATE_PATTERN.matcher(line);
            if (m.find()) date = m.group();
            if (subject != null && !date.isBlank()) break;
        }
        if (subject == null) throw new IllegalArgumentException("Не удалось определить предмет внешнего протокола");
        return new ExternalMeta(subject, date);
    }

    private String normalizeSubject(String text) {
        String low = blank(text).toLowerCase(Locale.ROOT);
        for (String subject : OgeSubjects.CORE_SUBJECTS) {
            if (low.contains(subject.toLowerCase(Locale.ROOT))) return subject;
        }
        if (low.contains("информатика")) return "Информатика и ИКТ";
        return null;
    }

    private boolean isMeaningfulResultRow(Row row, HeaderPos pos, Integer score) {
        if (pos.external()) return score != null;
        String presence = pos.presenceCol >= 0 ? getText(row.getCell(pos.presenceCol)).trim().toLowerCase(Locale.ROOT) : "";
        boolean variantFilled = pos.variantCol >= 0 && !getText(row.getCell(pos.variantCol)).trim().isEmpty();
        boolean taskPointsFilled = hasTaskPoints(row, pos);

        if (score != null && score > 0) return true;
        if ((presence.contains("не был") || presence.contains("отсутств")) && !variantFilled && !taskPointsFilled) return false;
        if (presence.isBlank() && !variantFilled && !taskPointsFilled && (score == null || score == 0)) return false;
        if (score != null && score == 0) return taskPointsFilled;
        return variantFilled || taskPointsFilled;
    }

    private boolean hasTaskPoints(Row row, HeaderPos pos) {
        if (pos.scoreCol < 0) return false;
        int start = Math.max(0, Math.min(pos.fioCol, pos.scoreCol) + 1);
        int end = Math.max(pos.fioCol, pos.scoreCol) - 1;
        for (int c = start; c <= end; c++) {
            if (c == pos.classCol || c == pos.presenceCol || c == pos.variantCol) continue;
            String value = getText(row.getCell(c)).trim();
            if (!value.isEmpty() && !"0".equals(value)) return true;
        }
        return false;
    }

    private Map<String, Map<Integer, Integer>> scaleMap(String academicYear) {
        Map<String, Map<Integer, Integer>> map = new LinkedHashMap<>();
        for (String s : OgeSubjects.CORE_SUBJECTS) map.put(s, new HashMap<>());
        for (OgeScoreScaleEntry e : scoreScaleRepository.findAllByAcademicYearOrderByScoreAscSubjectNameAsc(academicYear)) {
            map.computeIfAbsent(e.getSubjectName(), k -> new HashMap<>()).put(e.getScore(), e.getGrade());
        }
        return map;
    }

    private Map<Integer, Integer> extractTaskScores(Row row, HeaderPos pos) {
        if (pos.external()) {
            return parseExternalTaskScores(row, pos);
        }
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        int taskIndex = 1;
        for (int c = pos.variantCol + 1; c < pos.scoreCol; c++) {
            if (c == pos.classCol || c == pos.presenceCol || c == pos.variantCol) continue;
            scores.put(taskIndex++, parseInt(getText(row.getCell(c))));
        }
        return scores;
    }

    private Map<Integer, Integer> parseExternalTaskScores(Row row, HeaderPos pos) {
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        int idx = 1;
        String shortAnswers = getText(row.getCell(pos.variantCol));
        for (char ch : shortAnswers.toCharArray()) {
            if (ch == '+' || ch == '-') {
                scores.put(idx++, ch == '+' ? 1 : 0);
            }
        }
        String detailed = getText(row.getCell(pos.externalDetailedCol()));
        Matcher m = Pattern.compile("(\\d+)\\s*\\(").matcher(detailed);
        while (m.find()) {
            scores.put(idx++, parseInt(m.group(1)));
        }
        return scores;
    }

    private String buildFio(Row row, HeaderPos pos) {
        if (!pos.external()) return getText(row.getCell(pos.fioCol));
        if (pos.fioCol() >= 0 && pos.externalFirstNameCol() < 0) return getText(row.getCell(pos.fioCol()));
        String ln = getText(row.getCell(pos.fioCol())).trim();
        String fn = getText(row.getCell(pos.externalFirstNameCol())).trim();
        String mn = pos.externalMiddleNameCol() >= 0 ? getText(row.getCell(pos.externalMiddleNameCol())).trim() : "";
        return (ln + " " + fn + " " + mn).trim();
    }

    private boolean isExternalTotalRow(Row row, HeaderPos pos) {
        if (!pos.external()) return false;
        String first = getText(row.getCell(0)).toLowerCase(Locale.ROOT);
        return first.contains("итог") || first.contains("всего");
    }

    private String toJson(Map<Integer, Integer> taskScores) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, Integer> e : taskScores.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            sb.append(e.getValue() == null ? "null" : e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private void ensureDefaultSubjectAliases() {
        ensureSubjectAlias("LOAD", "Математика", "Алгебра");
        ensureSubjectAlias("LOAD", "Информатика и ИКТ", "Информатика");
    }

    private void ensureDefaultTaskScale(String academicYear) {
        if (taskScaleRepository.countByAcademicYear(academicYear) > 0) return;
        List<OgeTaskScaleEntry> entities = new ArrayList<>();
        MANUAL_MAX_SCORES.forEach((subject, scores) -> {
            String normalizedSubject = normalizeSubject(subject);
            String title = normalizedSubject == null ? capitalizeSubject(subject) : normalizedSubject;
            for (int i = 0; i < scores.size(); i++) {
                OgeTaskScaleEntry entry = new OgeTaskScaleEntry();
                entry.setAcademicYear(academicYear);
                entry.setSubjectName(title);
                entry.setTaskNumber(i + 1);
                entry.setMaxScore(scores.get(i));
                entities.add(entry);
            }
        });
        taskScaleRepository.saveAll(entities);
    }

    private List<Integer> taskScoresForSubject(String academicYear, String subject) {
        ensureDefaultTaskScale(academicYear);
        return taskScaleRepository.findAllByAcademicYearOrderBySubjectNameAscTaskNumberAsc(academicYear).stream()
                .filter(e -> normalizeSubjectKey(e.getSubjectName()).equals(normalizeSubjectKey(subject)))
                .sorted(Comparator.comparing(OgeTaskScaleEntry::getTaskNumber))
                .map(OgeTaskScaleEntry::getMaxScore)
                .toList();
    }

    private void ensureSubjectAlias(String scope, String source, String target) {
        subjectAliasRepository.findByScopeAndSourceNameIgnoreCase(scope, source).orElseGet(() -> {
            SubjectAlias alias = new SubjectAlias();
            alias.setScope(scope);
            alias.setSourceName(source);
            alias.setTargetName(target);
            alias.setActive(true);
            return subjectAliasRepository.save(alias);
        });
    }

    private String mapSubjectForLoad(String subjectName) {
        if (subjectName == null) return "";
        return subjectAliasRepository.findAllByScopeAndActiveTrue("LOAD").stream()
                .filter(a -> a.getSourceName().equalsIgnoreCase(subjectName))
                .map(SubjectAlias::getTargetName)
                .findFirst()
                .orElse(subjectName);
    }

    private String normalizeSubjectKey(String subjectName) {
        return blank(subjectName).trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private String capitalizeSubject(String subject) {
        String s = blank(subject);
        if (s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private String normalizeSource(String source) {
        return "EXTERNAL_TRYOUT".equalsIgnoreCase(source) ? "EXTERNAL_TRYOUT" : "INTERNAL";
    }

    private Map<String, OgeGiaParticipant> indexByKey(List<OgeGiaParticipant> participants) {
        Map<String, OgeGiaParticipant> map = new LinkedHashMap<>();
        for (OgeGiaParticipant p : participants) {
            map.put(uniqueKey(p), p);
        }
        return map;
    }

    private String uniqueKey(OgeGiaParticipant p) {
        String snils = blank(p.getSnils());
        if (!snils.isBlank()) return "SNILS:" + snils;
        return "FIO_CLASS:" + OgeSubjects.normalizeFio(p.getFullName()).toUpperCase(Locale.ROOT) + "|" + blank(p.getClassName());
    }

    private String displayKey(OgeGiaParticipant p) {
        return blank(p.getClassName()) + " | " + p.getFullName();
    }

    private String toSubjects(OgeGiaParticipant p) {
        return p.getSelectedSubjects().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    private String key(String fio, String subject) {
        return OgeSubjects.normalizeFio(fio).toUpperCase(Locale.ROOT) + "|" + subject;
    }

    private String splitKeySubject(String key) {
        String[] parts = key.split("\\|");
        return parts.length >= 2 ? parts[1] : "";
    }

    private boolean isEmptyRow(Row row, int start, int end) {
        for (int c = start; c <= end; c++) {
            if (!getText(row.getCell(c)).isBlank()) return false;
        }
        return true;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String normalizeClassLoose(String className) {
        return blank(className).replaceAll("[^А-Яа-яA-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeFioCompare(String fio) {
        return OgeSubjects.normalizeFio(blank(fio))
                .replace('Ё', 'Е')
                .replace('ё', 'е')
                .toLowerCase(Locale.ROOT);
    }

    private String normalizePassport(String value) {
        if (value == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(value);
        List<String> parts = new ArrayList<>();
        while (m.find() && parts.size() < 2) {
            parts.add(m.group());
        }
        return String.join(" ", parts);
    }

    private Integer parseInt(String text) {
        String value = blank(text).trim().replace(',', '.');
        if (value.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseLeadingDigit(String text) {
        String v = blank(text).trim();
        Matcher m = Pattern.compile("([2-5])").matcher(v);
        if (m.find()) return parseInt(m.group(1));
        return null;
    }

    private String getText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) yield String.valueOf((int) v);
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                CellType type = cell.getCachedFormulaResultType();
                if (type == CellType.STRING) yield cell.getStringCellValue().trim();
                if (type == CellType.NUMERIC) {
                    double v = cell.getNumericCellValue();
                    if (v == Math.floor(v)) yield String.valueOf((int) v);
                    yield String.valueOf(v);
                }
                yield "";
            }
            default -> "";
        };
    }

    private record HeaderPos(int fioCol, int classCol, int presenceCol, int variantCol, int scoreCol,
                             int dataStartRow, int externalFirstNameCol, int externalMiddleNameCol,
                             int externalDetailedCol, int externalMarkCol) {
        private HeaderPos(int fioCol, int classCol, int presenceCol, int variantCol, int scoreCol, int dataStartRow) {
            this(fioCol, classCol, presenceCol, variantCol, scoreCol, dataStartRow, -1, -1, -1, -1);
        }

        private boolean external() {
            return externalMarkCol >= 0;
        }
    }

    private record ExternalMeta(String subject, String workDate) {}
}
