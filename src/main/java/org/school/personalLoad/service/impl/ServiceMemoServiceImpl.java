package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.ServiceMemoService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private final org.school.personalLoad.dao.TarifficationChangesDAO changesDAO;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ServiceMemoRepository serviceMemoRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.PendingTeacher> findPendingTeachers() {
        Map<String, TeacherChangeAggregate> candidates = loadTeacherChanges();
        log.info("service-memos pending: кандидатов={} (до фильтра обработанных)", candidates.size());
        if (candidates.isEmpty()) return List.of();

        Set<String> teacherNames = candidates.values().stream()
                .map(TeacherChangeAggregate::teacherDisplay)
                .collect(Collectors.toSet());
        Map<String, LocalDateTime> latestMemoByTeacher = serviceMemoRepository
                .findAllByFioTeacherInAndStatusIn(teacherNames, List.of(ServiceMemo.Status.PROCESSED))
                .stream()
                .collect(Collectors.toMap(
                        ServiceMemo::getFioTeacher,
                        ServiceMemo::getCreatedAt,
                        (a, b) -> a.isAfter(b) ? a : b
                ));

        List<ServiceMemoDtos.PendingTeacher> pending = candidates.entrySet().stream()
                .filter(entry -> {
                    LocalDateTime latestMemoAt = latestMemoByTeacher.get(entry.getValue().teacherDisplay());
                    if (latestMemoAt == null) {
                        return true;
                    }
                    LocalDateTime latestChangeAt = entry.getValue().latestChangeAt();
                    return latestChangeAt == null || latestChangeAt.isAfter(latestMemoAt);
                })
                .map(entry -> toPendingDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ServiceMemoDtos.PendingTeacher::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .toList();
        log.info("service-memos pending: доступно к обработке={}", pending.size());
        return pending;
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
        List<String> normalized = Optional.ofNullable(fioTeachers).orElseGet(List::of).stream()
                .map(v -> String.valueOf(v == null ? "" : v).trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        log.info("service-memos generate: входящих педагогов={}, createdBy={}", normalized.size(), createdBy);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одного педагога");
        }

        Map<String, TeacherChangeAggregate> allPending = loadTeacherChanges();
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
        List<ServiceMemo> created = new ArrayList<>();
        List<String> generationErrors = new ArrayList<>();

        for (String fioTeacher : normalized) {
            TeacherChangeAggregate aggregate = allPending.get(normalize(fioTeacher));
            if (aggregate == null) {
                log.warn("service-memos generate: педагог '{}' не найден среди pending-кандидатов", fioTeacher);
                continue;
            }

            try {
                ServiceMemo entity = new ServiceMemo();
                entity.setFioTeacher(aggregate.teacherDisplay());
                entity.setChangeStartDate(aggregate.startDate());
                entity.setCreatedBy(createdBy);
                entity.setGeneratedFilename("sluzhebnaya_" + safeName(aggregate.teacherDisplay()) + "_" + LocalDate.now() + ".docx");
                entity.setGeneratedDocument(buildDocx(aggregate.teacherDisplay(), aggregate, createdBy, teacherDativeByFio));
                created.add(serviceMemoRepository.save(entity));
                log.info("service-memos generate: сформирована служебка для '{}', memoId={}", aggregate.teacherDisplay(), entity.getId());
            } catch (Exception ex) {
                generationErrors.add(aggregate.teacherDisplay() + ": " + ex.getMessage());
                log.error("service-memos generate: ошибка формирования для '{}'", aggregate.teacherDisplay(), ex);
            }
        }
        if (created.isEmpty() && !generationErrors.isEmpty()) {
            log.warn("service-memos generate: не создано ни одной служебки, ошибок={}", generationErrors.size());
            throw new IllegalArgumentException("Не удалось сформировать служебки. Ошибки: " + String.join(" | ", generationErrors));
        }
        log.info("service-memos generate: успешно создано={}, ошибок={}", created.size(), generationErrors.size());
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
        int total = rows.stream().map(ServiceMemoDtos.LoadRow::getLoad).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        return ServiceMemoDtos.PendingTeacher.builder()
                .teacherKey(teacherKey)
                .fioTeacher(aggregate.teacherDisplay())
                .startDate(aggregate.startDate())
                .memoType(aggregate.onlyAdditions() ? "NEW" : "CHANGED")
                .rows(rows)
                .totalHours(total)
                .build();
    }

    private String resolveStatus(TeacherChangeAggregate aggregate, ManualLoadEntry row) {
        String key = keyOf(row);
        if (aggregate.removedKeys().contains(key) && !aggregate.activeKeys().contains(key)) return "Снять";
        if (aggregate.addedKeys().contains(key) && !aggregate.removedKeys().contains(key)) return "Добавить";
        return "";
    }

    private Map<String, TeacherChangeAggregate> loadTeacherChanges() {
        LocalDate start = academicStart();
        LocalDate end = academicEnd();

        List<TarifficationChanges> changes = changesDAO.findAll().stream()
                .filter(ch -> ch.getChangeDate() != null)
                .filter(ch -> {
                    LocalDate date = ch.getChangeDate().toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .toList();
        log.debug("service-memos loadTeacherChanges: изменений в периоде={}", changes.size());

        Map<String, Set<String>> removedByTeacher = new HashMap<>();
        Map<String, Set<String>> addedByTeacher = new HashMap<>();
        Map<String, Set<String>> removedTeachersByKey = new HashMap<>();
        Map<String, LocalDate> startByTeacher = new HashMap<>();
        Map<String, LocalDateTime> latestChangeByTeacher = new HashMap<>();
        Map<String, String> displayNameByTeacher = new HashMap<>();
        Map<String, List<ManualLoadEntry>> removedRowsByTeacher = new HashMap<>();

        for (TarifficationChanges ch : changes) {
            String teacher = normalize(ch.getFioTeacher());
            if (teacher == null) continue;
            String key = keyOf(ch);
            LocalDate date = ch.getChangeDate().toLocalDate();
            startByTeacher.merge(teacher, date, (a, b) -> a.isBefore(b) ? a : b);
            latestChangeByTeacher.merge(teacher, ch.getChangeDate(), (a, b) -> a.isAfter(b) ? a : b);
            displayNameByTeacher.putIfAbsent(teacher, ch.getFioTeacher());

            if (ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED) {
                removedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
                removedTeachersByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(teacher);
                removedRowsByTeacher.computeIfAbsent(teacher, k -> new ArrayList<>()).add(toSyntheticRow(ch));
            }
            if (ch.getChangeType() == TarifficationChanges.ChangeType.ADDED || ch.getChangeType() == TarifficationChanges.ChangeType.MODIFIED) {
                addedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
            }
        }

        Map<String, TeacherDirectoryEntry> directory = teacherDirectoryRepository.findAll().stream()
                .collect(Collectors.toMap(e -> normalize(e.getFioTeacher()), e -> e, (a, b) -> a));
        List<ManualLoadEntry> periodRows = manualLoadEntryRepository.findAll().stream()
                .filter(row -> {
                    LocalDate from = row.getLoadFromDate();
                    LocalDate to = row.getLoadToDate();
                    return from != null && to != null && !from.isAfter(end) && !to.isBefore(start);
                })
                .toList();

        for (ManualLoadEntry row : periodRows) {
            String teacher = normalize(row.getFioTeacher());
            if (teacher == null) continue;
            displayNameByTeacher.putIfAbsent(teacher, row.getFioTeacher());
            String key = keyOf(row);

            LocalDate from = row.getLoadFromDate();
            LocalDate to = row.getLoadToDate();
            if (from != null && from.isAfter(start) && !from.isAfter(end)) {
                addedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
                startByTeacher.merge(teacher, from, (a, b) -> a.isBefore(b) ? a : b);
                latestChangeByTeacher.merge(teacher, from.atStartOfDay(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        Map<String, List<ManualLoadEntry>> rowsByTeacher = periodRows.stream()
                .filter(row -> normalize(row.getFioTeacher()) != null)
                .collect(Collectors.groupingBy(row -> normalize(row.getFioTeacher())));

        Map<String, TeacherChangeAggregate> result = new HashMap<>();
        Set<String> candidateTeachers = new HashSet<>();
        candidateTeachers.addAll(addedByTeacher.keySet());
        candidateTeachers.addAll(removedByTeacher.keySet());

        for (String teacher : candidateTeachers) {
            List<ManualLoadEntry> activeRows = new ArrayList<>(rowsByTeacher.getOrDefault(teacher, List.of()));
            Set<String> activeKeys = activeRows.stream().map(this::keyOf).collect(Collectors.toSet());
            TeacherDirectoryEntry directoryEntry = directory.get(teacher);
            Set<String> added = addedByTeacher.getOrDefault(teacher, Set.of());
            Set<String> removed = removedByTeacher.getOrDefault(teacher, Set.of());
            Set<String> transferDonors = new LinkedHashSet<>();
            Map<String, List<ManualLoadEntry>> donorRows = new LinkedHashMap<>();
            for (String key : added) {
                Set<String> donors = removedTeachersByKey.getOrDefault(key, Set.of()).stream()
                        .filter(donor -> !donor.equals(teacher))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                transferDonors.addAll(donors);
                donors.forEach(donor -> {
                    List<ManualLoadEntry> rows = donorRows.computeIfAbsent(donor, d -> new ArrayList<>());
                    removedRowsByTeacher.getOrDefault(donor, List.of()).stream()
                            .filter(row -> keyOf(row).equals(key))
                            .forEach(rows::add);
                });
            }

            boolean dismissedTeacherWithoutNewLoad = directoryEntry != null
                    && directoryEntry.getDismissalDate() != null
                    && added.isEmpty()
                    && activeRows.isEmpty();
            if (dismissedTeacherWithoutNewLoad) {
                continue;
            }

            List<ManualLoadEntry> rowsForMemo = mergeRows(activeRows, removedRowsByTeacher.getOrDefault(teacher, List.of()));
            if (rowsForMemo.isEmpty()) {
                continue;
            }

            LocalDate startDate = resolveStartDate(rowsForMemo, startByTeacher.getOrDefault(teacher, start), start, end);
            boolean onlyAdditions = !added.isEmpty() && removed.isEmpty();
            if (transferDonors.contains("вакансия")) {
                boolean hadPreviousLoad = manualLoadEntryRepository.findAll().stream()
                        .filter(row -> normalize(row.getFioTeacher()) != null && normalize(row.getFioTeacher()).equals(teacher))
                        .anyMatch(row -> row.getLoadFromDate() != null && row.getLoadFromDate().isBefore(startDate));
                if (hadPreviousLoad) {
                    onlyAdditions = false;
                }
            }
            String teacherDisplay = displayNameByTeacher.getOrDefault(teacher, rowsForMemo.get(0).getFioTeacher());

            result.put(teacher, new TeacherChangeAggregate(
                    teacherDisplay,
                    startDate,
                    latestChangeByTeacher.get(teacher),
                    rowsForMemo,
                    activeKeys,
                    added,
                    removed,
                    onlyAdditions,
                    transferDonors,
                    donorRows
            ));
        }
        log.debug("service-memos loadTeacherChanges: итоговых агрегатов={}", result.size());
        return result;
    }

    private List<ManualLoadEntry> mergeRows(List<ManualLoadEntry> activeRows, List<ManualLoadEntry> removedRows) {
        LinkedHashMap<String, ManualLoadEntry> merged = new LinkedHashMap<>();
        for (ManualLoadEntry row : activeRows) {
            merged.put(keyOf(row), row);
        }
        for (ManualLoadEntry row : removedRows) {
            merged.putIfAbsent(keyOf(row), row);
        }
        return new ArrayList<>(merged.values());
    }

    private LocalDate resolveStartDate(List<ManualLoadEntry> rows, LocalDate fallback, LocalDate periodStart, LocalDate periodEnd) {
        return rows.stream()
                .map(ManualLoadEntry::getLoadFromDate)
                .filter(Objects::nonNull)
                .filter(date -> !date.isBefore(periodStart) && !date.isAfter(periodEnd))
                .min(LocalDate::compareTo)
                .orElse(fallback);
    }

    private ManualLoadEntry toSyntheticRow(TarifficationChanges ch) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setFioTeacher(ch.getFioTeacher());
        row.setSubjectName(ch.getSubjectName());
        row.setClassName(ch.getClassName());
        row.setLoad(ch.getLoad() == null ? 0 : ch.getLoad());
        return row;
    }

    private byte[] buildDocx(String fioTeacher,
                             TeacherChangeAggregate aggregate,
                             String createdBy,
                             Map<String, String> teacherDativeByFio) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String teacherDative = Optional.ofNullable(teacherDativeByFio.get(normalize(fioTeacher)))
                    .filter(value -> !value.isBlank())
                    .orElse(fioTeacher);
            paragraph(doc, "Директору ГБОУ Школы №7", false, ParagraphAlignment.RIGHT);
            paragraph(doc, "Ждановой И.Д.", false, ParagraphAlignment.RIGHT);
            paragraph(doc, "от заместителя директора", false, ParagraphAlignment.RIGHT);
            paragraph(doc, createdBy, false, ParagraphAlignment.RIGHT);
            paragraph(doc, "", false);
            paragraph(doc, "служебная записка.", true, ParagraphAlignment.CENTER);
            paragraph(doc, "В связи с производственной необходимостью прошу Вас разрешить изменение в тарификации.", false);
            paragraph(doc, "", false);

            boolean twoPoints = !aggregate.transferDonors().isEmpty() && !aggregate.transferDonors().contains("вакансия");

            if (aggregate.onlyAdditions()) {
                String prefix = twoPoints ? "2. " : "";
                paragraph(doc, prefix + "Прошу Вас с " + RU_DATE.format(aggregate.startDate()) + " утвердить нагрузку на учебный год вновь принятому сотруднику "
                        + teacherDative + ", в следующем объеме:", false);
            } else if (twoPoints) {
                String donor = aggregate.transferDonors().iterator().next();
                String donorDative = Optional.ofNullable(teacherDativeByFio.get(normalize(donor)))
                        .filter(value -> !value.isBlank())
                        .orElse(donor);
                paragraph(doc, "1. На основании личного заявления " + donorDative
                        + " считать актуальной следующую учебную нагрузку данного учителя с "
                        + RU_DATE.format(aggregate.startDate()) + ":", false);
                appendTable(doc, aggregate.donorRows().getOrDefault(donor, List.of()), aggregate, false);
                paragraph(doc, "", false);
                paragraph(doc, "2. Прошу Вас с " + RU_DATE.format(aggregate.startDate()) + " утвердить нагрузку на учебный год сотруднику "
                        + teacherDative + ", в следующем объеме:", false);
            } else {
                paragraph(doc, "На основании личного заявления " + teacherDative
                        + " считать актуальной следующую учебную нагрузку данного учителя с "
                        + RU_DATE.format(aggregate.startDate()) + ":", false);
            }
            int totalRemainingHours = appendTable(doc, aggregate.rows(), aggregate, aggregate.onlyAdditions());

            paragraph(doc, "", false);
            paragraph(doc, "ИТОГО: " + totalRemainingHours + " ч.", true);
            paragraph(doc, createdBy, false);
            paragraph(doc, RU_DATE.format(LocalDate.now()), false);

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
        paragraph(doc, text, bold, ParagraphAlignment.LEFT);
    }

    private void paragraph(XWPFDocument doc, String text, boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alignment == null ? ParagraphAlignment.LEFT : alignment);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily("Times New Roman");
        run.setFontSize(14);
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
        return String.join("|", safe(row.getSubjectName()), safe(row.getClassName()), String.valueOf(row.getLoad()));
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

    private record TeacherChangeAggregate(
            String teacherDisplay,
            LocalDate startDate,
            LocalDateTime latestChangeAt,
            List<ManualLoadEntry> rows,
            Set<String> activeKeys,
            Set<String> addedKeys,
            Set<String> removedKeys,
            boolean onlyAdditions,
            Set<String> transferDonors,
            Map<String, List<ManualLoadEntry>> donorRows
    ) {
    }
}
