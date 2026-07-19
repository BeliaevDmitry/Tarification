package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.dto.ServiceMemoSettingsDto;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.model.EmploymentContract;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.EmploymentContractRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.ServiceMemoService;
import org.school.personalLoad.service.ServiceMemoSettingsService;
import org.school.personalLoad.service.HrDocumentService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.xmlbeans.XmlCursor;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ServiceMemoServiceImpl implements ServiceMemoService {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SELECTION_SEPARATOR = "::";
    private static final String DEFAULT_MEMO_TEMPLATE_DOCX = "templates/service-memo/memo-demo.docx";
    private static final String PLACEHOLDER_DIRECTOR_TITLE = "{DIRECTOR_TITLE}";
    private static final String PLACEHOLDER_DIRECTOR_NAME = "{DIRECTOR_NAME}";
    private static final String PLACEHOLDER_AUTHOR = "{AUTHOR}";
    private static final String PLACEHOLDER_FIO = "{FIO}";
    private static final String PLACEHOLDER_START_DATE = "{START_DATE}";
    private static final String PLACEHOLDER_TOTAL_HOURS = "{TOTAL_HOURS}";
    private static final String PLACEHOLDER_CREATED_DATE = "{CREATED_DATE}";
    private static final String PLACEHOLDER_TABLE = "{TABLE}";
    private static final String PLACEHOLDER_RATIONALE = "{RATIONALE}";
    private static final String OLD_RATIONALE_INTRO = "В связи с производственной необходимостью направляю на утверждение изменение учебной нагрузки.";
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    private final org.school.personalLoad.dao.TarifficationChangesDAO changesDAO;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ServiceMemoRepository serviceMemoRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;
    private final ServiceMemoSettingsService serviceMemoSettingsService;
    private final EmploymentContractRepository employmentContractRepository;
    private final HrDocumentService hrDocumentService;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.PendingTeacher> findPendingTeachers(String academicYear) {
        Map<TeacherDateKey, TeacherChangeAggregate> candidates = loadTeacherChangesByDate(academicYear);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, ServiceMemo> latestMemoBySelection = latestMemoBySelectionKey(academicYear);
        return candidates.entrySet().stream()
                .filter(entry -> {
                    String selectionKey = selectionKey(entry.getKey());
                    ServiceMemo latest = latestMemoBySelection.get(selectionKey);
                    if (latest == null) {
                        return true;
                    }
                    TeacherChangeAggregate aggregate = entry.getValue();
                    String currentSignature = aggregateSignature(aggregate);
                    if (signatureMatches(latest.getLoadSignature(), currentSignature)) {
                        return false;
                    }
                    return hasChangesAfterMemo(aggregate, latest);
                })
                .map(entry -> toPendingDto(selectionKey(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(ServiceMemoDtos.PendingTeacher::getStartDate)
                        .thenComparing(ServiceMemoDtos.PendingTeacher::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findProcessed(String academicYear) {
        List<ServiceMemo.Status> visible = List.of(ServiceMemo.Status.PROCESSED, ServiceMemo.Status.RECEIVED_BY_HR, ServiceMemo.Status.EXECUTED);
        java.util.List<ServiceMemo> items = (academicYear == null || academicYear.isBlank())
                ? serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(visible)
                : serviceMemoRepository.findAllByAcademicYearAndStatusInOrderByCreatedAtDesc(academicYear, visible);
        return items
                .stream()
                .map(this::toProcessedDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findForHr(String academicYear) {
        if (academicYear == null || academicYear.isBlank()) return List.of();
        return serviceMemoRepository.findAllByAcademicYearOrderByCreatedAtDesc(academicYear).stream()
                .filter(memo -> memo.getStatus() != ServiceMemo.Status.ARCHIVED)
                .map(this::toProcessedDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceMemoDtos.ProcessedMemo> findArchived(String academicYear) {
        java.util.List<ServiceMemo> items = (academicYear == null || academicYear.isBlank())
                ? serviceMemoRepository.findAllByStatusOrderByCreatedAtDesc(ServiceMemo.Status.ARCHIVED)
                : serviceMemoRepository.findAllByAcademicYearAndStatusOrderByCreatedAtDesc(academicYear, ServiceMemo.Status.ARCHIVED);
        return items
                .stream()
                .map(this::toProcessedDto)
                .toList();
    }

    @Override
    public List<ServiceMemoDtos.ProcessedMemo> generateForTeachers(String academicYear, List<String> fioTeachers, String createdBy) {
        List<String> requested = Optional.ofNullable(fioTeachers).orElseGet(List::of).stream()
                .map(v -> String.valueOf(v == null ? "" : v).trim())
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одну запись со списком изменений");
        }

        Map<TeacherDateKey, TeacherChangeAggregate> allPending = loadTeacherChangesByDate(academicYear);
        Map<String, ServiceMemo> latestMemoBySelection = latestMemoBySelectionKey(academicYear);
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

                if (latest != null) {
                    if (signatureMatches(latest.getLoadSignature(), signature)) {
                        continue;
                    }
                    if (!hasChangesAfterMemo(aggregate, latest)) {
                        continue;
                    }
                }

                TeacherDirectoryEntry teacher = resolveTeacher(aggregate);
                EmploymentContract contract = resolvePrimaryContract(teacher);
                ServiceMemo entity = new ServiceMemo();
                entity.setAcademicYear(resolveMemoAcademicYear(academicYear));
                entity.setFioTeacher(aggregate.teacherDisplay());
                entity.setTeacherId(teacher.getId());
                entity.setContractId(contract.getId());
                entity.setChangeStartDate(aggregate.startDate());
                entity.setCreatedBy(createdBy);
                entity.setGeneratedFilename("служебка по нагрузке " + safeName(aggregate.teacherDisplay())
                        + " " + aggregate.startDate() + ".docx");
                entity.setLoadSignature(signature);
                entity.setBeforeSnapshotJson(loadSnapshot(aggregate, true));
                entity.setAfterSnapshotJson(loadSnapshot(aggregate, false));
                entity.setGeneratedDocument(buildDocx(
                        aggregate.teacherDisplay(),
                        aggregate,
                        createdBy,
                        teacherDativeByFio
                ));

                ServiceMemo saved = serviceMemoRepository.save(entity);
                hrDocumentService.createLoadChangeDraft(saved, contract, createdBy);
                created.add(saved);
                latestMemoBySelection.put(selectionKey, saved);
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
        if (memo.getStatus() != ServiceMemo.Status.RECEIVED_BY_HR && memo.getStatus() != ServiceMemo.Status.EXECUTED)
            throw new IllegalStateException("Служебную записку можно архивировать после получения кадрами");
        memo.setStatus(ServiceMemo.Status.ARCHIVED);
        memo.setArchivedAt(LocalDateTime.now());
        return serviceMemoRepository.save(memo);
    }

    @Override
    public ServiceMemo receiveByHr(Long id, String username) {
        ServiceMemo memo = getById(id);
        if (memo.getStatus() == ServiceMemo.Status.ARCHIVED || memo.getStatus() == ServiceMemo.Status.ANNULLED) {
            throw new IllegalStateException("Нельзя принять архивную или аннулированную служебную записку");
        }
        if (memo.getStatus() != ServiceMemo.Status.PROCESSED && memo.getStatus() != ServiceMemo.Status.RECEIVED_BY_HR)
            throw new IllegalStateException("Служебная записка ещё не выпущена");
        TeacherDirectoryEntry teacher = memo.getTeacherId() == null
                ? teacherDirectoryRepository.findByFioTeacherIgnoreCase(memo.getFioTeacher())
                    .orElseThrow(() -> new IllegalStateException("Не найден ID работника: " + memo.getFioTeacher()))
                : teacherDirectoryRepository.findById(memo.getTeacherId())
                    .orElseThrow(() -> new IllegalStateException("Работник служебной записки не найден"));
        EmploymentContract contract = memo.getContractId() == null
                ? resolvePrimaryContract(teacher)
                : employmentContractRepository.findById(memo.getContractId())
                    .filter(item -> Objects.equals(item.getTeacherId(),teacher.getId()))
                    .orElseGet(() -> resolvePrimaryContract(teacher));
        memo.setTeacherId(teacher.getId()); memo.setContractId(contract.getId());
        hrDocumentService.ensureLoadChangeDraft(memo,contract,username);
        memo.setStatus(ServiceMemo.Status.RECEIVED_BY_HR);
        memo.setReceivedAt(LocalDateTime.now());
        memo.setReceivedBy(username);
        ServiceMemo saved = serviceMemoRepository.save(memo);
        hrDocumentService.onLoadMemoReceived(saved);
        return saved;
    }

    @Override
    public ServiceMemo annul(Long id, String reason, String username) {
        ServiceMemo memo = getById(id);
        memo.setStatus(ServiceMemo.Status.ANNULLED);
        memo.setAnnulReason(String.valueOf(reason == null ? "" : reason).trim());
        if (memo.getAnnulReason().isBlank()) throw new IllegalArgumentException("Укажите причину аннулирования");
        memo.setAnnulledAt(LocalDateTime.now());
        memo.setAnnulledBy(username);
        ServiceMemo saved = serviceMemoRepository.save(memo);
        hrDocumentService.onLoadMemoAnnulled(saved);
        return saved;
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
                .teacherId(memo.getTeacherId())
                .contractId(memo.getContractId())
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
                .memoType(aggregate.memoReason() == MemoReason.NEW_EMPLOYEE ? "NEW" : "CHANGED")
                .rows(rows)
                .totalHours(total)
                .build();
    }

    private Map<TeacherDateKey, TeacherChangeAggregate> loadTeacherChangesByDate(String academicYear) {
        Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges =
                (academicYear == null || academicYear.isBlank())
                        ? studyPeriodSettingService.rangesByKey()
                        : studyPeriodSettingService.rangesByKey(academicYear);
        LocalDate start = academicStart(periodRanges);
        LocalDate end = academicEnd(periodRanges);

        List<ManualLoadEntry> sourceRows = (academicYear == null || academicYear.isBlank())
                ? manualLoadEntryRepository.findAll()
                : manualLoadEntryRepository.findAllByAcademicYear(academicYear);
        List<ManualLoadEntry> periodRows = sourceRows.stream()
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
                LocalDate rowStartDate = curriculumAwareStartDate(row, periodRanges);
                if (!rowStartDate.isBefore(start)
                        && !rowStartDate.isAfter(end)
                        && !rowStartDate.isEqual(start)) {
                    dates.add(rowStartDate);
                }
                LocalDate nextDay = curriculumAwareEndDate(row, periodRanges).plusDays(1);
                if (!nextDay.isBefore(start) && !nextDay.isAfter(end) && !hasFutureContinuation(rows, row, nextDay)) {
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
                        .filter(row -> isActiveAt(row, changeDate.minusDays(1), periodRanges))
                        .map(row -> copyWithCurriculumBoundaries(row, periodRanges))
                        .toList());
                List<ManualLoadEntry> rowsOnDate = normalizeActiveRows(teacherRows.stream()
                        .filter(row -> isActiveAt(row, changeDate, periodRanges))
                        .map(row -> copyWithCurriculumBoundaries(row, periodRanges))
                        .toList());

                CurriculumTransition curriculumTransition = curriculumTransition(
                        changeDate,
                        teacherRows,
                        rowsBeforeDate,
                        rowsOnDate,
                        periodRanges
                );
                List<ManualLoadEntry> rowsBeforeComparison = curriculumTransition.rowsBeforeComparison();

                Map<String, Integer> beforeCounts = countRows(rowsBeforeComparison);
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
                        removedCounts = filterCountsByShortKeys(
                                removedCounts,
                                removedShortKeys,
                                curriculumTransition.preservedShortKeys()
                        );
                    } else if (!anyShortKeys.isEmpty()) {
                        removedCounts = filterCountsByShortKeys(
                                removedCounts,
                                anyShortKeys,
                                curriculumTransition.preservedShortKeys()
                        );
                    }
                    if (!addedShortKeys.isEmpty()) {
                        addedCounts = filterCountsByShortKeys(
                                addedCounts,
                                addedShortKeys,
                                curriculumTransition.preservedShortKeys()
                        );
                    } else if (!anyShortKeys.isEmpty()) {
                        addedCounts = filterCountsByShortKeys(
                                addedCounts,
                                anyShortKeys,
                                curriculumTransition.preservedShortKeys()
                        );
                    }
                }
                if (removedCounts.isEmpty() && addedCounts.isEmpty()) {
                    continue;
                }

                List<ManualLoadEntry> removedRows = selectRowsByCount(rowsBeforeComparison, removedCounts).stream()
                        .map(row -> copyForRemoval(row, changeDate.minusDays(1)))
                        .toList();
                List<ManualLoadEntry> addedRows = selectRowsByCount(rowsOnDate, addedCounts);
                List<ManualLoadEntry> rowsForMemo = mergeRowsForMemo(rowsOnDate, removedRows, addedRows);
                if (rowsForMemo.isEmpty()) {
                    continue;
                }
                if (!rowsForMemo.isEmpty() && removedRows.size() == rowsForMemo.size()) {
                    log.warn("memo-debug all rows marked as removed for teacher={} date={} before={} onDate={} removed={} added={} dayChanges={}",
                            teacherKey,
                            changeDate,
                            rowsBeforeComparison.stream().map(this::debugRow).toList(),
                            rowsOnDate.stream().map(this::debugRow).toList(),
                            removedRows.stream().map(this::debugRow).toList(),
                            addedRows.stream().map(this::debugRow).toList(),
                            dayChanges.stream().map(this::debugChange).toList());
                } else if (log.isDebugEnabled()) {
                    log.debug("memo-debug aggregate teacher={} date={} rowsForMemo={} removed={} added={}",
                            teacherKey,
                            changeDate,
                            rowsForMemo.stream()
                                    .map(row -> debugRowWithResolvedStatus(row, removedRows, addedRows))
                                    .toList(),
                            removedRows.size(),
                            addedRows.size());
                }

                String displayName = displayByTeacher.getOrDefault(teacherKey, rowsForMemo.get(0).getFioTeacher());
                MemoReason reason = inferMemoReason(
                        teacherKey,
                        changeDate,
                        teacherRows,
                        rowsBeforeComparison,
                        removedRows,
                        addedRows,
                        periodRows,
                        periodChanges
                );

                result.put(new TeacherDateKey(teacherKey, changeDate), new TeacherChangeAggregate(
                        displayName,
                        changeDate,
                        latestChangeAt,
                        rowsForMemo,
                        rowsKeySet(addedRows),
                        rowsKeySet(removedRows),
                        reason
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

            List<ManualLoadEntry> teacherRows = rowsByTeacher.getOrDefault(teacherKey, List.of());
            List<ManualLoadEntry> removedRows = removedChanges.stream()
                    .filter(ch -> !hasActiveEquivalentOnDate(teacherRows, ch, changeDate))
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
                    MemoReason.PRODUCTION_NECESSITY
            ));
            log.warn("memo-debug fallback aggregate from removed history teacher={} date={} removedChanges={} reconstructedRows={}",
                    teacherKey,
                    changeDate,
                    removedChanges.stream().map(this::debugChange).toList(),
                    removedRows.stream().map(this::debugRow).toList());
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

    private MemoReason inferMemoReason(String teacherKey,
                                       LocalDate changeDate,
                                       List<ManualLoadEntry> teacherRows,
                                       List<ManualLoadEntry> rowsBeforeDate,
                                       List<ManualLoadEntry> removedRows,
                                       List<ManualLoadEntry> addedRows,
                                       List<ManualLoadEntry> periodRows,
                                       List<TarifficationChanges> periodChanges) {
        if (hasTransferPair(teacherKey, changeDate, addedRows, removedRows, periodRows, periodChanges)) {
            return MemoReason.PRODUCTION_NECESSITY;
        }
        if (hasCurriculumPlanSignal(teacherKey, changeDate, teacherRows, removedRows, addedRows, periodChanges)) {
            return MemoReason.CURRICULUM_PLAN_ALIGNMENT;
        }
        boolean onlyAdditions = (removedRows == null || removedRows.isEmpty())
                && addedRows != null && !addedRows.isEmpty();
        if (onlyAdditions && (rowsBeforeDate == null || rowsBeforeDate.isEmpty())) {
            return MemoReason.NEW_EMPLOYEE;
        }
        return MemoReason.PRODUCTION_NECESSITY;
    }

    private boolean hasCurriculumPlanSignal(String teacherKey,
                                            LocalDate changeDate,
                                            List<ManualLoadEntry> teacherRows,
                                            List<ManualLoadEntry> removedRows,
                                            List<ManualLoadEntry> addedRows,
                                            List<TarifficationChanges> periodChanges) {
        Set<String> changedSubjectClasses = new LinkedHashSet<>();
        Optional.ofNullable(removedRows).orElseGet(List::of).stream()
                .map(this::subjectClassKeyOf)
                .forEach(changedSubjectClasses::add);
        Optional.ofNullable(addedRows).orElseGet(List::of).stream()
                .map(this::subjectClassKeyOf)
                .forEach(changedSubjectClasses::add);
        if (changedSubjectClasses.isEmpty()) {
            return false;
        }

        boolean sameTeacherContinuation = Optional.ofNullable(teacherRows).orElseGet(List::of).stream()
                .filter(row -> row.getLoadToDate() != null && row.getLoadToDate().isBefore(changeDate))
                .map(this::subjectClassKeyOf)
                .anyMatch(changedSubjectClasses::contains);
        if (sameTeacherContinuation) {
            return true;
        }

        return Optional.ofNullable(periodChanges).orElseGet(List::of).stream()
                .filter(ch -> Objects.equals(teacherKey, normalize(ch.getFioTeacher())))
                .filter(ch -> ch.getChangeDate() != null && !ch.getChangeDate().toLocalDate().isAfter(changeDate))
                .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.ADDED
                        || ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED
                        || ch.getChangeType() == TarifficationChanges.ChangeType.MODIFIED)
                .map(this::subjectClassKeyOf)
                .anyMatch(changedSubjectClasses::contains);
    }

    private boolean hasTransferPair(String teacherKey,
                                    LocalDate changeDate,
                                    List<ManualLoadEntry> addedRows,
                                    List<ManualLoadEntry> removedRows,
                                    List<ManualLoadEntry> periodRows,
                                    List<TarifficationChanges> periodChanges) {
        Set<String> addedKeys = Optional.ofNullable(addedRows).orElseGet(List::of).stream()
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());
        Set<String> removedKeys = Optional.ofNullable(removedRows).orElseGet(List::of).stream()
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());
        if (!Collections.disjoint(addedKeys, removedKeys)) {
            return true;
        }

        Set<String> ownKeys = new LinkedHashSet<>();
        ownKeys.addAll(addedKeys);
        ownKeys.addAll(removedKeys);
        if (ownKeys.isEmpty()) {
            return false;
        }

        Set<String> otherRowsEndingBefore = Optional.ofNullable(periodRows).orElseGet(List::of).stream()
                .filter(row -> !Objects.equals(teacherKey, normalize(row.getFioTeacher())))
                .filter(row -> Objects.equals(row.getLoadToDate(), changeDate.minusDays(1)))
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());
        Set<String> otherRowsStartingOnDate = Optional.ofNullable(periodRows).orElseGet(List::of).stream()
                .filter(row -> !Objects.equals(teacherKey, normalize(row.getFioTeacher())))
                .filter(row -> Objects.equals(row.getLoadFromDate(), changeDate))
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());
        if (addedKeys.stream().anyMatch(otherRowsEndingBefore::contains)
                || removedKeys.stream().anyMatch(otherRowsStartingOnDate::contains)) {
            return true;
        }

        Set<String> otherRemoved = Optional.ofNullable(periodChanges).orElseGet(List::of).stream()
                .filter(ch -> ch.getChangeDate() != null && Objects.equals(changeDate, ch.getChangeDate().toLocalDate()))
                .filter(ch -> !Objects.equals(teacherKey, normalize(ch.getFioTeacher())))
                .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.REMOVED)
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());
        Set<String> otherAdded = Optional.ofNullable(periodChanges).orElseGet(List::of).stream()
                .filter(ch -> ch.getChangeDate() != null && Objects.equals(changeDate, ch.getChangeDate().toLocalDate()))
                .filter(ch -> !Objects.equals(teacherKey, normalize(ch.getFioTeacher())))
                .filter(ch -> ch.getChangeType() == TarifficationChanges.ChangeType.ADDED)
                .map(this::shortKeyOf)
                .collect(Collectors.toSet());

        return addedKeys.stream().anyMatch(otherRemoved::contains)
                || removedKeys.stream().anyMatch(otherAdded::contains)
                || ownKeys.stream().anyMatch(key -> otherRemoved.contains(key) || otherAdded.contains(key));
    }

    private boolean hasFutureContinuation(List<ManualLoadEntry> rows, ManualLoadEntry source, LocalDate afterDate) {
        if (source == null || afterDate == null) {
            return false;
        }
        String sourceKey = subjectClassKeyOf(source);
        return Optional.ofNullable(rows).orElseGet(List::of).stream()
                .filter(row -> row != source)
                .filter(row -> row.getLoadFromDate() != null)
                .filter(row -> !row.getLoadFromDate().isBefore(afterDate))
                .map(this::subjectClassKeyOf)
                .anyMatch(sourceKey::equals);
    }

    private CurriculumTransition curriculumTransition(
            LocalDate changeDate,
            List<ManualLoadEntry> teacherRows,
            List<ManualLoadEntry> rowsBeforeDate,
            List<ManualLoadEntry> rowsOnDate,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        List<ManualLoadEntry> comparisonRows = new ArrayList<>(
                Optional.ofNullable(rowsBeforeDate).orElseGet(List::of)
        );
        Set<String> preservedShortKeys = new LinkedHashSet<>();

        for (ManualLoadEntry secondHalfRow : Optional.ofNullable(rowsOnDate).orElseGet(List::of)) {
            if (secondHalfRow == null || secondHalfRow.getStudyPeriod() != StudyPeriod.H2) {
                continue;
            }

            StudyPeriodSettingService.DateRange secondHalfRange = periodRangeForClass(
                    secondHalfRow.getClassName(),
                    StudyPeriod.H2,
                    periodRanges
            );
            if (secondHalfRange == null || !Objects.equals(secondHalfRange.startDate(), changeDate)) {
                continue;
            }

            StudyPeriodSettingService.DateRange firstHalfRange = periodRangeForClass(
                    secondHalfRow.getClassName(),
                    StudyPeriod.H1,
                    periodRanges
            );
            if (firstHalfRange == null || firstHalfRange.endDate() == null) {
                continue;
            }

            String transitionKey = curriculumContinuityKeyOf(secondHalfRow);
            List<ManualLoadEntry> predecessors = Optional.ofNullable(teacherRows).orElseGet(List::of).stream()
                    .filter(Objects::nonNull)
                    .filter(row -> row.getStudyPeriod() == StudyPeriod.H1)
                    .filter(row -> Objects.equals(transitionKey, curriculumContinuityKeyOf(row)))
                    .filter(row -> isActiveAt(row, firstHalfRange.endDate()))
                    .toList();
            if (predecessors.isEmpty()) {
                continue;
            }

            comparisonRows.addAll(predecessors);
            preservedShortKeys.add(shortKeyOf(secondHalfRow));
            predecessors.stream().map(this::shortKeyOf).forEach(preservedShortKeys::add);
        }

        return new CurriculumTransition(normalizeActiveRows(comparisonRows), preservedShortKeys);
    }

    private StudyPeriodSettingService.DateRange periodRangeForClass(
            String className,
            StudyPeriod studyPeriod,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        if (studyPeriod == null || periodRanges == null || periodRanges.isEmpty()) {
            return null;
        }
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        StudyPeriodSettingKey key;
        if (parallel == null || parallel <= 9) {
            key = studyPeriod == StudyPeriod.H2
                    ? StudyPeriodSettingKey.H2_1_9
                    : StudyPeriodSettingKey.H1_1_9;
        } else if (parallel == 10) {
            key = studyPeriod == StudyPeriod.H2
                    ? StudyPeriodSettingKey.H2_10
                    : StudyPeriodSettingKey.H1_10;
        } else {
            key = studyPeriod == StudyPeriod.H2
                    ? StudyPeriodSettingKey.H2_11
                    : StudyPeriodSettingKey.H1_11;
        }
        return periodRanges.get(key);
    }

    private LocalDate curriculumAwareStartDate(
            ManualLoadEntry row,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        LocalDate storedStart = row.getLoadFromDate();
        if (storedStart == null || row.getStudyPeriod() == null || row.getStudyPeriod() == StudyPeriod.YEAR) {
            return storedStart;
        }
        StudyPeriodSettingService.DateRange configuredRange = periodRangeForClass(
                row.getClassName(),
                row.getStudyPeriod(),
                periodRanges
        );
        if (configuredRange == null || configuredRange.startDate() == null) {
            return storedStart;
        }
        return storedStart.isBefore(configuredRange.startDate())
                ? configuredRange.startDate()
                : storedStart;
    }

    private LocalDate curriculumAwareEndDate(
            ManualLoadEntry row,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        LocalDate storedEnd = row.getLoadToDate();
        if (storedEnd == null || row.getStudyPeriod() == null || row.getStudyPeriod() == StudyPeriod.YEAR) {
            return storedEnd;
        }
        StudyPeriodSettingService.DateRange configuredRange = periodRangeForClass(
                row.getClassName(),
                row.getStudyPeriod(),
                periodRanges
        );
        if (configuredRange == null || configuredRange.endDate() == null) {
            return storedEnd;
        }
        return storedEnd.isAfter(configuredRange.endDate())
                ? configuredRange.endDate()
                : storedEnd;
    }

    private boolean isActiveAt(
            ManualLoadEntry row,
            LocalDate date,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        if (row == null || date == null) {
            return false;
        }
        LocalDate effectiveStart = curriculumAwareStartDate(row, periodRanges);
        LocalDate effectiveEnd = curriculumAwareEndDate(row, periodRanges);
        if (effectiveStart == null || effectiveEnd == null) {
            return false;
        }
        return !effectiveStart.isAfter(date) && !effectiveEnd.isBefore(date);
    }

    private ManualLoadEntry copyWithCurriculumBoundaries(
            ManualLoadEntry row,
            Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> periodRanges
    ) {
        LocalDate effectiveStart = curriculumAwareStartDate(row, periodRanges);
        LocalDate effectiveEnd = curriculumAwareEndDate(row, periodRanges);
        if (Objects.equals(effectiveStart, row.getLoadFromDate())
                && Objects.equals(effectiveEnd, row.getLoadToDate())) {
            return row;
        }
        return copyLoadRow(row, effectiveStart, effectiveEnd);
    }

    private String curriculumContinuityKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                safe(row.getNumberSchoolBuilding()),
                String.valueOf(row.getSchoolBuildingId()),
                String.valueOf(row.getClassId()),
                String.valueOf(row.getMetaGroupId()),
                safe(row.getGroupNameEducationalPlan()),
                String.valueOf(row.getCurriculumModuleId()),
                String.valueOf(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart()));
    }

    private String subjectClassKeyOf(ManualLoadEntry row) {
        return String.join("|", safe(row.getSubjectName()), safe(row.getClassName()));
    }

    private String subjectClassKeyOf(TarifficationChanges change) {
        return String.join("|", safe(change.getSubjectName()), safe(change.getClassName()));
    }

    private boolean hasActiveEquivalentOnDate(List<ManualLoadEntry> rows, TarifficationChanges change, LocalDate date) {
        String changeKey = shortKeyOf(change);
        return Optional.ofNullable(rows).orElseGet(List::of).stream()
                .filter(row -> isActiveAt(row, date))
                .map(this::shortKeyOf)
                .anyMatch(changeKey::equals);
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
                String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                safe(row.getNumberSchoolBuilding()),
                safe(row.getGroupNameEducationalPlan()),
                String.valueOf(row.getCurriculumModuleId()),
                String.valueOf(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart()));
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

    private Map<String, ServiceMemo> latestMemoBySelectionKey(String academicYear) {
        List<ServiceMemo.Status> completed = List.of(ServiceMemo.Status.PROCESSED, ServiceMemo.Status.RECEIVED_BY_HR,
                ServiceMemo.Status.EXECUTED, ServiceMemo.Status.ARCHIVED);
        List<ServiceMemo> source = (academicYear == null || academicYear.isBlank())
                ? serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(completed)
                : serviceMemoRepository.findAllByAcademicYearAndStatusInOrderByCreatedAtDesc(academicYear, completed);
        return source.stream()
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
        return copyLoadRow(source, source.getLoadFromDate(), removalToDate);
    }

    private ManualLoadEntry copyLoadRow(ManualLoadEntry source, LocalDate loadFromDate, LocalDate loadToDate) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setId(source.getId());
        row.setAcademicYear(source.getAcademicYear());
        row.setFioTeacher(source.getFioTeacher());
        row.setTeacherId(source.getTeacherId());
        row.setSubjectName(source.getSubjectName());
        row.setClassName(source.getClassName());
        row.setClassId(source.getClassId());
        row.setMetaGroupId(source.getMetaGroupId());
        row.setLoad(source.getLoad());
        row.setLoadFromDate(loadFromDate);
        row.setLoadToDate(loadToDate);
        row.setGroupNameEducationalPlan(source.getGroupNameEducationalPlan());
        row.setGroupLoad(source.getGroupLoad());
        row.setCurriculumModuleId(source.getCurriculumModuleId());
        row.setCurriculumPart(source.getCurriculumPart());
        row.setEducationLevel(source.getEducationLevel());
        row.setStudyPeriod(source.getStudyPeriod());
        row.setNumberSchoolBuilding(source.getNumberSchoolBuilding());
        row.setSchoolBuildingId(source.getSchoolBuildingId());
        row.setContinuityStatus(source.getContinuityStatus());
        return row;
    }

    private List<ManualLoadEntry> mergeRowsForMemo(List<ManualLoadEntry> activeRows,
                                                   List<ManualLoadEntry> removedRows,
                                                   List<ManualLoadEntry> addedRows) {
        LinkedHashMap<String, ManualLoadEntry> merged = new LinkedHashMap<>();

        for (ManualLoadEntry row : Optional.ofNullable(removedRows).orElseGet(List::of)) {
            merged.put(memoRowKey(row), row);
        }
        for (ManualLoadEntry row : Optional.ofNullable(activeRows).orElseGet(List::of)) {
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

    private Map<String, Integer> filterCountsByShortKeys(
            Map<String, Integer> counts,
            Set<String> allowedShortKeys,
            Set<String> preservedShortKeys
    ) {
        if (counts == null || counts.isEmpty() || allowedShortKeys == null || allowedShortKeys.isEmpty()) {
            return counts == null ? Map.of() : counts;
        }
        return counts.entrySet().stream()
                .filter(entry -> {
                    String shortKey = shortKeyFromTransferKey(entry.getKey());
                    return allowedShortKeys.contains(shortKey)
                            || Optional.ofNullable(preservedShortKeys).orElseGet(Set::of).contains(shortKey);
                })
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
        String teacherDative = Optional.ofNullable(teacherDativeByFio.get(normalize(fioTeacher)))
                .filter(value -> !value.isBlank())
                .orElse(fioTeacher);
        String schoolCode = System.getenv().getOrDefault("SCHOOL_CODE", "demo").toLowerCase(Locale.ROOT);
        String templatePath = String.format("templates/service-memo/memo-%s.docx", schoolCode);
        InputStream in = templateOrFallback(templatePath);
        if (in != null) {
            try (InputStream template = in; XWPFDocument doc = new XWPFDocument(template); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var memoSettings = serviceMemoSettingsService.get();
            replaceMemoTemplateAndInsertTable(doc, aggregate, createdBy, teacherDative, memoSettings);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("Не удалось применить шаблон служебки {}: {}. Используется встроенный шаблон.", templatePath, e.getMessage());
        }
        }

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var memoSettings = serviceMemoSettingsService.get();
            paragraph(doc, memoSettings.directorTitle(), false, ParagraphAlignment.RIGHT, 12, 0, 0, 0);
            paragraph(doc, memoSettings.directorName(), false, ParagraphAlignment.RIGHT, 12, 0, 160, 0);
            paragraph(doc, "от заместителя директора", false, ParagraphAlignment.RIGHT, 12, 0, 0, 0);
            paragraph(doc, createdBy, false, ParagraphAlignment.RIGHT, 12, 0, 220, 0);

            paragraph(doc, "СЛУЖЕБНАЯ ЗАПИСКА", true, ParagraphAlignment.CENTER, 14, 0, 220, 0);
            paragraph(doc, buildRationaleText(aggregate, teacherDative), false, ParagraphAlignment.BOTH, 12, 0, 120, 420);

            int totalRemainingHours = appendTable(doc, aggregate.rows(), aggregate, aggregate.newEmployeeMemo());
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


    private InputStream templateOrFallback(String templatePath) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(templatePath);
        if (in != null) return in;
        return getClass().getClassLoader().getResourceAsStream(DEFAULT_MEMO_TEMPLATE_DOCX);
    }

    private int replaceMemoTemplateAndInsertTable(XWPFDocument doc,
                                                  TeacherChangeAggregate aggregate,
                                                  String createdBy,
                                                  String teacherDative,
                                                  ServiceMemoSettingsDto memoSettings) {
        int totalHours = aggregate.rows().stream()
                .filter(Objects::nonNull)
                .map(row -> resolveStatus(aggregate, row).equalsIgnoreCase("Снять") ? 0 : Optional.ofNullable(row.getLoad()).orElse(0))
                .mapToInt(Integer::intValue)
                .sum();
        Map<String, String> placeholders = Map.of(
                PLACEHOLDER_DIRECTOR_TITLE, Optional.ofNullable(memoSettings.directorTitle()).orElse(""),
                PLACEHOLDER_DIRECTOR_NAME, Optional.ofNullable(memoSettings.directorName()).orElse(""),
                PLACEHOLDER_AUTHOR, Optional.ofNullable(createdBy).orElse(""),
                PLACEHOLDER_FIO, Optional.ofNullable(teacherDative).orElse(""),
                PLACEHOLDER_START_DATE, RU_DATE.format(aggregate.startDate()),
                PLACEHOLDER_TOTAL_HOURS, String.valueOf(totalHours),
                PLACEHOLDER_CREATED_DATE, RU_DATE.format(LocalDate.now()),
                PLACEHOLDER_RATIONALE, buildRationaleText(aggregate, teacherDative)
        );
        for (XWPFParagraph paragraph : allDocumentParagraphs(doc)) {
            replaceOldRationaleIntro(paragraph);
            replaceMemoParagraph(paragraph, placeholders);
        }
        insertMemoTable(doc, aggregate);
        return totalHours;
    }

    private void replaceOldRationaleIntro(XWPFParagraph paragraph) {
        if (!OLD_RATIONALE_INTRO.equals(paragraph.getText())) {
            return;
        }
        while (paragraph.getRuns().size() > 0) paragraph.removeRun(0);
    }

    private void replaceMemoParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) return;
        String replaced = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        if (!Objects.equals(text, replaced)) {
            while (paragraph.getRuns().size() > 0) paragraph.removeRun(0);
            XWPFRun run = paragraph.createRun();
            run.setText(replaced);
            run.setFontFamily("Times New Roman");
            run.setFontSize(12);
        }
    }

    private void insertMemoTable(XWPFDocument doc, TeacherChangeAggregate aggregate) {
        XWPFParagraph marker = allDocumentParagraphs(doc).stream()
                .filter(p -> p.getText() != null && p.getText().contains(PLACEHOLDER_TABLE))
                .findFirst()
                .orElseGet(doc::createParagraph);
        while (marker.getRuns().size() > 0) marker.removeRun(0);
        XmlCursor cursor = marker.getCTP().newCursor();
        XWPFTable table = doc.insertNewTbl(cursor);
        appendTableToExisting(table, aggregate.rows(), aggregate, aggregate.newEmployeeMemo());
    }

    private int appendTable(XWPFDocument doc, List<ManualLoadEntry> rows, TeacherChangeAggregate aggregate, boolean newEmployeeMode) {
        XWPFTable table = doc.createTable(1, newEmployeeMode ? 3 : 4);
        return appendTableToExisting(table, rows, aggregate, newEmployeeMode);
    }

    private int appendTableToExisting(XWPFTable table, List<ManualLoadEntry> rows, TeacherChangeAggregate aggregate, boolean newEmployeeMode) {
        table.setWidthType(TableWidthType.PCT);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);

        List<String> header = newEmployeeMode
                ? List.of("Предмет", "Класс", "Количество часов")
                : List.of("Предмет", "Класс", "Количество часов", "Статус");
        for (int i = 0; i < header.size(); i++) {
            setCellText(ensureCell(table.getRow(0), i), header.get(i), true);
        }

        LinkedHashMap<String, DisplayRow> rowsForDisplay = new LinkedHashMap<>();
        for (ManualLoadEntry row : Optional.ofNullable(rows).orElseGet(List::of)) {
            if (row == null) continue;
            String status = resolveStatus(aggregate, row);
            String key = String.join("|", safe(row.getSubjectName()), safe(row.getClassName()), String.valueOf(row.getLoad() == null ? 0 : row.getLoad()), safe(status));
            rowsForDisplay.putIfAbsent(key, new DisplayRow(safeDocText(row.getSubjectName()), safeDocText(row.getClassName()), row.getLoad() == null ? 0 : row.getLoad(), status));
        }

        int totalRemainingHours = 0;
        for (DisplayRow row : rowsForDisplay.values()) {
            XWPFTableRow tr = table.createRow();
            setCellText(ensureCell(tr, 0), row.subjectName(), false);
            setCellText(ensureCell(tr, 1), row.className(), false);
            setCellText(ensureCell(tr, 2), String.valueOf(row.load()), false);
            if (!"Снять".equalsIgnoreCase(row.status())) totalRemainingHours += row.load();
            if (!newEmployeeMode) setCellText(ensureCell(tr, 3), row.status(), false);
        }
        return totalRemainingHours;
    }

    private XWPFTableCell ensureCell(XWPFTableRow row, int index) {
        while (row.getTableCells().size() <= index) {
            row.addNewTableCell();
        }
        return row.getCell(index);
    }

    private List<XWPFParagraph> allDocumentParagraphs(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = new ArrayList<>(doc.getParagraphs());
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    paragraphs.addAll(cell.getParagraphs());
                }
            }
        }
        return paragraphs;
    }

    private String buildRationaleText(TeacherChangeAggregate aggregate, String teacherDative) {
        String date = RU_DATE.format(aggregate.startDate());
        if (aggregate.memoReason() == MemoReason.NEW_EMPLOYEE) {
            return "Прошу Вас с " + date
                    + " утвердить нагрузку на учебный год вновь принятому сотруднику "
                    + teacherDative
                    + " в следующем объеме:";
        }
        if (aggregate.memoReason() == MemoReason.CURRICULUM_PLAN_ALIGNMENT) {
            return "В связи с необходимостью приведения учебной нагрузки в соответствие с учебным планом на "
                    + academicYearLabel(aggregate.startDate())
                    + " учебный год прошу утвердить изменение учебной нагрузки "
                    + teacherDative
                    + " с "
                    + date
                    + ".";
        }
        return "В связи с производственной необходимостью направляю на утверждение изменение учебной нагрузки "
                + teacherDative
                + " с "
                + date
                + ".";
    }

    private String academicYearLabel(LocalDate date) {
        LocalDate value = Optional.ofNullable(date).orElseGet(LocalDate::now);
        int startYear = value.getMonthValue() >= 7 ? value.getYear() : value.getYear() - 1;
        return startYear + "/" + (startYear + 1);
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

    private LocalDate academicStart(Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> ranges) {
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9))
                .map(StudyPeriodSettingService.DateRange::startDate)
                .orElse(LocalDate.of(LocalDate.now().getYear(), 9, 1));
    }

    private LocalDate academicEnd(Map<StudyPeriodSettingKey, StudyPeriodSettingService.DateRange> ranges) {
        return Optional.ofNullable(ranges.get(StudyPeriodSettingKey.YEAR_1_9))
                .map(StudyPeriodSettingService.DateRange::endDate)
                .orElse(LocalDate.of(LocalDate.now().plusYears(1).getYear(), 5, 31));
    }

    private String resolveMemoAcademicYear(String academicYear) {
        if (academicYear != null && !academicYear.isBlank()) {
            return academicYear;
        }
        LocalDate now = LocalDate.now();
        int start = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return start + "/" + (start + 1);
    }

    private String keyOf(TarifficationChanges ch) {
        return String.join("|", safe(ch.getSubjectName()), safe(ch.getClassName()), String.valueOf(ch.getLoad()));
    }

    private String keyOf(ManualLoadEntry row) {
        return memoCompareKeyOf(row);
    }

    private String transferKeyOf(ManualLoadEntry row) {
        return String.join("|",
                safe(row.getSubjectName()),
                safe(row.getClassName()),
                String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                safe(row.getNumberSchoolBuilding()),
                safe(row.getGroupNameEducationalPlan()),
                String.valueOf(row.getCurriculumModuleId()),
                String.valueOf(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart()),
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
                .map(row -> new AbstractMap.SimpleEntry<>(row, resolveStatus(aggregate, row)))
                .filter(entry -> !safe(entry.getValue()).isBlank())
                .map(entry -> {
                    ManualLoadEntry row = entry.getKey();
                    String status = entry.getValue();
                    return String.join("|",
                            status,
                            safe(row.getSubjectName()),
                            safe(row.getClassName()),
                            String.valueOf(row.getLoad() == null ? 0 : row.getLoad()),
                            String.valueOf(row.getLoadFromDate() == null ? "" : row.getLoadFromDate()),
                            String.valueOf(row.getLoadToDate() == null ? "" : row.getLoadToDate()));
                })
                .sorted()
                .collect(Collectors.joining("||", aggregate.startDate() + "|" + aggregate.memoReason() + "|", ""));

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

    private boolean hasChangesAfterMemo(TeacherChangeAggregate aggregate, ServiceMemo latestMemo) {
        if (aggregate == null || latestMemo == null) {
            return true;
        }
        LocalDateTime latestChangeAt = aggregate.latestChangeAt();
        LocalDateTime memoCreatedAt = latestMemo.getCreatedAt();
        if (latestChangeAt == null || memoCreatedAt == null) {
            return true;
        }
        return latestChangeAt.isAfter(memoCreatedAt);
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

    private record CurriculumTransition(
            List<ManualLoadEntry> rowsBeforeComparison,
            Set<String> preservedShortKeys
    ) {
    }

    private record SelectionRequest(String teacherKey, LocalDate changeDate) {
    }

    private record TeacherDateKey(String teacherKey, LocalDate changeDate) {
    }

    private TeacherDirectoryEntry resolveTeacher(TeacherChangeAggregate aggregate) {
        Optional<Long> teacherId = aggregate.rows().stream().map(ManualLoadEntry::getTeacherId).filter(Objects::nonNull).findFirst();
        if (teacherId.isPresent()) {
            Optional<TeacherDirectoryEntry> byId = teacherDirectoryRepository.findById(teacherId.get());
            if (byId.isPresent()) return byId.get();
        }
        return teacherDirectoryRepository.findByFioTeacherIgnoreCase(aggregate.teacherDisplay())
                .orElseThrow(() -> new IllegalStateException("Не найден ID работника для служебной записки: " + aggregate.teacherDisplay()));
    }

    private EmploymentContract resolvePrimaryContract(TeacherDirectoryEntry teacher) {
        List<EmploymentContract> available = employmentContractRepository
                .findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacher.getId());
        return available.stream().filter(EmploymentContract::isActive).filter(EmploymentContract::isPrimaryContract).findFirst()
                .or(() -> available.stream().filter(EmploymentContract::isActive).findFirst())
                .orElseThrow(() -> new IllegalStateException("Не найден действующий трудовой договор: " + teacher.getFioTeacher()));
    }

    private String loadSnapshot(TeacherChangeAggregate aggregate, boolean before) {
        List<Map<String, Object>> rows = aggregate.rows().stream().filter(Objects::nonNull)
                .filter(row -> {
                    String status = resolveStatus(aggregate, row);
                    return before ? !"Добавить".equalsIgnoreCase(status) : !"Снять".equalsIgnoreCase(status);
                })
                .map(row -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("teacherId", row.getTeacherId());
                    value.put("subject", row.getSubjectName());
                    value.put("className", row.getClassName());
                    value.put("hours", row.getLoad());
                    value.put("validFrom", Objects.toString(row.getLoadFromDate(), ""));
                    value.put("validTo", Objects.toString(row.getLoadToDate(), ""));
                    return value;
                }).toList();
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(Map.of("effectiveDate", aggregate.startDate().toString(), "rows", rows));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сохранить снимок нагрузки", e);
        }
    }

    private enum MemoReason {
        PRODUCTION_NECESSITY,
        NEW_EMPLOYEE,
        CURRICULUM_PLAN_ALIGNMENT
    }

    private record TeacherChangeAggregate(
            String teacherDisplay,
            LocalDate startDate,
            LocalDateTime latestChangeAt,
            List<ManualLoadEntry> rows,
            Set<String> addedRowKeys,
            Set<String> removedRowKeys,
            MemoReason memoReason
    ) {
        private TeacherChangeAggregate(
                String teacherDisplay,
                LocalDate startDate,
                LocalDateTime latestChangeAt,
                List<ManualLoadEntry> rows,
                Set<String> activeKeys,
                Set<String> addedKeys,
                Set<String> removedKeys,
                MemoReason memoReason
        ) {
            this(teacherDisplay, startDate, latestChangeAt, rows, addedKeys, removedKeys, memoReason);
        }

        private boolean newEmployeeMemo() {
            return memoReason == MemoReason.NEW_EMPLOYEE;
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
