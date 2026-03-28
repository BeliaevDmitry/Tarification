package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
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
        if (candidates.isEmpty()) return List.of();

        Set<String> teacherNames = candidates.values().stream()
                .map(TeacherChangeAggregate::teacherDisplay)
                .collect(Collectors.toSet());
        List<ServiceMemo> existing = serviceMemoRepository.findAllByFioTeacherInAndStatusIn(
                teacherNames,
                List.of(ServiceMemo.Status.PROCESSED)
        );
        Set<String> alreadyProcessed = existing.stream().map(ServiceMemo::getFioTeacher).collect(Collectors.toSet());

        return candidates.values().stream()
                .filter(aggregate -> !alreadyProcessed.contains(aggregate.teacherDisplay()))
                .map(this::toPendingDto)
                .sorted(Comparator.comparing(ServiceMemoDtos.PendingTeacher::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
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
        List<String> normalized = Optional.ofNullable(fioTeachers).orElseGet(List::of).stream()
                .map(v -> String.valueOf(v == null ? "" : v).trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одного педагога");
        }

        Map<String, TeacherChangeAggregate> allPending = loadTeacherChanges();
        List<ServiceMemo> created = new ArrayList<>();

        for (String fioTeacher : normalized) {
            TeacherChangeAggregate aggregate = allPending.get(normalize(fioTeacher));
            if (aggregate == null) continue;

            ServiceMemo entity = new ServiceMemo();
            entity.setFioTeacher(aggregate.teacherDisplay());
            entity.setChangeStartDate(aggregate.startDate());
            entity.setCreatedBy(createdBy);
            entity.setGeneratedFilename("sluzhebnaya_" + safeName(aggregate.teacherDisplay()) + "_" + LocalDate.now() + ".docx");
            entity.setGeneratedDocument(buildDocx(aggregate.teacherDisplay(), aggregate, createdBy));
            created.add(serviceMemoRepository.save(entity));
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

    private ServiceMemoDtos.PendingTeacher toPendingDto(TeacherChangeAggregate aggregate) {
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
                .fioTeacher(aggregate.teacherDisplay())
                .startDate(aggregate.startDate())
                .memoType(aggregate.onlyAdditions() ? "NEW" : "CHANGED")
                .rows(rows)
                .totalHours(total)
                .build();
    }

    private String resolveStatus(TeacherChangeAggregate aggregate, ManualLoadEntry row) {
        String key = keyOf(row);
        if (aggregate.addedKeys().contains(key) && !aggregate.removedKeys().contains(key)) return "добавлено";
        if (aggregate.removedKeys().contains(key) && !aggregate.addedKeys().contains(key)) return "снято";
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

        Map<String, Set<String>> removedByTeacher = new HashMap<>();
        Map<String, Set<String>> addedByTeacher = new HashMap<>();
        Map<String, LocalDate> startByTeacher = new HashMap<>();
        Map<String, String> displayNameByTeacher = new HashMap<>();
        Map<String, List<ManualLoadEntry>> removedRowsByTeacher = new HashMap<>();

        for (TarifficationChanges ch : changes) {
            String teacher = normalize(ch.getFioTeacher());
            if (teacher == null) continue;
            String key = keyOf(ch);
            LocalDate date = ch.getChangeDate().toLocalDate();
            startByTeacher.merge(teacher, date, (a, b) -> a.isBefore(b) ? a : b);
            displayNameByTeacher.putIfAbsent(teacher, ch.getFioTeacher());

            if (ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED) {
                removedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
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
            if (from != null && !from.isBefore(start) && !from.isAfter(end)) {
                addedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
                startByTeacher.merge(teacher, from, (a, b) -> a.isBefore(b) ? a : b);
            }
            if (to != null && !to.isBefore(start) && !to.isAfter(end) && to.isBefore(end)) {
                removedByTeacher.computeIfAbsent(teacher, k -> new LinkedHashSet<>()).add(key);
                removedRowsByTeacher.computeIfAbsent(teacher, k -> new ArrayList<>()).add(row);
                startByTeacher.merge(teacher, to, (a, b) -> a.isBefore(b) ? a : b);
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
            TeacherDirectoryEntry directoryEntry = directory.get(teacher);
            Set<String> added = addedByTeacher.getOrDefault(teacher, Set.of());
            Set<String> removed = removedByTeacher.getOrDefault(teacher, Set.of());
            boolean dismissedTeacherWithoutNewLoad = directoryEntry != null
                    && directoryEntry.getDismissalDate() != null
                    && added.isEmpty()
                    && activeRows.isEmpty();
            if (dismissedTeacherWithoutNewLoad) {
                continue;
            }

            List<ManualLoadEntry> rowsForMemo = !activeRows.isEmpty()
                    ? activeRows
                    : new ArrayList<>(removedRowsByTeacher.getOrDefault(teacher, List.of()));
            if (rowsForMemo.isEmpty()) {
                continue;
            }

            boolean onlyAdditions = !added.isEmpty() && removed.isEmpty();
            LocalDate startDate = resolveStartDate(rowsForMemo, startByTeacher.getOrDefault(teacher, start), start, end);
            String teacherDisplay = displayNameByTeacher.getOrDefault(teacher, rowsForMemo.get(0).getFioTeacher());

            result.put(teacher, new TeacherChangeAggregate(
                    teacherDisplay,
                    startDate,
                    rowsForMemo,
                    added,
                    removed,
                    onlyAdditions
            ));
        }
        return result;
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

    private byte[] buildDocx(String fioTeacher, TeacherChangeAggregate aggregate, String createdBy) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            paragraph(doc, "Директору ГБОУ Школы №7", false);
            paragraph(doc, "Ждановой И.Д.", false);
            paragraph(doc, "от заместителя директора", false);
            paragraph(doc, createdBy, false);
            paragraph(doc, "", false);
            paragraph(doc, "Служебная записка", true);
            paragraph(doc, "", false);

            if (aggregate.onlyAdditions()) {
                paragraph(doc, "Прошу Вас согласовать вопрос о назначении учебной нагрузки с " + RU_DATE.format(aggregate.startDate()) + ".", false);
            } else {
                paragraph(doc, "Прошу Вас согласовать вопрос об изменении учебной нагрузки с " + RU_DATE.format(aggregate.startDate()) + ".", false);
            }

            XWPFTable table = doc.createTable(1, aggregate.onlyAdditions() ? 4 : 5);
            List<String> header = aggregate.onlyAdditions()
                    ? List.of("ФИО", "Предмет", "Класс", "Количество часов")
                    : List.of("ФИО", "Предмет", "Класс", "Количество часов", "Статус");
            for (int i = 0; i < header.size(); i++) table.getRow(0).getCell(i).setText(header.get(i));

            int total = 0;
            for (ManualLoadEntry row : aggregate.rows()) {
                XWPFTableRow tr = table.createRow();
                tr.getCell(0).setText(row.getFioTeacher());
                tr.getCell(1).setText(row.getSubjectName());
                tr.getCell(2).setText(row.getClassName());
                tr.getCell(3).setText(String.valueOf(row.getLoad()));
                total += row.getLoad() == null ? 0 : row.getLoad();
                if (!aggregate.onlyAdditions()) {
                    tr.getCell(4).setText(resolveStatus(aggregate, row));
                }
            }

            paragraph(doc, "", false);
            paragraph(doc, "ИТОГО: " + total + " ч.", true);
            paragraph(doc, createdBy, false);
            paragraph(doc, "Дата формирования: " + RU_DATE.format(LocalDate.now()), false);

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать DOCX", e);
        }
    }

    private void paragraph(XWPFDocument doc, String text, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
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

    private record TeacherChangeAggregate(
            String teacherDisplay,
            LocalDate startDate,
            List<ManualLoadEntry> rows,
            Set<String> addedKeys,
            Set<String> removedKeys,
            boolean onlyAdditions
    ) {
    }
}
