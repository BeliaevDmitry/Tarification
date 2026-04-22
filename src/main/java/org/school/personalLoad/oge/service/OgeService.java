package org.school.personalLoad.oge.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.oge.dto.OgeDtos;
import org.school.personalLoad.oge.model.OgeGiaParticipant;
import org.school.personalLoad.oge.model.OgeGiaVersion;
import org.school.personalLoad.oge.model.OgeScoreScaleEntry;
import org.school.personalLoad.oge.model.OgeWorkResult;
import org.school.personalLoad.oge.repository.OgeGiaVersionRepository;
import org.school.personalLoad.oge.repository.OgeScoreScaleRepository;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OgeService {
    private final OgeGiaVersionRepository giaVersionRepository;
    private final OgeWorkResultRepository workResultRepository;
    private final OgeScoreScaleRepository scoreScaleRepository;

    @Transactional
    public List<OgeDtos.ImportFileResult> importGia(List<MultipartFile> files) {
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
                    p.setClassName(OgeSubjects.normalizeClassName(getText(row.getCell(5))));
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

    public List<OgeDtos.GiaVersionView> versions() {
        return giaVersionRepository.findAllByOrderByUploadedAtDescIdDesc().stream()
                .map(v -> new OgeDtos.GiaVersionView(v.getId(), v.getSourceFileName(), v.getUploadedAt(), v.getParticipants().size()))
                .toList();
    }

    public List<OgeDtos.GiaParticipantView> latestParticipants() {
        OgeGiaVersion latest = latestVersion();
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

    public OgeDtos.GiaStatsResponse giaStats() {
        OgeGiaVersion latest = latestVersion();
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

    public OgeDtos.GiaChangesResponse changesBetweenLastTwo() {
        List<OgeGiaVersion> versions = giaVersionRepository.findTop2ByOrderByUploadedAtDescIdDesc();
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
            Set<String> s = p.getSelectedSubjects();
            if (s.contains("Английский язык") && p.getSelectedSubjects().contains("Английский язык")) {
                // объединено в один предмет, явную рассинхронизацию по коду не можем восстановить после нормализации
            }
        }
        return errors;
    }

    @Transactional
    public void upsertScoreScale(List<OgeDtos.ScoreScaleRow> rows) {
        scoreScaleRepository.deleteAllInBatch();
        List<OgeScoreScaleEntry> entities = new ArrayList<>();
        for (OgeDtos.ScoreScaleRow row : rows) {
            for (String subject : OgeSubjects.CORE_SUBJECTS) {
                OgeScoreScaleEntry entry = new OgeScoreScaleEntry();
                entry.setScore(row.score());
                entry.setSubjectName(subject);
                entry.setGrade(row.gradesBySubject().get(subject));
                entities.add(entry);
            }
        }
        scoreScaleRepository.saveAll(entities);
    }

    @Transactional
    public void ensureDefaultScale() {
        if (scoreScaleRepository.count() > 0) return;
        List<OgeDtos.ScoreScaleRow> defaults = OgeDefaultScale.defaultRows();
        upsertScoreScale(defaults);
    }

    public List<OgeDtos.ScoreScaleRow> scoreScale() {
        ensureDefaultScale();
        Map<Integer, Map<String, Integer>> out = new TreeMap<>();
        for (OgeScoreScaleEntry entry : scoreScaleRepository.findAllByOrderByScoreAscSubjectNameAsc()) {
            out.computeIfAbsent(entry.getScore(), k -> new LinkedHashMap<>()).put(entry.getSubjectName(), entry.getGrade());
        }
        return out.entrySet().stream().map(e -> new OgeDtos.ScoreScaleRow(e.getKey(), e.getValue())).toList();
    }

    @Transactional
    public List<OgeDtos.ImportFileResult> importWorks(List<MultipartFile> files) {
        ensureDefaultScale();
        Map<String, Map<Integer, Integer>> scale = scaleMap();
        List<OgeDtos.ImportFileResult> out = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("work.xlsx");
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                out.add(new OgeDtos.ImportFileResult(fileName, false, "Поддерживается только .xlsx", 0));
                continue;
            }
            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet info = workbook.getSheet("Информация");
                Sheet data = workbook.getSheet("Сбор информации");
                if (info == null || data == null) throw new IllegalArgumentException("Нет листов 'Информация'/'Сбор информации'");
                String subject = extractSubject(info);
                if (!OgeSubjects.CORE_SUBJECTS.contains(subject)) throw new IllegalArgumentException("Не определён предмет");
                HeaderPos pos = detectHeader(data);
                if (pos == null) throw new IllegalArgumentException("Не найдены заголовки ФИО/Итог");
                int count = 0;
                for (int r = pos.dataStartRow; r <= data.getLastRowNum(); r++) {
                    Row row = data.getRow(r);
                    if (row == null) continue;
                    String fio = OgeSubjects.normalizeFio(getText(row.getCell(pos.fioCol)));
                    if (fio.isBlank()) continue;
                    Integer score = parseInt(getText(row.getCell(pos.scoreCol)));
                    String className = OgeSubjects.normalizeClassName(getText(row.getCell(pos.classCol)));
                    if (className.isBlank()) {
                        className = resolveClassFromGia(fio);
                    }
                    if (className.isBlank()) continue;

                    Integer grade = score == null ? null : scale.getOrDefault(subject, Map.of()).get(score);
                    if (score != null && grade == null) {
                        throw new IllegalArgumentException("Нет оценки в таблице баллов для предмета '" + subject + "' и балла " + score);
                    }

                    OgeWorkResult entity = workResultRepository
                            .findByClassNameAndFullNameAndSubjectName(className, fio, subject)
                            .orElseGet(OgeWorkResult::new);
                    entity.setClassName(className);
                    entity.setFullName(fio);
                    entity.setSubjectName(subject);
                    entity.setTestScore(score);
                    entity.setGrade(grade);
                    entity.setSourceFile(fileName);
                    entity.setUpdatedAt(LocalDateTime.now());
                    workResultRepository.save(entity);
                    count++;
                }
                out.add(new OgeDtos.ImportFileResult(fileName, true, "OK", count));
            } catch (Exception e) {
                out.add(new OgeDtos.ImportFileResult(fileName, false, e.getMessage(), 0));
            }
        }
        return out;
    }

    public OgeDtos.WorkDatasetResponse workDataset() {
        OgeGiaVersion latest = latestVersion();
        List<OgeWorkResult> work = workResultRepository.findAllByOrderByClassNameAscFullNameAscSubjectNameAsc();
        Map<String, OgeWorkResult> workByKey = work.stream().collect(Collectors.toMap(
                w -> key(w.getClassName(), w.getFullName(), w.getSubjectName()), Function.identity(), (a, b) -> b, LinkedHashMap::new));

        Map<String, OgeGiaParticipant> expected = new LinkedHashMap<>();
        if (latest != null) {
            for (OgeGiaParticipant p : latest.getParticipants()) {
                for (String subject : p.getSelectedSubjects()) {
                    expected.put(key(p.getClassName(), p.getFullName(), subject), p);
                }
            }
        }

        List<OgeDtos.WorkResultRow> resultRows = new ArrayList<>();
        for (OgeWorkResult wr : work) {
            boolean expectedNow = expected.containsKey(key(wr.getClassName(), wr.getFullName(), wr.getSubjectName()));
            String status = "ok";
            if (!expectedNow) status = "gray";
            else if (wr.getGrade() != null && wr.getGrade() == 2) status = "red";
            resultRows.add(new OgeDtos.WorkResultRow(wr.getClassName(), wr.getFullName(), wr.getSubjectName(), wr.getTestScore(), wr.getGrade(), expectedNow, status));
        }

        List<OgeDtos.WorkResultRow> missing = expected.entrySet().stream()
                .filter(e -> !workByKey.containsKey(e.getKey()))
                .map(e -> new OgeDtos.WorkResultRow(e.getValue().getClassName(), e.getValue().getFullName(), splitKeySubject(e.getKey()), null, null, true, "yellow"))
                .sorted(Comparator.comparing(OgeDtos.WorkResultRow::subject).thenComparing(OgeDtos.WorkResultRow::className).thenComparing(OgeDtos.WorkResultRow::fullName))
                .toList();

        Map<String, Map<String, int[]>> agg = new TreeMap<>();
        for (OgeWorkResult wr : work) {
            if (wr.getGrade() == null || wr.getGrade() < 2 || wr.getGrade() > 5) continue;
            agg.computeIfAbsent(wr.getClassName(), k -> new TreeMap<>())
                    .computeIfAbsent(wr.getSubjectName(), k -> new int[6])[wr.getGrade()]++;
        }
        List<OgeDtos.WorkStatsRow> stats = new ArrayList<>();
        agg.forEach((cls, bySub) -> bySub.forEach((sub, counts) -> stats.add(new OgeDtos.WorkStatsRow(cls, sub, counts[2], counts[3], counts[4], counts[5]))));

        return new OgeDtos.WorkDatasetResponse(resultRows, missing, stats);
    }

    public byte[] exportWorksWorkbook() throws IOException {
        OgeDtos.WorkDatasetResponse data = workDataset();
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

    public byte[] exportGiaWorkbook() throws IOException {
        List<OgeDtos.GiaParticipantView> participants = latestParticipants();
        OgeDtos.GiaChangesResponse changes = changesBetweenLastTwo();
        OgeDtos.GiaStatsResponse stats = giaStats();
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

    private CellStyle color(Workbook wb, short color) {
        CellStyle style = wb.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFillForegroundColor(color);
        return style;
    }

    private OgeGiaVersion latestVersion() {
        return giaVersionRepository.findTop2ByOrderByUploadedAtDescIdDesc().stream().findFirst().orElse(null);
    }

    private String resolveClassFromGia(String fio) {
        OgeGiaVersion latest = latestVersion();
        if (latest == null) return "";
        return latest.getParticipants().stream()
                .filter(p -> OgeSubjects.normalizeFio(p.getFullName()).equalsIgnoreCase(fio))
                .map(OgeGiaParticipant::getClassName)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");
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
        int scoreCol = -1;
        for (int c = 0; c <= Math.min(80, header.getLastCellNum()); c++) {
            String val = getText(header.getCell(c)).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (val.contains("фио")) fioCol = c;
            if (val.contains("класс")) classCol = c;
            if ("итог".equals(val) || "итого".equals(val)) scoreCol = c;
        }
        return fioCol >= 0 && scoreCol >= 0 ? new HeaderPos(fioCol, classCol < 0 ? 0 : classCol, scoreCol, 3) : null;
    }

    private Map<String, Map<Integer, Integer>> scaleMap() {
        Map<String, Map<Integer, Integer>> map = new LinkedHashMap<>();
        for (String s : OgeSubjects.CORE_SUBJECTS) map.put(s, new HashMap<>());
        for (OgeScoreScaleEntry e : scoreScaleRepository.findAllByOrderByScoreAscSubjectNameAsc()) {
            map.computeIfAbsent(e.getSubjectName(), k -> new HashMap<>()).put(e.getScore(), e.getGrade());
        }
        return map;
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

    private String key(String className, String fio, String subject) {
        return blank(className) + "|" + OgeSubjects.normalizeFio(fio).toUpperCase(Locale.ROOT) + "|" + subject;
    }

    private String splitKeySubject(String key) {
        String[] parts = key.split("\\|");
        return parts.length >= 3 ? parts[2] : "";
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

    private Integer parseInt(String text) {
        String value = blank(text).trim().replace(',', '.');
        if (value.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (Exception e) {
            return null;
        }
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

    private record HeaderPos(int fioCol, int classCol, int scoreCol, int dataStartRow) {}
}
