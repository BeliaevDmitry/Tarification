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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        int linked = 0;
        int created = 0;
        int ambiguous = 0;

        for (ContingentStudent row : source) {
            Resolution resolution = resolve(row, snapshot.getSnapshotDate());
            if (resolution.profile() == null) {
                row.setStudentId(null);
                row.setIdentityMatchStatus(StudentIdentityMatchStatus.AMBIGUOUS);
                ambiguous++;
                continue;
            }
            StudentProfile profile = resolution.profile();
            updateCurrentIdentity(profile, row, snapshot.getSnapshotDate());
            profile = studentProfileRepository.save(profile);
            row.setStudentId(profile.getId());
            row.setIdentityMatchStatus(resolution.status());
            syncNameHistory(profile, row.getFullName(), snapshot.getSnapshotDate());
            syncEnrollment(profile, snapshot, row, classByName.get(classKey(row.getClassName())));
            if (resolution.created()) {
                created++;
            } else {
                linked++;
            }
        }
        return new LinkResult(linked, created, ambiguous);
    }

    @Override
    @Transactional
    public LinkResult reconcileSnapshot(Long snapshotId) {
        ContingentSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Снимок контингента не найден: " + snapshotId));
        List<ContingentStudent> rows = contingentStudentRepository.findAllBySnapshotIdAndStudentIdIsNull(snapshotId);
        LinkResult result = linkStudents(snapshot, rows);
        contingentStudentRepository.saveAll(rows);
        return result;
    }

    private Resolution resolve(ContingentStudent row, LocalDate snapshotDate) {
        String normalizedRecord = normalizeRecordNumber(row.getRecordNumber());
        LocalDate birthDate = parseBirthDate(row.getBirthDate());
        String normalizedName = normalizeName(row.getFullName());

        if (usableRecordNumber(normalizedRecord)) {
            List<StudentProfile> candidates = studentProfileRepository.findAllByNormalizedRecordNumber(normalizedRecord)
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
            List<StudentProfile> candidates = studentProfileRepository
                    .findAllByNormalizedFullNameAndBirthDate(normalizedName, birthDate);
            if (candidates.size() == 1) {
                return new Resolution(candidates.get(0), StudentIdentityMatchStatus.LINKED_BY_NAME_AND_BIRTH_DATE, false);
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
        }
        profile.setActive(true);
        profile.setUpdatedAt(LocalDateTime.now());
    }

    private void syncNameHistory(StudentProfile profile, String observedName, LocalDate observedAt) {
        String normalizedObservedName = normalizeName(observedName);
        List<StudentNameHistory> histories = nameHistoryRepository
                .findAllByStudent_IdOrderByValidFromAsc(profile.getId());
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
                nameHistoryRepository.save(containing);
                return;
            }
            if (containing.getValidFrom() == null || !observedAt.isBefore(containing.getValidFrom())) {
                containing.setValidTo(observedAt.minusDays(1));
                nameHistoryRepository.save(containing);
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
        nameHistoryRepository.save(history);
    }

    private void syncEnrollment(StudentProfile profile,
                                ContingentSnapshot snapshot,
                                ContingentStudent row,
                                ClassroomLeadershipEntry classRef) {
        String className = ClassNameNormalizer.normalize(row.getClassName());
        LocalDate observedAt = snapshot.getSnapshotDate();
        StudentClassEnrollment observedEnrollment = enrollmentRepository
                .findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(profile.getId(), snapshot.getAcademicYear())
                .stream()
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
            enrollmentRepository.save(observedEnrollment);
            return;
        }
        if (observedEnrollment != null && observedAt != null) {
            if (observedEnrollment.getValidFrom() == null
                    || !observedAt.isAfter(observedEnrollment.getValidFrom())) {
                observedEnrollment.setClassName(className);
                observedEnrollment.setClassRef(classRef);
                observedEnrollment.setSourceSnapshotId(snapshot.getId());
                observedEnrollment.setUpdatedAt(LocalDateTime.now());
                enrollmentRepository.save(observedEnrollment);
                return;
            }
            LocalDate previousEnd = observedEnrollment.getValidTo();
            observedEnrollment.setValidTo(observedAt.minusDays(1));
            observedEnrollment.setStatus(StudentEnrollmentStatus.TRANSFERRED);
            observedEnrollment.setUpdatedAt(LocalDateTime.now());
            enrollmentRepository.save(observedEnrollment);

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
            enrollmentRepository.save(changed);
            return;
        }

        StudentClassEnrollment active = enrollmentRepository
                .findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(
                        profile.getId(),
                        snapshot.getAcademicYear()
                )
                .orElse(null);
        if (active != null && classKey(active.getClassName()).equals(classKey(className))) {
            if (observedAt != null && (active.getValidFrom() == null || observedAt.isBefore(active.getValidFrom()))) {
                active.setValidFrom(observedAt);
            }
            if (active.getClassRef() == null && classRef != null) {
                active.setClassRef(classRef);
            }
            active.setSourceSnapshotId(snapshot.getId());
            active.setUpdatedAt(LocalDateTime.now());
            enrollmentRepository.save(active);
            return;
        }
        if (active != null && observedAt != null
                && (active.getValidFrom() == null || !observedAt.isBefore(active.getValidFrom()))) {
            active.setValidTo(observedAt.minusDays(1));
            active.setStatus(StudentEnrollmentStatus.TRANSFERRED);
            active.setUpdatedAt(LocalDateTime.now());
            enrollmentRepository.save(active);
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
        enrollmentRepository.save(created);
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
