package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentEnrollmentStatus;
import org.school.personalLoad.model.StudentIdentityMatchStatus;
import org.school.personalLoad.model.StudentNameHistory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.service.StudentIdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentIdentityServiceImpl implements StudentIdentityService {

    private static final List<DateTimeFormatter> BIRTH_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private final StudentProfileRepository studentProfileRepository;
    private final StudentNameHistoryRepository nameHistoryRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;

    @Override
    @Transactional
    public LinkResult linkStudents(ContingentSnapshot snapshot, List<ContingentStudent> students) {
        if (snapshot == null || snapshot.getId() == null) {
            throw new IllegalArgumentException("Снимок контингента должен быть сохранён до сопоставления детей");
        }
        List<ContingentStudent> source = students == null ? List.of() : students;
        Map<String, ClassroomLeadershipEntry> classByName = uniqueClassesByName(snapshot.getAcademicYear());
        IdentityIndex identityIndex = new IdentityIndex(studentProfileRepository.findAll());
        Map<String, Integer> weakIdentityOccurrences = new HashMap<>();
        for (ContingentStudent row : source) {
            String normalizedName = normalizeName(row.getFullName());
            if (!normalizedName.isBlank()
                    && !usableRecordNumber(normalizeRecordNumber(row.getRecordNumber()))
                    && parseBirthDate(row.getBirthDate()) == null) {
                weakIdentityOccurrences.merge(normalizedName, 1, Integer::sum);
            }
        }
        int linked = 0;
        int created = 0;
        int ambiguous = 0;
        List<ResolvedStudentRow> resolvedRows = new ArrayList<>();
        List<StudentProfile> profilesToSave = new ArrayList<>();
        Set<StudentProfile> profilesMarkedForSave = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ContingentStudent row : source) {
            String weakName = normalizeName(row.getFullName());
            if (weakIdentityOccurrences.getOrDefault(weakName, 0) > 1) {
                row.setStudentId(null);
                row.setIdentityMatchStatus(StudentIdentityMatchStatus.AMBIGUOUS);
                ambiguous++;
                continue;
            }
            Resolution resolution = resolve(row, snapshot.getSnapshotDate(), identityIndex);
            if (resolution.profile() == null) {
                row.setStudentId(null);
                row.setIdentityMatchStatus(StudentIdentityMatchStatus.AMBIGUOUS);
                ambiguous++;
                continue;
            }
            StudentProfile profile = resolution.profile();
            updateCurrentIdentity(profile, row, snapshot.getSnapshotDate());
            row.setIdentityMatchStatus(resolution.status());
            identityIndex.add(profile);
            markDirty(profile, profilesToSave, profilesMarkedForSave);
            resolvedRows.add(new ResolvedStudentRow(row, profile));
            if (resolution.created()) {
                created++;
            } else {
                linked++;
            }
        }

        if (!profilesToSave.isEmpty()) {
            studentProfileRepository.saveAll(profilesToSave);
        }
        resolvedRows.forEach(item -> item.row().setStudentId(item.profile().getId()));

        Map<Long, List<StudentNameHistory>> historiesByStudent = new HashMap<>();
        nameHistoryRepository.findAll().stream()
                .filter(history -> history.getStudent() != null && history.getStudent().getId() != null)
                .forEach(history -> historiesByStudent
                        .computeIfAbsent(history.getStudent().getId(), ignored -> new ArrayList<>())
                        .add(history));
        Map<Long, List<StudentClassEnrollment>> enrollmentsByStudent = new HashMap<>();
        enrollmentRepository.findAllByAcademicYear(snapshot.getAcademicYear()).stream()
                .filter(enrollment -> enrollment.getStudent() != null && enrollment.getStudent().getId() != null)
                .forEach(enrollment -> enrollmentsByStudent
                        .computeIfAbsent(enrollment.getStudent().getId(), ignored -> new ArrayList<>())
                        .add(enrollment));

        List<StudentNameHistory> historiesToSave = new ArrayList<>();
        Set<StudentNameHistory> historiesMarkedForSave = Collections.newSetFromMap(new IdentityHashMap<>());
        List<StudentClassEnrollment> enrollmentsToSave = new ArrayList<>();
        Set<StudentClassEnrollment> enrollmentsMarkedForSave = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ResolvedStudentRow item : resolvedRows) {
            if (item.profile().getId() == null) {
                continue;
            }
            List<StudentNameHistory> histories = historiesByStudent
                    .computeIfAbsent(item.profile().getId(), ignored -> new ArrayList<>());
            syncNameHistory(item.profile(), item.row().getFullName(), snapshot.getSnapshotDate(), histories,
                    historiesToSave, historiesMarkedForSave);
            List<StudentClassEnrollment> enrollments = enrollmentsByStudent
                    .computeIfAbsent(item.profile().getId(), ignored -> new ArrayList<>());
            syncEnrollment(item.profile(), snapshot, item.row(), classByName.get(classKey(item.row().getClassName())),
                    enrollments, enrollmentsToSave, enrollmentsMarkedForSave);
        }
        if (!historiesToSave.isEmpty()) {
            nameHistoryRepository.saveAll(historiesToSave);
        }
        if (!enrollmentsToSave.isEmpty()) {
            enrollmentRepository.saveAll(enrollmentsToSave);
        }
        return new LinkResult(linked, created, ambiguous);
    }

    @Override
    @Transactional
    public LinkResult reconcileSnapshot(Long snapshotId) {
        ContingentSnapshot snapshot = snapshotRepository.findByIdForUpdate(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Снимок контингента не найден: " + snapshotId));
        List<ContingentStudent> rows = contingentStudentRepository.findAllBySnapshotIdAndStudentIdIsNull(snapshotId);
        LinkResult result = linkStudents(snapshot, rows);
        contingentStudentRepository.saveAll(rows);
        return result;
    }

    @Override
    @Transactional
    public void resolveManually(ContingentSnapshot snapshot, ContingentStudent row, Long studentId) {
        if (snapshot == null || snapshot.getId() == null) {
            throw new IllegalArgumentException("Снимок контингента не найден");
        }
        if (row == null || row.getId() == null || !Objects.equals(snapshot.getId(), row.getSnapshotId())) {
            throw new IllegalArgumentException("Строка не относится к выбранной выгрузке");
        }
        Long targetStudentId = studentId == null ? row.getStudentId() : studentId;
        if (targetStudentId == null) {
            throw new IllegalArgumentException("Выберите карточку ребёнка");
        }
        if (row.getStudentId() != null && !Objects.equals(row.getStudentId(), targetStudentId)) {
            throw new IllegalArgumentException(
                    "Уже связанную карточку нельзя заменить здесь. Исправьте связь в карточке ребёнка."
            );
        }

        StudentProfile profile = studentProfileRepository.findById(targetStudentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        updateCurrentIdentity(profile, row, snapshot.getSnapshotDate());
        studentProfileRepository.save(profile);
        row.setStudentId(profile.getId());
        row.setIdentityMatchStatus(StudentIdentityMatchStatus.MANUALLY_LINKED);

        List<StudentNameHistory> histories = nameHistoryRepository.findAll().stream()
                .filter(history -> history.getStudent() != null
                        && Objects.equals(history.getStudent().getId(), profile.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<StudentNameHistory> historiesToSave = new ArrayList<>();
        Set<StudentNameHistory> historiesMarkedForSave = Collections.newSetFromMap(new IdentityHashMap<>());
        syncNameHistory(profile, row.getFullName(), snapshot.getSnapshotDate(), histories,
                historiesToSave, historiesMarkedForSave);
        if (!historiesToSave.isEmpty()) {
            nameHistoryRepository.saveAll(historiesToSave);
        }

        List<StudentClassEnrollment> enrollments = new ArrayList<>(
                enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                        profile.getId(), snapshot.getAcademicYear()
                )
        );
        List<StudentClassEnrollment> enrollmentsToSave = new ArrayList<>();
        Set<StudentClassEnrollment> enrollmentsMarkedForSave = Collections.newSetFromMap(new IdentityHashMap<>());
        ClassroomLeadershipEntry classRef = uniqueClassesByName(snapshot.getAcademicYear())
                .get(classKey(row.getClassName()));
        syncEnrollment(profile, snapshot, row, classRef, enrollments,
                enrollmentsToSave, enrollmentsMarkedForSave);
        if (!enrollmentsToSave.isEmpty()) {
            enrollmentRepository.saveAll(enrollmentsToSave);
        }
    }

    private Resolution resolve(ContingentStudent row, LocalDate snapshotDate, IdentityIndex identityIndex) {
        String normalizedRecord = normalizeRecordNumber(row.getRecordNumber());
        LocalDate birthDate = parseBirthDate(row.getBirthDate());
        String normalizedName = normalizeName(row.getFullName());

        if (usableRecordNumber(normalizedRecord)) {
            List<StudentProfile> candidates = identityIndex.byRecord(normalizedRecord)
                    .stream()
                    .filter(profile -> birthDate == null || profile.getBirthDate() == null || birthDate.equals(profile.getBirthDate()))
                    .toList();
            if (candidates.size() == 1) {
                return new Resolution(candidates.get(0), StudentIdentityMatchStatus.LINKED_BY_RECORD_NUMBER, false);
            }
            if (candidates.size() > 1) {
                return Resolution.ambiguous();
            }
        }

        if (!normalizedName.isBlank() && birthDate != null) {
            List<StudentProfile> candidates = identityIndex.byNameAndBirthDate(normalizedName, birthDate);
            if (candidates.size() == 1) {
                return new Resolution(candidates.get(0), StudentIdentityMatchStatus.LINKED_BY_NAME_AND_BIRTH_DATE, false);
            }
            if (candidates.size() > 1) {
                return Resolution.ambiguous();
            }
        }

        // The shortened МЭШ export has no record number or birth date. Reuse a
        // permanent profile only when the normalized full name is unique; a
        // duplicate name must stay ambiguous instead of silently merging two children.
        if (!normalizedName.isBlank() && birthDate == null) {
            List<StudentProfile> candidates = identityIndex.byName(normalizedName);
            if (candidates.size() == 1) {
                return new Resolution(candidates.get(0), StudentIdentityMatchStatus.LINKED_BY_NAME_ONLY, false);
            }
            if (candidates.size() > 1) {
                return Resolution.ambiguous();
            }
        }

        StudentProfile created = new StudentProfile();
        created.setCurrentFullName(displayName(row.getFullName()));
        created.setNormalizedFullName(normalizedName);
        created.setBirthDate(birthDate);
        created.setRecordNumber(usableRecordNumber(normalizedRecord) ? normalize(row.getRecordNumber()) : null);
        created.setNormalizedRecordNumber(usableRecordNumber(normalizedRecord) ? normalizedRecord : null);
        created.setFirstSeenDate(snapshotDate);
        created.setLastSeenDate(snapshotDate);
        created.setActive(true);
        return new Resolution(created, StudentIdentityMatchStatus.CREATED, true);
    }

    private void updateCurrentIdentity(StudentProfile profile, ContingentStudent row, LocalDate snapshotDate) {
        if (profile.getFirstSeenDate() == null || (snapshotDate != null && snapshotDate.isBefore(profile.getFirstSeenDate()))) {
            profile.setFirstSeenDate(snapshotDate);
        }
        boolean newestObservation = profile.getLastSeenDate() == null
                || snapshotDate == null
                || !snapshotDate.isBefore(profile.getLastSeenDate());
        if (newestObservation) {
            profile.setCurrentFullName(displayName(row.getFullName()));
            profile.setNormalizedFullName(normalizeName(row.getFullName()));
            profile.setLastSeenDate(snapshotDate);
            String normalizedRecord = normalizeRecordNumber(row.getRecordNumber());
            if (usableRecordNumber(normalizedRecord)) {
                profile.setRecordNumber(normalize(row.getRecordNumber()));
                profile.setNormalizedRecordNumber(normalizedRecord);
            }
            LocalDate birthDate = parseBirthDate(row.getBirthDate());
            if (birthDate != null) {
                profile.setBirthDate(birthDate);
            }
            if (!normalize(row.getPhone()).isBlank()) {
                profile.setChildPhone(normalize(row.getPhone()));
            }
            if (!normalize(row.getRepresentativeName()).isBlank()) {
                profile.setRepresentativeName(normalize(row.getRepresentativeName()));
            }
            if (!normalize(row.getRepresentativePhone()).isBlank()) {
                profile.setRepresentativePhone(normalize(row.getRepresentativePhone()));
            }
        }
        profile.setActive(true);
        profile.setUpdatedAt(LocalDateTime.now());
    }

    private void syncNameHistory(StudentProfile profile,
                                 String observedName,
                                 LocalDate observedAt,
                                 List<StudentNameHistory> histories,
                                 List<StudentNameHistory> historiesToSave,
                                 Set<StudentNameHistory> historiesMarkedForSave) {
        String normalizedObservedName = normalizeName(observedName);
        histories.sort(Comparator.comparing(StudentNameHistory::getValidFrom,
                Comparator.nullsFirst(LocalDate::compareTo)));
        StudentNameHistory containing = histories.stream()
                .filter(history -> contains(history.getValidFrom(), history.getValidTo(), observedAt))
                .findFirst()
                .orElse(null);
        if (containing != null
                && Objects.equals(containing.getNormalizedFullName(), normalizedObservedName)) {
            return;
        }
        if (containing != null && observedAt != null) {
            if (containing.getValidFrom() == null || !observedAt.isAfter(containing.getValidFrom())) {
                containing.setFullName(displayName(observedName));
                containing.setNormalizedFullName(normalizedObservedName);
                markDirty(containing, historiesToSave, historiesMarkedForSave);
                return;
            }
            if (containing.getValidFrom() == null || !observedAt.isBefore(containing.getValidFrom())) {
                containing.setValidTo(observedAt.minusDays(1));
                markDirty(containing, historiesToSave, historiesMarkedForSave);
            }
        }

        LocalDate nextStart = histories.stream()
                .map(StudentNameHistory::getValidFrom)
                .filter(Objects::nonNull)
                .filter(date -> observedAt != null && date.isAfter(observedAt))
                .min(LocalDate::compareTo)
                .orElse(null);
        StudentNameHistory history = new StudentNameHistory();
        history.setStudent(profile);
        history.setFullName(displayName(observedName));
        history.setNormalizedFullName(normalizedObservedName);
        history.setValidFrom(observedAt);
        history.setValidTo(nextStart == null ? null : nextStart.minusDays(1));
        histories.add(history);
        markDirty(history, historiesToSave, historiesMarkedForSave);
    }

    private void syncEnrollment(StudentProfile profile,
                                ContingentSnapshot snapshot,
                                ContingentStudent row,
                                ClassroomLeadershipEntry classRef,
                                List<StudentClassEnrollment> enrollments,
                                List<StudentClassEnrollment> enrollmentsToSave,
                                Set<StudentClassEnrollment> enrollmentsMarkedForSave) {
        String className = ClassNameNormalizer.normalize(row.getClassName());
        LocalDate observedAt = snapshot.getSnapshotDate();
        enrollments.sort(Comparator.comparing(StudentClassEnrollment::getValidFrom,
                Comparator.nullsLast(Comparator.reverseOrder())));
        StudentClassEnrollment observedEnrollment = enrollments.stream()
                .filter(enrollment -> contains(enrollment.getValidFrom(), enrollment.getValidTo(), observedAt))
                .findFirst()
                .orElse(null);
        if (observedEnrollment != null
                && classKey(observedEnrollment.getClassName()).equals(classKey(className))) {
            if (observedEnrollment.getClassRef() == null && classRef != null) {
                observedEnrollment.setClassRef(classRef);
            }
            observedEnrollment.setSourceSnapshotId(snapshot.getId());
            observedEnrollment.setUpdatedAt(LocalDateTime.now());
            markDirty(observedEnrollment, enrollmentsToSave, enrollmentsMarkedForSave);
            return;
        }
        if (observedEnrollment != null && observedAt != null) {
            if (observedEnrollment.getValidFrom() == null
                    || !observedAt.isAfter(observedEnrollment.getValidFrom())) {
                observedEnrollment.setClassName(className);
                observedEnrollment.setClassRef(classRef);
                observedEnrollment.setSourceSnapshotId(snapshot.getId());
                observedEnrollment.setUpdatedAt(LocalDateTime.now());
                markDirty(observedEnrollment, enrollmentsToSave, enrollmentsMarkedForSave);
                return;
            }
            LocalDate previousEnd = observedEnrollment.getValidTo();
            observedEnrollment.setValidTo(observedAt.minusDays(1));
            observedEnrollment.setStatus(StudentEnrollmentStatus.TRANSFERRED);
            observedEnrollment.setUpdatedAt(LocalDateTime.now());
            markDirty(observedEnrollment, enrollmentsToSave, enrollmentsMarkedForSave);

            StudentClassEnrollment changed = new StudentClassEnrollment();
            changed.setStudent(profile);
            changed.setClassRef(classRef);
            changed.setAcademicYear(snapshot.getAcademicYear());
            changed.setClassName(className);
            changed.setValidFrom(observedAt);
            changed.setValidTo(previousEnd);
            changed.setStatus(previousEnd == null
                    ? StudentEnrollmentStatus.ACTIVE
                    : StudentEnrollmentStatus.TRANSFERRED);
            changed.setSourceSnapshotId(snapshot.getId());
            enrollments.add(changed);
            markDirty(changed, enrollmentsToSave, enrollmentsMarkedForSave);
            return;
        }

        StudentClassEnrollment active = enrollments.stream()
                .filter(enrollment -> enrollment.getValidTo() == null)
                .findFirst().orElse(null);
        if (active != null && classKey(active.getClassName()).equals(classKey(className))) {
            if (observedAt != null && (active.getValidFrom() == null || observedAt.isBefore(active.getValidFrom()))) {
                active.setValidFrom(observedAt);
            }
            if (active.getClassRef() == null && classRef != null) {
                active.setClassRef(classRef);
            }
            active.setSourceSnapshotId(snapshot.getId());
            active.setUpdatedAt(LocalDateTime.now());
            markDirty(active, enrollmentsToSave, enrollmentsMarkedForSave);
            return;
        }
        if (active != null && observedAt != null
                && (active.getValidFrom() == null || !observedAt.isBefore(active.getValidFrom()))) {
            active.setValidTo(observedAt.minusDays(1));
            active.setStatus(StudentEnrollmentStatus.TRANSFERRED);
            active.setUpdatedAt(LocalDateTime.now());
            markDirty(active, enrollmentsToSave, enrollmentsMarkedForSave);
        }

        StudentClassEnrollment created = new StudentClassEnrollment();
        created.setStudent(profile);
        created.setClassRef(classRef);
        created.setAcademicYear(snapshot.getAcademicYear());
        created.setClassName(className);
        created.setValidFrom(observedAt);
        if (active != null && observedAt != null && active.getValidFrom() != null
                && observedAt.isBefore(active.getValidFrom())) {
            created.setValidTo(active.getValidFrom().minusDays(1));
            created.setStatus(StudentEnrollmentStatus.TRANSFERRED);
        } else {
            created.setStatus(StudentEnrollmentStatus.ACTIVE);
        }
        created.setSourceSnapshotId(snapshot.getId());
        enrollments.add(created);
        markDirty(created, enrollmentsToSave, enrollmentsMarkedForSave);
    }

    private <T> void markDirty(T entity, List<T> target, Set<T> marked) {
        if (marked.add(entity)) {
            target.add(entity);
        }
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        if (date == null) {
            return to == null;
        }
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private Map<String, ClassroomLeadershipEntry> uniqueClassesByName(String academicYear) {
        Map<String, List<ClassroomLeadershipEntry>> grouped = new HashMap<>();
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).forEach(entry ->
                grouped.computeIfAbsent(classKey(entry.getClassName()), ignored -> new ArrayList<>()).add(entry));
        Map<String, ClassroomLeadershipEntry> result = new HashMap<>();
        grouped.forEach((key, values) -> {
            if (values.size() == 1) {
                result.put(key, values.get(0));
            }
        });
        return result;
    }

    private LocalDate parseBirthDate(String raw) {
        String value = normalize(raw);
        if (value.isBlank()) {
            return null;
        }
        if (value.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            value = value.substring(0, 10);
        } else if (value.matches("^\\d{2}[./]\\d{2}[./]\\d{4}.*")) {
            value = value.substring(0, 10);
        }
        for (DateTimeFormatter formatter : BIRTH_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported source format.
            }
        }
        return null;
    }

    private boolean usableRecordNumber(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(normalized);
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private String normalizeName(String value) {
        return normalize(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ");
    }

    private String displayName(String value) {
        return normalize(value).replaceAll("\\s+", " ");
    }

    private String normalizeRecordNumber(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String classKey(String value) {
        return ClassNameNormalizer.normalize(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private final class IdentityIndex {
        private final Map<String, List<StudentProfile>> byRecord = new HashMap<>();
        private final Map<String, List<StudentProfile>> byNameAndBirthDate = new HashMap<>();
        private final Map<String, List<StudentProfile>> byName = new HashMap<>();

        private IdentityIndex(List<StudentProfile> profiles) {
            (profiles == null ? List.<StudentProfile>of() : profiles).forEach(this::add);
        }

        private void add(StudentProfile profile) {
            if (profile == null) {
                return;
            }
            String record = normalizeRecordNumber(firstNotBlank(
                    profile.getRecordNumber(),
                    profile.getNormalizedRecordNumber()
            ));
            if (usableRecordNumber(record)) {
                addCandidate(byRecord, record, profile);
            }
            String name = normalizeName(profile.getCurrentFullName());
            if (!name.isBlank()) {
                addCandidate(byName, name, profile);
                if (profile.getBirthDate() != null) {
                    addCandidate(byNameAndBirthDate, nameBirthKey(name, profile.getBirthDate()), profile);
                }
            }
        }

        private List<StudentProfile> byRecord(String record) {
            return byRecord.getOrDefault(record, List.of());
        }

        private List<StudentProfile> byNameAndBirthDate(String name, LocalDate birthDate) {
            return byNameAndBirthDate.getOrDefault(nameBirthKey(name, birthDate), List.of());
        }

        private List<StudentProfile> byName(String name) {
            return byName.getOrDefault(name, List.of());
        }

        private void addCandidate(Map<String, List<StudentProfile>> target, String key, StudentProfile profile) {
            List<StudentProfile> candidates = target.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (candidates.stream().noneMatch(existing -> existing == profile)) {
                candidates.add(profile);
            }
        }

        private String nameBirthKey(String name, LocalDate birthDate) {
            return name + "\u0000" + birthDate;
        }
    }

    private String firstNotBlank(String first, String second) {
        return !normalize(first).isBlank() ? first : second;
    }

    private record ResolvedStudentRow(ContingentStudent row, StudentProfile profile) {
    }

    private record Resolution(
            StudentProfile profile,
            StudentIdentityMatchStatus status,
            boolean created
    ) {
        static Resolution ambiguous() {
            return new Resolution(null, StudentIdentityMatchStatus.AMBIGUOUS, false);
        }
    }
}
