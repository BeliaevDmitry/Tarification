package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.StudentClassTransferDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.StudentClassTransferHistory;
import org.school.personalLoad.model.StudentClassTransferRequest;
import org.school.personalLoad.model.StudentClassTransferStatus;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassTransferHistoryRepository;
import org.school.personalLoad.repository.StudentClassTransferRequestRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.service.impl.ClassNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentClassTransferService {

    private final StudentClassTransferRequestRepository requestRepository;
    private final StudentClassTransferHistoryRepository historyRepository;
    private final StudentProfileRepository studentRepository;
    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;

    @Transactional(readOnly = true)
    public StudentClassTransferDtos.Access access(SessionUser user) {
        StudentClassTransferDtos.Access access = new StudentClassTransferDtos.Access();
        if (user == null) return access;
        access.setCanView(user.canViewTab(AppTab.CONTINGENT_CLASS_TRANSFERS));
        access.setCanEdit(user.canEditTab(AppTab.CONTINGENT_CLASS_TRANSFERS));
        return access;
    }

    @Transactional(readOnly = true)
    public StudentClassTransferDtos.Overview overview(String academicYear, StudentClassTransferDtos.Access access) {
        List<StudentClassTransferRequest> requests = requestRepository
                .findAllByAcademicYearOrderByUpdatedAtDescIdDesc(academicYear);
        CurrentContingent current = currentContingent(academicYear);
        Map<Long, List<StudentClassTransferHistory>> history = historyByRequest(requests);

        StudentClassTransferDtos.Overview overview = new StudentClassTransferDtos.Overview();
        overview.setAcademicYear(academicYear);
        overview.setTotal(requests.size());
        overview.setWaiting((int) requests.stream().filter(this::isWaiting).count());
        overview.setTransferred((int) requests.stream()
                .filter(item -> item.getStatus() == StudentClassTransferStatus.TRANSFERRED).count());
        overview.setClosed((int) requests.stream().filter(this::isClosed).count());
        overview.setRequests(requests.stream().map(item -> toRow(item, history.getOrDefault(item.getId(), List.of()))).toList());
        overview.setStudentOptions(current.students());
        overview.setClassOptions(current.classes());
        overview.setAccess(access);
        return overview;
    }

    @Transactional
    public StudentClassTransferDtos.Overview create(String academicYear,
                                                    StudentClassTransferDtos.SaveRequest request,
                                                    String actor,
                                                    StudentClassTransferDtos.Access access) {
        if (request == null || request.getStudentId() == null) {
            throw new IllegalArgumentException("Выберите ребёнка из текущего контингента");
        }
        CurrentPlacement placement = currentPlacement(academicYear, request.getStudentId());
        if (placement == null) {
            throw new IllegalArgumentException("Ребёнок не найден в последней выгрузке текущего учебного года");
        }
        StudentProfile student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));

        StudentClassTransferRequest entity = new StudentClassTransferRequest();
        entity.setAcademicYear(academicYear);
        entity.setStudent(student);
        entity.setStudentNameSnapshot(normalize(student.getCurrentFullName()).isBlank()
                ? placement.fullName() : normalize(student.getCurrentFullName()));
        entity.setFromClassName(placement.className());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(displayActor(actor));
        apply(entity, request, actor);
        requestRepository.save(entity);
        saveHistory(entity, "Заявление зарегистрировано", actor);
        return overview(academicYear, access);
    }

    @Transactional
    public StudentClassTransferDtos.Overview update(String academicYear,
                                                    Long id,
                                                    StudentClassTransferDtos.SaveRequest request,
                                                    String actor,
                                                    StudentClassTransferDtos.Access access) {
        StudentClassTransferRequest entity = find(academicYear, id);
        if (request != null && request.getStudentId() != null
                && !Objects.equals(request.getStudentId(), entity.getStudent().getId())) {
            throw new IllegalArgumentException("Ребёнка в зарегистрированном заявлении менять нельзя");
        }
        apply(entity, request, actor);
        requestRepository.save(entity);
        saveHistory(entity, "Данные заявления изменены", actor);
        return overview(academicYear, access);
    }

    private void apply(StudentClassTransferRequest entity,
                       StudentClassTransferDtos.SaveRequest request,
                       String actor) {
        if (request == null) throw new IllegalArgumentException("Заполните данные заявления");
        String targetClass = ClassNameNormalizer.normalize(request.getTargetClassName());
        String fromClass = ClassNameNormalizer.normalize(entity.getFromClassName());
        if (targetClass.isBlank()) throw new IllegalArgumentException("Выберите желаемый класс");
        if (targetClass.equalsIgnoreCase(fromClass)) {
            throw new IllegalArgumentException("Исходный и желаемый класс не могут совпадать");
        }
        Integer fromParallel = ClassNameNormalizer.extractParallel(fromClass);
        Integer targetParallel = ClassNameNormalizer.extractParallel(targetClass);
        if (fromParallel == null || !Objects.equals(fromParallel, targetParallel)) {
            throw new IllegalArgumentException("Перевод можно зарегистрировать только внутри одной параллели");
        }
        String reason = normalizeMultiline(request.getReason());
        if (reason.isBlank()) throw new IllegalArgumentException("Укажите причину перевода");

        StudentClassTransferStatus status = request.getStatus() == null
                ? StudentClassTransferStatus.WAITING_FOR_PLACE : request.getStatus();
        LocalDate completedDate = request.getCompletedDate();
        if (status == StudentClassTransferStatus.TRANSFERRED && completedDate == null) {
            completedDate = LocalDate.now();
        }
        if (status != StudentClassTransferStatus.TRANSFERRED) completedDate = null;

        entity.setTargetClassName(targetClass);
        entity.setRequestDate(request.getRequestDate() == null ? LocalDate.now() : request.getRequestDate());
        entity.setReason(reason);
        entity.setPromisedDate(request.getPromisedDate());
        entity.setPromiseNote(normalizeMultiline(request.getPromiseNote()));
        entity.setStatus(status);
        entity.setCompletedDate(completedDate);
        entity.setComment(normalizeMultiline(request.getComment()));
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(displayActor(actor));
    }

    private StudentClassTransferRequest find(String academicYear, Long id) {
        if (id == null) throw new IllegalArgumentException("Заявление не выбрано");
        return requestRepository.findByIdAndAcademicYear(id, academicYear)
                .orElseThrow(() -> new IllegalArgumentException("Заявление на перевод не найдено"));
    }

    private void saveHistory(StudentClassTransferRequest request, String action, String actor) {
        StudentClassTransferHistory history = new StudentClassTransferHistory();
        history.setTransferRequest(request);
        history.setAction(action);
        history.setDetails(historyDetails(request));
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy(displayActor(actor));
        historyRepository.save(history);
    }

    private String historyDetails(StudentClassTransferRequest request) {
        List<String> details = new ArrayList<>();
        details.add(request.getFromClassName() + " → " + request.getTargetClassName());
        details.add("статус: " + statusLabel(request.getStatus()));
        details.add("заявление: " + request.getRequestDate());
        if (request.getPromisedDate() != null) details.add("обещанная дата: " + request.getPromisedDate());
        if (!normalize(request.getPromiseNote()).isBlank()) details.add("обещание: " + request.getPromiseNote());
        details.add("причина: " + request.getReason());
        if (request.getCompletedDate() != null) details.add("переведён: " + request.getCompletedDate());
        if (!normalize(request.getComment()).isBlank()) details.add("комментарий: " + request.getComment());
        return String.join("; ", details);
    }

    private String statusLabel(StudentClassTransferStatus status) {
        if (status == null) return "зона ожидания";
        return switch (status) {
            case WAITING_FOR_PLACE -> "зона ожидания";
            case PROMISED -> "перевод обещан";
            case APPROVED -> "перевод согласован";
            case TRANSFERRED -> "переведён";
            case DECLINED -> "отказано";
            case WITHDRAWN -> "заявление отозвано";
        };
    }

    private Map<Long, List<StudentClassTransferHistory>> historyByRequest(List<StudentClassTransferRequest> requests) {
        List<Long> ids = requests.stream().map(StudentClassTransferRequest::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        return historyRepository.findAllByTransferRequest_IdInOrderByChangedAtDescIdDesc(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getTransferRequest().getId(), LinkedHashMap::new,
                        Collectors.toList()));
    }

    private StudentClassTransferDtos.TransferRow toRow(StudentClassTransferRequest source,
                                                       List<StudentClassTransferHistory> history) {
        StudentClassTransferDtos.TransferRow row = new StudentClassTransferDtos.TransferRow();
        row.setId(source.getId());
        row.setStudentId(source.getStudent().getId());
        row.setStudentName(source.getStudentNameSnapshot());
        row.setFromClassName(source.getFromClassName());
        row.setTargetClassName(source.getTargetClassName());
        row.setRequestDate(source.getRequestDate());
        row.setReason(source.getReason());
        row.setPromisedDate(source.getPromisedDate());
        row.setPromiseNote(source.getPromiseNote());
        row.setStatus(source.getStatus());
        row.setCompletedDate(source.getCompletedDate());
        row.setComment(source.getComment());
        row.setCreatedAt(source.getCreatedAt());
        row.setCreatedBy(source.getCreatedBy());
        row.setUpdatedAt(source.getUpdatedAt());
        row.setUpdatedBy(source.getUpdatedBy());
        row.setHistory(history.stream().map(this::toHistoryRow).toList());
        return row;
    }

    private StudentClassTransferDtos.HistoryRow toHistoryRow(StudentClassTransferHistory source) {
        StudentClassTransferDtos.HistoryRow row = new StudentClassTransferDtos.HistoryRow();
        row.setAction(source.getAction());
        row.setDetails(source.getDetails());
        row.setChangedAt(source.getChangedAt());
        row.setChangedBy(source.getChangedBy());
        return row;
    }

    private CurrentPlacement currentPlacement(String academicYear, Long studentId) {
        if (studentId == null) return null;
        ContingentSnapshot snapshot = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null);
        if (snapshot == null) return null;
        return contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(item -> Objects.equals(item.getStudentId(), studentId))
                .filter(item -> ClassNameNormalizer.extractParallel(item.getClassName()) != null)
                .map(item -> new CurrentPlacement(normalize(item.getFullName()), ClassNameNormalizer.normalize(item.getClassName())))
                .findFirst().orElse(null);
    }

    private CurrentContingent currentContingent(String academicYear) {
        ContingentSnapshot snapshot = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null);
        if (snapshot == null) return new CurrentContingent(List.of(), List.of());
        List<ContingentStudent> currentRows = contingentStudentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                .filter(item -> item.getStudentId() != null)
                .filter(item -> ClassNameNormalizer.extractParallel(item.getClassName()) != null)
                .toList();
        Collection<Long> studentIds = currentRows.stream().map(ContingentStudent::getStudentId).distinct().toList();
        Map<Long, StudentProfile> profiles = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity(), (left, right) -> left));

        Map<Long, StudentClassTransferDtos.StudentOption> students = new LinkedHashMap<>();
        currentRows.stream()
                .sorted(Comparator.comparing(ContingentStudent::getFullName, String.CASE_INSENSITIVE_ORDER))
                .forEach(item -> {
                    if (students.containsKey(item.getStudentId())) return;
                    StudentClassTransferDtos.StudentOption option = new StudentClassTransferDtos.StudentOption();
                    StudentProfile profile = profiles.get(item.getStudentId());
                    option.setStudentId(item.getStudentId());
                    option.setFullName(profile == null || normalize(profile.getCurrentFullName()).isBlank()
                            ? normalize(item.getFullName()) : normalize(profile.getCurrentFullName()));
                    option.setClassName(ClassNameNormalizer.normalize(item.getClassName()));
                    option.setParallel(ClassNameNormalizer.extractParallel(item.getClassName()));
                    students.put(item.getStudentId(), option);
                });

        Map<String, StudentClassTransferDtos.ClassOption> classes = new LinkedHashMap<>();
        currentRows.stream().collect(Collectors.groupingBy(
                        item -> ClassNameNormalizer.normalize(item.getClassName()),
                        Collectors.mapping(ContingentStudent::getStudentId, Collectors.toSet())))
                .forEach((className, studentIdsInClass) -> {
                    StudentClassTransferDtos.ClassOption option = new StudentClassTransferDtos.ClassOption();
                    option.setClassName(className);
                    option.setParallel(ClassNameNormalizer.extractParallel(className));
                    option.setStudents(studentIdsInClass.size());
                    classes.put(className.toLowerCase(Locale.ROOT), option);
                });
        List<StudentClassTransferDtos.ClassOption> classOptions = classes.values().stream()
                .sorted(Comparator.comparing(StudentClassTransferDtos.ClassOption::getParallel,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(StudentClassTransferDtos.ClassOption::getClassName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new CurrentContingent(new ArrayList<>(students.values()), classOptions);
    }

    private boolean isWaiting(StudentClassTransferRequest request) {
        return request.getStatus() == null
                || request.getStatus() == StudentClassTransferStatus.WAITING_FOR_PLACE
                || request.getStatus() == StudentClassTransferStatus.PROMISED
                || request.getStatus() == StudentClassTransferStatus.APPROVED;
    }

    private boolean isClosed(StudentClassTransferRequest request) {
        return request.getStatus() == StudentClassTransferStatus.DECLINED
                || request.getStatus() == StudentClassTransferStatus.WITHDRAWN;
    }

    private String displayActor(String value) {
        String actor = normalize(value);
        return actor.isBlank() ? "Система" : actor;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ");
    }

    private String normalizeMultiline(String value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private record CurrentPlacement(String fullName, String className) {
    }

    private record CurrentContingent(List<StudentClassTransferDtos.StudentOption> students,
                                     List<StudentClassTransferDtos.ClassOption> classes) {
    }
}
