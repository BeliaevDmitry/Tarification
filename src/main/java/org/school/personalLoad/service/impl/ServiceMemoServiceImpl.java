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
                .stream()
                .map(this::toProcessedDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findArchived() {
        return serviceMemoRepository.findAllByStatusOrderByCreatedAtDesc(ServiceMemo.Status.ARCHIVED)
                .stream()
                .map(this::toProcessedDto)
                .toList();
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
                entity.setGeneratedDocument(buildDocx(
                        aggregate.teacherDisplay(),
                        aggregate,
                        createdBy,
                        teacherDativeByFio
                ));

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
        return serviceMemoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Служебная записка не найдена"));
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
                if (!row.getLoadFromDate().isBefore(start)
                        && !row.getLoadFromDate().isAfter(end)
                        && !row.getLoadFromDate().isEqual(start)) {
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

        for (NavigableSet<LocalDate> dates : candidateDatesByTeacher.values()) {
            dates.remove(start);
        }

        List<org.school.personalLoad.model.TeacherDirectoryEntry> directoryRows = teacherDirectoryRepository.findAll();
        Map<String, String> displayByTeacher = new HashMap<>();
        Map<String, LocalDate> teacherDirectoryCreatedDate = new HashMap<>();
        directoryRows.forEach(row -> {
            String key = normalize(row.getFioTeacher());
            if (key != null) {
                displayByTeacher.putIfAbsent(key, row.getFioTeacher());
                LocalDate createdDate = Optional.ofNullable(row.getCreatedAt()).map(LocalDateTime::toLocalDate).orElse(null);
                if (createdDate != null) {
                    teacherDirectoryCreatedDate.putIfAbsent(key, createdDate);
                }
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
                List<ManualLoadEntry> rowsBeforeDate = normalizeActiveRows(teacherRows.stream()
                        .filter(row -> isActiveAt(row, changeDate.minusDays(1)))
                        .toList());
                List<ManualLoadEntry> rowsOnDate = normalizeActiveRows(teacherRows.stream()
                        .filter(row -> isActiveAt(row, changeDate))
                        .toList());

                Map<String, Integer> beforeCounts = countRows(rowsBeforeDate);
                Map<String, Integer> afterCounts = countRows(rowsOnDate);
                Map<String, Integer> removedCounts = diffCounts(beforeCounts, afterCounts);
                Map<String, Integer> addedCounts = diffCounts(afterCounts, beforeCounts);

                List<TarifficationChanges> teacherDateChanges = periodChanges.stream()
                        .filter(ch -> Objects.equals(teacherKey, normalize(ch.getFioTeacher())))
                        .filter(ch -> Objects.equals(changeDate, ch.getChangeDate().toLocalDate()))
                        .toList();
                LocalDateTime latestChangeAt = teacherDateChanges.stream()
                        .map(TarifficationChanges::getChangeDate)
                        .max(LocalDateTime::compareTo)
                        .orElse(changeDate.atStartOfDay());
                List<TarifficationChanges> dayChanges = teacherDateChanges;
                if (!teacherDateChanges.isEmpty()) {
                    dayChanges = teacherDateChanges.stream()
                            .filter(ch -> Objects.equals(latestChangeAt, ch.getChangeDate()))
                            .toList();
                }
                if (!dayChanges.isEmpty()) {
                    Set<String> anyShortKeys = dayChanges.stream()
                            .map(this::shortKeyOf)
                            .collect(Collectors.toSet());
                    Set<String> removedShortKeys = dayChanges.stream()
                            .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED)
                            .map(this::shortKeyOf)
                            .collect(Collectors.toSet());
                    Set<String> addedShortKeys = dayChanges.stream()
                            .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.ADDED)
                            .map(this::shortKeyOf)
                            .collect(Collectors.toSet());
                    if (!removedShortKeys.isEmpty()) {
                        removedCounts = filterCountsByShortKeys(removedCounts, removedShortKeys);
                    } else if (!anyShortKeys.isEmpty()) {
                        removedCounts = filterCountsByShortKeys(removedCounts, anyShortKeys);
                    }
                    if (!addedShortKeys.isEmpty()) {
                        addedCounts = filterCountsByShortKeys(addedCounts, addedShortKeys);
                    } else if (!anyShortKeys.isEmpty()) {
                        addedCounts = filterCountsByShortKeys(addedCounts, anyShortKeys);
                    }
                }
                if (removedCounts.isEmpty() && addedCounts.isEmpty()) {
                    continue;
                }

                List<ManualLoadEntry> removedRows = selectRowsByCount(rowsBeforeDate, removedCounts).stream()
                        .map(row -> copyForRemoval(row, changeDate.minusDays(1)))
                        .toList();
                List<ManualLoadEntry> addedRows = selectRowsByCount(rowsOnDate, addedCounts);
                List<ManualLoadEntry> rowsForMemo = mergeRowsForMemo(rowsOnDate, removedRows, addedRows);
                if (rowsForMemo.isEmpty()) {
                    continue;
                }

                String displayName = displayByTeacher.getOrDefault(teacherKey, rowsForMemo.get(0).getFioTeacher());
                boolean firstLoadAppearance = rowsBeforeDate.isEmpty()
                        && !rowsOnDate.isEmpty()
                        && teacherRows.stream()
                        .filter(Objects::nonNull)
                        .map(ManualLoadEntry::getLoadFromDate)
                        .filter(Objects::nonNull)
                        .noneMatch(fromDate -> fromDate.isBefore(changeDate));
                boolean newEmploymentByDirectory = Optional.ofNullable(teacherDirectoryCreatedDate.get(teacherKey))
                        .map(createdDate -> !createdDate.isBefore(changeDate))
                        .orElse(true);
                boolean newEmployment = firstLoadAppearance && newEmploymentByDirectory;

                result.put(new TeacherDateKey(teacherKey, changeDate), new TeacherChangeAggregate(
                        displayName,
                        changeDate,
                        latestChangeAt,
                        rowsForMemo,
                        rowsKeySet(addedRows),
                        rowsKeySet(removedRows),
                        newEmployment
                ));
            }
        }

        // Фолбэк для случаев, когда у донора после передачи больше нет ни одной активной строки
        // в текущей ручной нагрузке: берём удалённые строки из истории изменений.
        Map<String, List<TarifficationChanges>> removedByTeacherAndDateTime = periodChanges.stream()
                .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED)
                .collect(Collectors.groupingBy(ch -> {
                    String teacher = normalize(ch.getFioTeacher());
                    return String.valueOf(teacher) + "|" + String.valueOf(ch.getChangeDate());
                }));

        Map<String, List<TarifficationChanges>> latestRemovedByTeacher = new HashMap<>();
        Map<String, LocalDateTime> latestTimestampByTeacher = new HashMap<>();
        for (List<TarifficationChanges> changes : removedByTeacherAndDateTime.values()) {
            if (changes == null || changes.isEmpty()) {
                continue;
            }
            TarifficationChanges first = changes.get(0);
            String teacherKey = normalize(first.getFioTeacher());
            LocalDateTime ts = first.getChangeDate();
            if (teacherKey == null || ts == null) {
                continue;
            }
            LocalDateTime prev = latestTimestampByTeacher.get(teacherKey);
            if (prev == null || ts.isAfter(prev)) {
                latestTimestampByTeacher.put(teacherKey, ts);
                latestRemovedByTeacher.put(teacherKey, changes);
            }
        }

        for (Map.Entry<String, List<TarifficationChanges>> entry : latestRemovedByTeacher.entrySet()) {
            List<TarifficationChanges> removedChanges = entry.getValue();
            if (removedChanges == null || removedChanges.isEmpty()) {
                continue;
            }

            TarifficationChanges first = removedChanges.get(0);
            String teacherKey = normalize(first.getFioTeacher());
            LocalDate changeDate = first.getChangeDate() == null ? null : first.getChangeDate().toLocalDate();
            if (teacherKey == null || changeDate == null) {
                continue;
            }

            TeacherDateKey mapKey = new TeacherDateKey(teacherKey, changeDate);
            if (result.containsKey(mapKey)) {
                continue;
            }

            List<ManualLoadEntry> removedRows = removedChanges.stream()
                    .map(ch -> manualRowFromChange(ch, changeDate.minusDays(1)))
                    .toList();
            if (removedRows.isEmpty()) {
                continue;
            }

            LocalDateTime latestChangeAt = removedChanges.stream()
                    .map(TarifficationChanges::getChangeDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(changeDate.atStartOfDay());

            String displayName = Optional.ofNullable(displayByTeacher.get(teacherKey))
                    .orElse(first.getFioTeacher());

            result.put(mapKey, new TeacherChangeAggregate(
                    displayName,
                    changeDate,
                    latestChangeAt,
                    removedRows,
                    Set.of(),
                    rowsKeySet(removedRows),
                    false
            ));
        }

        return result;
    }

    private ManualLoadEntry manualRowFromChange(TarifficationChanges change, LocalDate removalToDate) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setFioTeacher(change.getFioTeacher());
        row.setSubjectName(change.getSubjectName());
        row.setClassName(change.getClassName());
        row.setLoad(change.getLoad());
        row.setLoadFromDate(null);
        row.setLoadToDate(removalToDate);
        row.setGroupNameEducationalPlan(change.getGroupNameEducationalPlan());
        row.setNumberSchoolBuilding(change.getNumberSchoolBuilding());
        return row;
    }

    private List<ManualLoadEntry> normalizeActiveRows(List<ManualLoadEntry> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, ManualLoadEntry> bestByTransferKey = new LinkedHashMap<>();
        for (ManualLoadEntry row : rows) {
            if (row == null) {
                continue;
            }

            String key = transferKeyOf(row);
            ManualLoadEntry existing = bestByTransferKey.get(key);
            if (existing == null || isBetterActiveRow(row, existing)) {
                bestByTransferKey.put(key, row);
            }
        }

        return new ArrayList<>(bestByTransferKey.values());
    }

    private boolean isBetterActiveRow(ManualLoadEntry candidate, ManualLoadEntry current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }

        LocalDate candidateTo = candidate.getLoadToDate();
        LocalDate currentTo = current.getLoadToDate();

        if (candidateTo != null && currentTo != null && !candidateTo.equals(currentTo)) {
            return candidateTo.isAfter(currentTo);
        }
        if (candidateTo != null && currentTo == null) {
            return true;
        }
        if (candidateTo == null && currentTo != null) {
            return false;
        }

        LocalDate candidateFrom = candidate.getLoadFromDate();
        LocalDate currentFrom = current.getLoadFromDate();

        if (candidateFrom != null && currentFrom != null && !candidateFrom.equals(currentFrom)) {
            return candidateFrom.isAfter(currentFrom);
        }
        if (candidateFrom != null && currentFrom == null) {
            return true;
        }

        return false;
    }

    private String memoCompareKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                String.valueOf(row.getLoad() == null ? 0 : row.getLoad()));
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
        String rowKey = memoRowKey(row);
        if (aggregate.removedRowKeys().contains(rowKey)) return "Снять";
        if (aggregate.addedRowKeys().contains(rowKey)) return "Добавить";
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
        row.setNumberSchoolBuilding(source.getNumberSchoolBuilding());
        return row;
    }

    private List<ManualLoadEntry> mergeRowsForMemo(List<ManualLoadEntry> activeRows,
                                                   List<ManualLoadEntry> removedRows,
                                                   List<ManualLoadEntry> addedRows) {
        LinkedHashMap<String, ManualLoadEntry> merged = new LinkedHashMap<>();

        for (ManualLoadEntry row : Optional.ofNullable(activeRows).orElseGet(List::of)) {
            merged.put(memoRowKey(row), row);
        }
        for (ManualLoadEntry row : Optional.ofNullable(removedRows).orElseGet(List::of)) {
            merged.putIfAbsent(memoRowKey(row), row);
        }
        for (ManualLoadEntry row : Optional.ofNullable(addedRows).orElseGet(List::of)) {
            merged.putIfAbsent(memoRowKey(row), row);
        }

        return new ArrayList<>(merged.values());
    }

    private Map<String, Integer> countRows(List<ManualLoadEntry> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ManualLoadEntry row : Optional.ofNullable(rows).orElseGet(List::of)) {
            counts.merge(keyOf(row), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> diffCounts(Map<String, Integer> minuend, Map<String, Integer> subtrahend) {
        Map<String, Integer> diff = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : minuend.entrySet()) {
            int left = entry.getValue() == null ? 0 : entry.getValue();
            int right = subtrahend.getOrDefault(entry.getKey(), 0);
            if (left > right) {
                diff.put(entry.getKey(), left - right);
            }
        }
        return diff;
    }

    private Map<String, Integer> filterCountsByShortKeys(Map<String, Integer> counts, Set<String> allowedShortKeys) {
        if (counts == null || counts.isEmpty() || allowedShortKeys == null || allowedShortKeys.isEmpty()) {
            return counts == null ? Map.of() : counts;
        }
        return counts.entrySet().stream()
                .filter(entry -> allowedShortKeys.contains(shortKeyFromTransferKey(entry.getKey())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private List<ManualLoadEntry> selectRowsByCount(List<ManualLoadEntry> sourceRows, Map<String, Integer> requiredCounts) {
        if (requiredCounts == null || requiredCounts.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> remaining = new HashMap<>(requiredCounts);
        List<ManualLoadEntry> selected = new ArrayList<>();
        for (ManualLoadEntry row : Optional.ofNullable(sourceRows).orElseGet(List::of)) {
            String key = keyOf(row);
            int count = remaining.getOrDefault(key, 0);
            if (count <= 0) {
                continue;
            }
            selected.add(row);
            if (count == 1) {
                remaining.remove(key);
            } else {
                remaining.put(key, count - 1);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        return selected;
    }

    private Set<String> rowsKeySet(List<ManualLoadEntry> rows) {
        return Optional.ofNullable(rows).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .map(this::memoRowKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

            paragraph(doc,
                    "В связи с производственной необходимостью направляю на утверждение изменение учебной нагрузки.",
                    false, ParagraphAlignment.BOTH, 12, 0, 120, 420);

            if (aggregate.onlyAdditions()) {
                paragraph(doc,
                        "С " + RU_DATE.format(aggregate.startDate())
                                + " установить учебную нагрузку на учебный год сотруднику "
                                + teacherDative + " в следующем объеме:",
                        false, ParagraphAlignment.BOTH, 12, 0, 120, 420);
            } else {
                paragraph(doc,
                        "С " + RU_DATE.format(aggregate.startDate())
                                + " считать актуальной следующую учебную нагрузку "
                                + teacherDative + ":",
                        false, ParagraphAlignment.BOTH, 12, 0, 120, 420);
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

        LinkedHashMap<String, DisplayRow> displayRows = new LinkedHashMap<>();
        for (ManualLoadEntry row : safeRows) {
            if (row == null) {
                continue;
            }

            String status = resolveStatus(aggregate, row);
            String key = String.join("|",
                    safe(row.getSubjectName()),
                    safe(row.getClassName()),
                    String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                    safe(status));

            displayRows.putIfAbsent(key, new DisplayRow(
                    safeDocText(row.getSubjectName()),
                    safeDocText(row.getClassName()),
                    row.getLoad() == null ? 0 : row.getLoad(),
                    status
            ));
        }

        log.debug("displayRows for memo={}", displayRows.values());

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
        for (DisplayRow row : displayRows.values()) {
            XWPFTableRow tr = table.createRow();
            setCellText(tr.getCell(0), row.subjectName(), false);
            setCellText(tr.getCell(1), row.className(), false);
            setCellText(tr.getCell(2), String.valueOf(row.load()), false);

            if (!"Снять".equalsIgnoreCase(row.status())) {
                totalRemainingHours += row.load();
            }

            if (!newEmployeeMode) {
                setCellText(tr.getCell(3), row.status(), false);
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
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9))
                .map(StudyPeriodSettingService.DateRange::startDate)
                .orElse(LocalDate.of(LocalDate.now().getYear(), 9, 1));
    }

    private LocalDate academicEnd() {
        var ranges = studyPeriodSettingService.rangesByKey();
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9))
                .map(StudyPeriodSettingService.DateRange::endDate)
                .orElse(LocalDate.of(LocalDate.now().plusYears(1).getYear(), 5, 31));
    }

    private String keyOf(TarifficationChanges ch) {
        return String.join("|", safe(ch.getSubjectName()), safe(ch.getClassName()), String.valueOf(ch.getLoad()));
    }

    private String keyOf(ManualLoadEntry row) {
        return shortKeyOf(row);
    }

    private String transferKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                safe(row.getNumberSchoolBuilding()),
                safe(row.getGroupNameEducationalPlan()),
                String.valueOf(row.getEducationLevel()),
                String.valueOf(row.getStudyPeriod()));
    }

    private String shortKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                String.valueOf(row.getLoad() == null ? 0 : row.getLoad()));
    }

    private String shortKeyOf(TarifficationChanges ch) {
        return String.join("|",
                safe(ch.getSubjectName()),
                safe(ch.getClassName()),
                String.valueOf(ch.getLoad() == null ? 0 : ch.getLoad()));
    }

    private String shortKeyFromTransferKey(String transferKey) {
        String safeValue = String.valueOf(transferKey == null ? "" : transferKey);
        String[] parts = safeValue.split("\\|", -1);
        if (parts.length < 3) {
            return safeValue;
        }
        return String.join("|", parts[0], parts[1], parts[2]);
    }

    private String memoRowKey(ManualLoadEntry row) {
        return String.join("|",
                keyOf(row),
                String.valueOf(row.getLoadFromDate() == null ? "" : row.getLoadFromDate()),
                String.valueOf(row.getLoadToDate() == null ? "" : row.getLoadToDate()));
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

    private Map<String, Object> debugRow(ManualLoadEntry row) {
        return Map.ofEntries(
                Map.entry("teacher", String.valueOf(row.getFioTeacher())),
                Map.entry("subject", String.valueOf(row.getSubjectName())),
                Map.entry("class", String.valueOf(row.getClassName())),
                Map.entry("load", String.valueOf(row.getLoad())),
                Map.entry("from", String.valueOf(row.getLoadFromDate())),
                Map.entry("to", String.valueOf(row.getLoadToDate())),
                Map.entry("memoCompareKey", memoCompareKeyOf(row)),
                Map.entry("transferKey", transferKeyOf(row)),
                Map.entry("building", String.valueOf(row.getNumberSchoolBuilding())),
                Map.entry("groupPlan", String.valueOf(row.getGroupNameEducationalPlan())),
                Map.entry("educationLevel", String.valueOf(row.getEducationLevel())),
                Map.entry("studyPeriod", String.valueOf(row.getStudyPeriod()))
        );
    }

    private Map<String, Object> debugChange(TarifficationChanges ch) {
        return Map.ofEntries(
                Map.entry("teacher", String.valueOf(ch.getFioTeacher())),
                Map.entry("subject", String.valueOf(ch.getSubjectName())),
                Map.entry("class", String.valueOf(ch.getClassName())),
                Map.entry("load", String.valueOf(ch.getLoad())),
                Map.entry("type", String.valueOf(ch.getChangeType())),
                Map.entry("dateTime", String.valueOf(ch.getChangeDate())),
                Map.entry("shortKey", shortKeyOf(ch))
        );
    }

    private Map<String, Object> debugRowWithStatus(ManualLoadEntry row) {
        return Map.ofEntries(
                Map.entry("subject", String.valueOf(row.getSubjectName())),
                Map.entry("class", String.valueOf(row.getClassName())),
                Map.entry("load", String.valueOf(row.getLoad())),
                Map.entry("from", String.valueOf(row.getLoadFromDate())),
                Map.entry("to", String.valueOf(row.getLoadToDate())),
                Map.entry("transferKey", transferKeyOf(row))
        );
    }

    private Map<String, Object> debugRowWithResolvedStatus(ManualLoadEntry row,
                                                           List<ManualLoadEntry> removedRows,
                                                           List<ManualLoadEntry> addedRows) {
        Set<String> removedKeys = rowsKeySet(removedRows);
        Set<String> addedKeys = rowsKeySet(addedRows);
        String rowKey = memoRowKey(row);

        String status = "";
        if (removedKeys.contains(rowKey)) {
            status = "Снять";
        } else if (addedKeys.contains(rowKey)) {
            status = "Добавить";
        }

        return Map.ofEntries(
                Map.entry("subject", String.valueOf(row.getSubjectName())),
                Map.entry("class", String.valueOf(row.getClassName())),
                Map.entry("load", String.valueOf(row.getLoad())),
                Map.entry("from", String.valueOf(row.getLoadFromDate())),
                Map.entry("to", String.valueOf(row.getLoadToDate())),
                Map.entry("status", status),
                Map.entry("memoCompareKey", memoCompareKeyOf(row)),
                Map.entry("transferKey", transferKeyOf(row))
        );
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
            Set<String> addedRowKeys,
            Set<String> removedRowKeys,
            boolean onlyAdditions
    ) {
        private TeacherChangeAggregate(
                String teacherDisplay,
                LocalDate startDate,
                LocalDateTime latestChangeAt,
                List<ManualLoadEntry> rows,
                Set<String> activeKeys,
                Set<String> addedKeys,
                Set<String> removedKeys,
                boolean onlyAdditions
        ) {
            this(teacherDisplay, startDate, latestChangeAt, rows, addedKeys, removedKeys, onlyAdditions);
        }
    }

    private record DisplayRow(
            String subjectName,
            String className,
            int load,
            String status
    ) {
    }
}
