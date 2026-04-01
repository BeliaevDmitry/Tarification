package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.ServiceMemoService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ServiceMemoServiceImpl implements ServiceMemoService {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SELECTION_SEPARATOR = "::";

    private final org.school.personalLoad.dao.TarifficationChangesDAO changesDAO;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ServiceMemoRepository serviceMemoRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.PendingTeacher> findPendingTeachers() {
        Map<TeacherDateKey, TeacherChangeAggregate> candidates = loadTeacherChangesByDate();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, ServiceMemo> latestMemoBySelection = latestMemoBySelectionKey();
        return candidates.entrySet().stream()
                .filter(entry -> {
                    String selectionKey = selectionKey(entry.getKey());
                    ServiceMemo latest = latestMemoBySelection.get(selectionKey);
                    if (latest == null) {
                        return true;
                    }
                    String currentSignature = aggregateSignature(entry.getValue());
                    return !signatureMatches(latest.getLoadSignature(), currentSignature);
                })
                .map(entry -> toPendingDto(selectionKey(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(ServiceMemoDtos.PendingTeacher::getStartDate)
                        .thenComparing(ServiceMemoDtos.PendingTeacher::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findProcessed() {
        return serviceMemoRepository.findAllByStatusOrderByCreatedAtDesc(ServiceMemo.Status.PROCESSED)
                .stream().map(this::toProcessedDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findArchived() {
        return serviceMemoRepository.findAllByStatusOrderByCreatedAtDesc(ServiceMemo.Status.ARCHIVED)
                .stream().map(this::toProcessedDto).toList();
    }

    @Override
    public List<ServiceMemoDtos.ProcessedMemo> generateForTeachers(List<String> fioTeachers, String createdBy) {
        List<String> requested = Optional.ofNullable(fioTeachers).orElseGet(List::of).stream()
                .map(v -> String.valueOf(v == null ? "" : v).trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одну запись со списком изменений");
        }

        Map<TeacherDateKey, TeacherChangeAggregate> allPending = loadTeacherChangesByDate();
        Map<String, ServiceMemo> latestMemoBySelection = latestMemoBySelectionKey();
        Map<String, String> teacherDativeByFio = loadTeacherDativeByFio();

        List<ServiceMemo> created = new ArrayList<>();
        for (String token : requested) {
            SelectionRequest selectionRequest = parseSelectionRequest(token);
            List<Map.Entry<TeacherDateKey, TeacherChangeAggregate>> matched = allPending.entrySet().stream()
                    .filter(entry -> selectionMatches(selectionRequest, entry.getKey()))
                    .toList();
            if (matched.isEmpty()) {
                continue;
            }
            for (Map.Entry<TeacherDateKey, TeacherChangeAggregate> entry : matched) {
                String selectionKey = selectionKey(entry.getKey());
                TeacherChangeAggregate aggregate = entry.getValue();
                String signature = aggregateSignature(aggregate);
                ServiceMemo latest = latestMemoBySelection.get(selectionKey);
                if (latest != null && signatureMatches(latest.getLoadSignature(), signature)) {
                    continue;
                }

                ServiceMemo entity = new ServiceMemo();
                entity.setFioTeacher(aggregate.teacherDisplay());
                entity.setChangeStartDate(aggregate.startDate());
                entity.setCreatedBy(createdBy);
                entity.setGeneratedFilename("служебка по нагрузке " + safeName(aggregate.teacherDisplay())
                        + " " + aggregate.startDate() + ".docx");
                entity.setLoadSignature(signature);
                entity.setGeneratedDocument(buildDocx(aggregate.teacherDisplay(), aggregate, createdBy, teacherDativeByFio));
                created.add(serviceMemoRepository.save(entity));
                latestMemoBySelection.put(selectionKey, entity);
            }
        }

        if (created.isEmpty()) {
            throw new IllegalArgumentException("Служебки не сформированы: для выбранных записей нет новых изменений");
        }
        return created.stream().map(this::toProcessedDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceMemo getById(Long id) {
        return serviceMemoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Служебная записка не найдена"));
    }

    @Override
    public ServiceMemo archive(Long id) {
        ServiceMemo memo = getById(id);
        memo.setStatus(ServiceMemo.Status.ARCHIVED);
        memo.setArchivedAt(LocalDateTime.now());
        return serviceMemoRepository.save(memo);
    }

    @Override
    public ServiceMemo uploadCorrected(Long id, String filename, byte[] content) {
        ServiceMemo memo = getById(id);
        memo.setCorrectedFilename(filename);
        memo.setCorrectedDocument(content);
        return serviceMemoRepository.save(memo);
    }

    private ServiceMemoDtos.ProcessedMemo toProcessedDto(ServiceMemo memo) {
        return ServiceMemoDtos.ProcessedMemo.builder()
                .id(memo.getId())
                .fioTeacher(memo.getFioTeacher())
                .startDate(memo.getChangeStartDate())
                .status(memo.getStatus().name())
                .createdBy(memo.getCreatedBy())
                .createdAt(memo.getCreatedAt())
                .generatedFilename(memo.getGeneratedFilename())
                .correctedFilename(memo.getCorrectedFilename())
                .build();
    }

    private ServiceMemoDtos.PendingTeacher toPendingDto(String teacherKey, TeacherChangeAggregate aggregate) {
        List<ServiceMemoDtos.LoadRow> rows = aggregate.rows().stream()
                .map(row -> ServiceMemoDtos.LoadRow.builder()
                        .fioTeacher(row.getFioTeacher())
                        .subjectName(row.getSubjectName())
                        .className(row.getClassName())
                        .load(row.getLoad())
                        .status(resolveStatus(aggregate, row))
                        .build())
                .toList();
        int total = rows.stream()
                .filter(row -> !"Снять".equalsIgnoreCase(String.valueOf(row.getStatus())))
                .map(ServiceMemoDtos.LoadRow::getLoad)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return ServiceMemoDtos.PendingTeacher.builder()
                .teacherKey(teacherKey)
                .fioTeacher(aggregate.teacherDisplay())
                .startDate(aggregate.startDate())
                .memoType(aggregate.onlyAdditions() ? "NEW" : "CHANGED")
                .rows(rows)
                .totalHours(total)
                .build();
    }

    private Map<TeacherDateKey, TeacherChangeAggregate> loadTeacherChangesByDate() {
        LocalDate start = academicStart();
        LocalDate end = academicEnd();

        List<ManualLoadEntry> periodRows = manualLoadEntryRepository.findAll().stream()
                .filter(row -> row.getLoadFromDate() != null && row.getLoadToDate() != null)
                .filter(row -> !row.getLoadFromDate().isAfter(end) && !row.getLoadToDate().isBefore(start))
                .toList();

        Map<String, List<ManualLoadEntry>> rowsByTeacher = periodRows.stream()
                .filter(row -> normalize(row.getFioTeacher()) != null)
                .collect(Collectors.groupingBy(row -> normalize(row.getFioTeacher())));

        List<TarifficationChanges> periodChanges = changesDAO.findAll().stream()
                .filter(ch -> ch.getChangeDate() != null)
                .filter(ch -> normalize(ch.getFioTeacher()) != null)
                .filter(ch -> {
                    LocalDate date = ch.getChangeDate().toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .toList();

        Map<String, NavigableSet<LocalDate>> candidateDatesByTeacher = new HashMap<>();
        rowsByTeacher.forEach((teacher, rows) -> {
            NavigableSet<LocalDate> dates = candidateDatesByTeacher.computeIfAbsent(teacher, t -> new TreeSet<>());
            for (ManualLoadEntry row : rows) {
                if (!row.getLoadFromDate().isBefore(start) && !row.getLoadFromDate().isAfter(end)) {
                    dates.add(row.getLoadFromDate());
                }
                LocalDate nextDay = row.getLoadToDate().plusDays(1);
                if (!nextDay.isBefore(start) && !nextDay.isAfter(end)) {
                    dates.add(nextDay);
                }
            }
        });
        for (TarifficationChanges change : periodChanges) {
            String teacher = normalize(change.getFioTeacher());
            if (teacher == null) {
                continue;
            }
            candidateDatesByTeacher
                    .computeIfAbsent(teacher, t -> new TreeSet<>())
                    .add(change.getChangeDate().toLocalDate());
        }

        Map<String, String> displayByTeacher = new HashMap<>();
        teacherDirectoryRepository.findAll().forEach(row -> {
            String key = normalize(row.getFioTeacher());
            if (key != null) {
                displayByTeacher.putIfAbsent(key, row.getFioTeacher());
            }
        });
        periodRows.forEach(row -> {
            String key = normalize(row.getFioTeacher());
            if (key != null) {
                displayByTeacher.putIfAbsent(key, row.getFioTeacher());
            }
        });

        Map<TeacherDateKey, TeacherChangeAggregate> result = new LinkedHashMap<>();
        for (Map.Entry<String, NavigableSet<LocalDate>> entry : candidateDatesByTeacher.entrySet()) {
            String teacherKey = entry.getKey();
            List<ManualLoadEntry> teacherRows = rowsByTeacher.getOrDefault(teacherKey, List.of());
            for (LocalDate changeDate : entry.getValue()) {
                List<ManualLoadEntry> beforeRows = teacherRows.stream()
                        .filter(row -> isActiveAt(row, changeDate.minusDays(1)))
                        .toList();
                List<ManualLoadEntry> afterRows = teacherRows.stream()
                        .filter(row -> isActiveAt(row, changeDate))
                        .toList();

                Set<String> beforeKeys = beforeRows.stream().map(this::keyOf).collect(Collectors.toSet());
                Set<String> afterKeys = afterRows.stream().map(this::keyOf).collect(Collectors.toSet());

                Set<String> removedKeys = new LinkedHashSet<>(beforeKeys);
                removedKeys.removeAll(afterKeys);
                Set<String> addedKeys = new LinkedHashSet<>(afterKeys);
                addedKeys.removeAll(beforeKeys);
                if (addedKeys.isEmpty() && removedKeys.isEmpty()) {
                    continue;
                }

                List<ManualLoadEntry> removedRows = beforeRows.stream()
                        .filter(row -> removedKeys.contains(keyOf(row)))
                        .map(row -> copyForRemoval(row, changeDate.minusDays(1)))
                        .toList();
                List<ManualLoadEntry> addedRows = afterRows.stream()
                        .filter(row -> addedKeys.contains(keyOf(row)))
                        .toList();
                List<ManualLoadEntry> rowsForMemo = mergeRowsForMemo(afterRows, removedRows, addedRows);
                if (rowsForMemo.isEmpty()) {
                    continue;
                }

                LocalDateTime latestChangeAt = periodChanges.stream()
                        .filter(ch -> Objects.equals(teacherKey, normalize(ch.getFioTeacher())))
                        .filter(ch -> Objects.equals(changeDate, ch.getChangeDate().toLocalDate()))
                        .map(TarifficationChanges::getChangeDate)
                        .max(LocalDateTime::compareTo)
                        .orElse(changeDate.atStartOfDay());

                String displayName = displayByTeacher.getOrDefault(teacherKey, rowsForMemo.get(0).getFioTeacher());
                result.put(new TeacherDateKey(teacherKey, changeDate), new TeacherChangeAggregate(
                        displayName,
                        changeDate,
                        latestChangeAt,
                        rowsForMemo,
                        Set.copyOf(afterKeys),
                        Set.copyOf(addedKeys),
                        Set.copyOf(removedKeys),
                        !addedKeys.isEmpty() && removedKeys.isEmpty()
                ));
            }
        }

        return result;
    }

    private boolean isActiveAt(ManualLoadEntry row, LocalDate date) {
        if (row == null || date == null || row.getLoadFromDate() == null || row.getLoadToDate() == null) {
            return false;
        }
        return !row.getLoadFromDate().isAfter(date) && !row.getLoadToDate().isBefore(date);
    }

    private Map<String, String> loadTeacherDativeByFio() {
        Map<String, String> teacherDativeByFio = new HashMap<>();
        teacherDirectoryRepository.findAll().forEach(entry -> {
            String key = normalize(entry.getFioTeacher());
            if (key == null) {
                return;
            }
            String dative = entry.getFioTeacherDative();
            if (dative == null || dative.isBlank()) {
                return;
            }
            teacherDativeByFio.putIfAbsent(key, dative);
        });
        return teacherDativeByFio;
    }

    private Map<String, ServiceMemo> latestMemoBySelectionKey() {
        return serviceMemoRepository
                .findAllByStatusInOrderByCreatedAtDesc(List.of(ServiceMemo.Status.PROCESSED, ServiceMemo.Status.ARCHIVED))
                .stream()
                .filter(memo -> normalize(memo.getFioTeacher()) != null && memo.getChangeStartDate() != null)
                .collect(Collectors.toMap(
                        memo -> selectionKey(new TeacherDateKey(normalize(memo.getFioTeacher()), memo.getChangeStartDate())),
                        memo -> memo,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b
                ));
    }

    private boolean selectionMatches(SelectionRequest selectionRequest, TeacherDateKey key) {
        if (selectionRequest == null || key == null) {
            return false;
        }
        if (!Objects.equals(selectionRequest.teacherKey(), key.teacherKey())) {
            return false;
        }
        return selectionRequest.changeDate() == null || Objects.equals(selectionRequest.changeDate(), key.changeDate());
    }

    private SelectionRequest parseSelectionRequest(String token) {
        String safeToken = String.valueOf(token == null ? "" : token).trim();
        if (safeToken.isBlank()) {
            return new SelectionRequest(null, null);
        }
        if (!safeToken.contains(SELECTION_SEPARATOR)) {
            return new SelectionRequest(normalize(safeToken), null);
        }
        int idx = safeToken.lastIndexOf(SELECTION_SEPARATOR);
        String teacherPart = safeToken.substring(0, idx);
        String datePart = safeToken.substring(idx + SELECTION_SEPARATOR.length());
        try {
            return new SelectionRequest(normalize(teacherPart), LocalDate.parse(datePart));
        } catch (Exception ex) {
            return new SelectionRequest(normalize(safeToken), null);
        }
    }

    private String resolveStatus(TeacherChangeAggregate aggregate, ManualLoadEntry row) {
        String key = keyOf(row);
        if (aggregate.removedKeys().contains(key) && !aggregate.activeKeys().contains(key)) return "Снять";
        if (aggregate.addedKeys().contains(key) && !aggregate.removedKeys().contains(key)) return "Добавить";
        return "";
    }

    private ManualLoadEntry copyForRemoval(ManualLoadEntry source, LocalDate removalToDate) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setFioTeacher(source.getFioTeacher());
        row.setSubjectName(source.getSubjectName());
        row.setClassName(source.getClassName());
        row.setLoad(source.getLoad());
        row.setLoadFromDate(source.getLoadFromDate());
        row.setLoadToDate(removalToDate);
        row.setGroupNameEducationalPlan(source.getGroupNameEducationalPlan());
        row.setEducationLevel(source.getEducationLevel());
        row.setStudyPeriod(source.getStudyPeriod());
        return row;
    }

    private List<ManualLoadEntry> mergeRowsForMemo(List<ManualLoadEntry> activeRows,
                                                   List<ManualLoadEntry> removedRows,
                                                   List<ManualLoadEntry> addedRows) {
        LinkedHashMap<String, ManualLoadEntry> merged = new LinkedHashMap<>();
        for (ManualLoadEntry row : Optional.ofNullable(activeRows).orElseGet(List::of)) {
            merged.put(keyOf(row), row);
        }
        for (ManualLoadEntry row : Optional.ofNullable(removedRows).orElseGet(List::of)) {
            merged.putIfAbsent(keyOf(row), row);
        }
        for (ManualLoadEntry row : Optional.ofNullable(addedRows).orElseGet(List::of)) {
            merged.putIfAbsent(keyOf(row), row);
        }
        return new ArrayList<>(merged.values());
    }

    private byte[] buildDocx(String fioTeacher,
                             TeacherChangeAggregate aggregate,
                             String createdBy,
                             Map<String, String> teacherDativeByFio) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String teacherDative = Optional.ofNullable(teacherDativeByFio.get(normalize(fioTeacher)))
                    .filter(value -> !value.isBlank())
                    .orElse(fioTeacher);
            paragraph(doc, "Директору ГБОУ Школы №7", false, ParagraphAlignment.RIGHT, 12, 0, 0, 0);
            paragraph(doc, "Ждановой И.Д.", false, ParagraphAlignment.RIGHT, 12, 0, 160, 0);
            paragraph(doc, "от заместителя директора", false, ParagraphAlignment.RIGHT, 12, 0, 0, 0);
            paragraph(doc, createdBy, false, ParagraphAlignment.RIGHT, 12, 0, 220, 0);

            paragraph(doc, "СЛУЖЕБНАЯ ЗАПИСКА", true, ParagraphAlignment.CENTER, 14, 0, 220, 0);
            paragraph(doc, "В связи с производственной необходимостью прошу Вас утвердить изменение учебной нагрузки.", false,
                    ParagraphAlignment.BOTH, 12, 0, 120, 420);

            if (aggregate.onlyAdditions()) {
                paragraph(doc, "Прошу Вас с " + RU_DATE.format(aggregate.startDate()) + " утвердить нагрузку на учебный год сотруднику "
                        + teacherDative + " в следующем объеме:", false, ParagraphAlignment.BOTH, 12, 0, 120, 420);
            } else {
                paragraph(doc, "На основании личного заявления " + teacherDative
                        + " считать актуальной следующую учебную нагрузку данного учителя с "
                        + RU_DATE.format(aggregate.startDate()) + ":", false, ParagraphAlignment.BOTH, 12, 0, 120, 420);
            }

            int totalRemainingHours = appendTable(doc, aggregate.rows(), aggregate, aggregate.onlyAdditions());

            paragraph(doc, "", false, ParagraphAlignment.LEFT, 12, 120, 0, 0);
            paragraph(doc, "Итого: " + totalRemainingHours + " ч.", true, ParagraphAlignment.LEFT, 12, 0, 160, 0);
            paragraph(doc, createdBy, false, ParagraphAlignment.RIGHT, 12, 220, 0, 0);
            paragraph(doc, RU_DATE.format(LocalDate.now()), false, ParagraphAlignment.RIGHT, 12, 0, 0, 0);

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать DOCX", e);
        }
    }

    private int appendTable(XWPFDocument doc, List<ManualLoadEntry> rows, TeacherChangeAggregate aggregate, boolean newEmployeeMode) {
        List<ManualLoadEntry> safeRows = rows == null ? List.of() : rows;
        XWPFTable table = doc.createTable(1, newEmployeeMode ? 3 : 4);
        table.setWidthType(TableWidthType.PCT);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        List<String> header = newEmployeeMode
                ? List.of("Предмет", "Класс", "Количество часов")
                : List.of("Предмет", "Класс", "Количество часов", "Статус");
        for (int i = 0; i < header.size(); i++) {
            setCellText(table.getRow(0).getCell(i), header.get(i), true);
        }

        int totalRemainingHours = 0;
        for (ManualLoadEntry row : safeRows) {
            if (row == null) {
                continue;
            }
            XWPFTableRow tr = table.createRow();
            String status = resolveStatus(aggregate, row);
            setCellText(tr.getCell(0), safeDocText(row.getSubjectName()), false);
            setCellText(tr.getCell(1), safeDocText(row.getClassName()), false);
            setCellText(tr.getCell(2), String.valueOf(row.getLoad() == null ? 0 : row.getLoad()), false);
            if (!"Снять".equalsIgnoreCase(status)) {
                totalRemainingHours += row.getLoad() == null ? 0 : row.getLoad();
            }
            if (!newEmployeeMode) {
                setCellText(tr.getCell(3), status, false);
            }
        }
        return totalRemainingHours;
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold) {
        if (cell == null) {
            return;
        }
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Times New Roman");
        run.setFontSize(12);
        run.setBold(bold);
        run.setText(text == null ? "" : text);
    }

    private void paragraph(XWPFDocument doc, String text, boolean bold) {
        paragraph(doc, text, bold, ParagraphAlignment.LEFT, 12, 0, 0, 0);
    }

    private void paragraph(XWPFDocument doc, String text, boolean bold, ParagraphAlignment alignment) {
        paragraph(doc, text, bold, alignment, 12, 0, 0, 0);
    }

    private void paragraph(XWPFDocument doc,
                           String text,
                           boolean bold,
                           ParagraphAlignment alignment,
                           int fontSize,
                           int spacingBefore,
                           int spacingAfter,
                           int firstLineIndent) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alignment == null ? ParagraphAlignment.LEFT : alignment);
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(spacingAfter);
        p.setFirstLineIndent(firstLineIndent);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily("Times New Roman");
        run.setFontSize(fontSize);
        run.setBold(bold);
    }

    private LocalDate academicStart() {
        var ranges = studyPeriodSettingService.rangesByKey();
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9)).map(StudyPeriodSettingService.DateRange::startDate)
                .orElse(LocalDate.of(LocalDate.now().getYear(), 9, 1));
    }

    private LocalDate academicEnd() {
        var ranges = studyPeriodSettingService.rangesByKey();
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9)).map(StudyPeriodSettingService.DateRange::endDate)
                .orElse(LocalDate.of(LocalDate.now().plusYears(1).getYear(), 5, 31));
    }

    private String keyOf(TarifficationChanges ch) {
        return String.join("|", safe(ch.getSubjectName()), safe(ch.getClassName()), String.valueOf(ch.getLoad()));
    }

    private String keyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                String.valueOf(row.getLoad()),
                String.valueOf(row.getLoadFromDate() == null ? "" : row.getLoadFromDate()),
                String.valueOf(row.getLoadToDate() == null ? "" : row.getLoadToDate()));
    }

    private String transferKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                safe(row.getGroupNameEducationalPlan()),
                String.valueOf(row.getEducationLevel()),
                String.valueOf(row.getStudyPeriod()));
    }

    private String safe(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        String v = safe(value);
        return v.isBlank() ? null : v;
    }

    private String safeName(String fio) {
        return String.valueOf(fio == null ? "teacher" : fio)
                .replaceAll("[^\\p{L}\\p{N}]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String safeDocText(String value) {
        return value == null ? "" : value;
    }

    private String aggregateSignature(TeacherChangeAggregate aggregate) {
        if (aggregate == null || aggregate.rows() == null) {
            return "";
        }
        String payload = aggregate.rows().stream()
                .filter(Objects::nonNull)
                .map(row -> String.join("|",
                        safe(row.getSubjectName()),
                        safe(row.getClassName()),
                        String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                        String.valueOf(row.getLoadFromDate() == null ? "" : row.getLoadFromDate()),
                        String.valueOf(row.getLoadToDate() == null ? "" : row.getLoadToDate()),
                        resolveStatus(aggregate, row)))
                .sorted()
                .collect(Collectors.joining("||", aggregate.startDate() + "|" + aggregate.onlyAdditions() + "|", ""));
        return signatureHash(payload);
    }

    private boolean signatureMatches(String persistedSignature, String currentSignature) {
        if (Objects.equals(persistedSignature, currentSignature)) {
            return true;
        }
        if (persistedSignature == null || persistedSignature.isBlank() || currentSignature == null || currentSignature.isBlank()) {
            return false;
        }
        return Objects.equals(signatureHash(persistedSignature), currentSignature);
    }

    private String signatureHash(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private String selectionKey(TeacherDateKey key) {
        return key.teacherKey() + SELECTION_SEPARATOR + key.changeDate();
    }

    private LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private record SelectionRequest(String teacherKey, LocalDate changeDate) {
    }

    private record TeacherDateKey(String teacherKey, LocalDate changeDate) {
    }

    private record TeacherChangeAggregate(
            String teacherDisplay,
            LocalDate startDate,
            LocalDateTime latestChangeAt,
            List<ManualLoadEntry> rows,
            Set<String> activeKeys,
            Set<String> addedKeys,
            Set<String> removedKeys,
            boolean onlyAdditions
    ) {
    }
}
