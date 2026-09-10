package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.contingent.AdmissionDtos;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.model.AdmissionAccessRole;
import org.school.personalLoad.model.AdmissionCandidate;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;
import org.school.personalLoad.model.AdmissionRoleAssignment;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.AdmissionCandidateRepository;
import org.school.personalLoad.repository.AdmissionRoleAssignmentRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.impl.ClassNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionCandidateRepository repository;
    private final AdmissionRoleAssignmentRepository roleRepository;
    private final AppUserRepository userRepository;
    private final ContingentService contingentService;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;

    @Transactional(readOnly = true)
    public AdmissionDtos.Access access(SessionUser user) {
        AdmissionDtos.Access access = new AdmissionDtos.Access();
        if (user == null) return access;
        boolean admin = user.isAdmin();
        boolean secretary = user.getRole() == UserRole.SECRETARY
                || roleRepository.existsByUserIdAndRole(user.getId(), AdmissionAccessRole.SECRETARY);
        boolean decisionMaker = roleRepository.existsByUserIdAndRole(user.getId(), AdmissionAccessRole.DECISION_MAKER);
        boolean canEdit = admin || secretary || user.canEditTab(AppTab.CONTINGENT_ADMISSION);
        boolean canDecide = admin || decisionMaker;
        access.setCanView(canEdit || canDecide
                || user.canViewTab(AppTab.CONTINGENT_ADMISSION)
                || user.canViewTab(AppTab.CONTINGENT_STATS)
                || user.canViewTab(AppTab.CONTINGENT_IMPORT));
        access.setCanEdit(canEdit);
        access.setCanDecide(canDecide);
        access.setCanManageRoles(admin || user.canViewTab(AppTab.CONTINGENT_ADMISSION_ROLES));
        access.setCanEditRoles(admin || user.canEditTab(AppTab.CONTINGENT_ADMISSION_ROLES));
        return access;
    }

    @Transactional(readOnly = true)
    public AdmissionDtos.Overview overview(String academicYear) {
        return toOverview(academicYear, repository.findAllByAcademicYearOrderByProcessedAscUpdatedAtDescIdDesc(academicYear));
    }

    @Transactional
    public AdmissionDtos.Overview create(String academicYear, AdmissionDtos.SaveRequest request, String actor) {
        AdmissionCandidate candidate = new AdmissionCandidate();
        candidate.setAcademicYear(academicYear);
        candidate.setCreatedBy(displayActor(actor));
        candidate.setDocumentStatus(AdmissionDocumentStatus.MOS_RU_SUBMITTED);
        apply(candidate, request, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    @Transactional
    public AdmissionDtos.Overview update(String academicYear, Long id, AdmissionDtos.SaveRequest request, String actor) {
        AdmissionCandidate candidate = find(academicYear, id);
        apply(candidate, request, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    @Transactional
    public AdmissionDtos.Overview action(String academicYear, Long id, AdmissionDtos.ActionRequest request, String actor) {
        AdmissionCandidate candidate = find(academicYear, id);
        String action = normalize(request == null ? null : request.getAction()).toUpperCase(Locale.ROOT);
        switch (action) {
            case "SET_STATUS" -> setDocumentStatus(candidate, request == null ? null : request.getDocumentStatus());
            case "AGREE" -> agree(candidate, request);
            case "ENROLL" -> {
                if (normalize(candidate.getAssignedClass()).isBlank()) {
                    throw new IllegalArgumentException("Перед зачислением укажите класс зачисления");
                }
                candidate.setDecisionStatus(AdmissionDecisionStatus.ENROLLED);
                candidate.setDocumentStatus(AdmissionDocumentStatus.ENROLLED);
            }
            case "REFUSE" -> refuse(candidate, request);
            case "TESTING" -> candidate.setTesting(request != null && request.getTesting() != null
                    ? request.getTesting() : !candidate.isTesting());
            case "PROBLEM" -> candidate.setProblems(normalizeMultiline(request == null ? null : request.getProblems()));
            case "PROCESSED" -> candidate.setProcessed(true);
            case "REOPEN" -> candidate.setProcessed(false);
            default -> throw new IllegalArgumentException("Неизвестное действие по приёму ребёнка");
        }
        touch(candidate, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    private AdmissionCandidate find(String academicYear, Long id) {
        if (id == null) throw new IllegalArgumentException("Запись приёма не выбрана");
        return repository.findByIdAndAcademicYear(id, academicYear)
                .orElseThrow(() -> new IllegalArgumentException("Запись приёма не найдена"));
    }

    private void apply(AdmissionCandidate candidate, AdmissionDtos.SaveRequest request, String actor) {
        if (request == null) throw new IllegalArgumentException("Заполните данные ребёнка");
        String fullName = normalize(request.getFullName());
        if (fullName.isBlank()) throw new IllegalArgumentException("Укажите ФИО ребёнка");
        Integer requestedParallel = request.getRequestedParallel();
        if (requestedParallel == null || requestedParallel < 1 || requestedParallel > 11) {
            throw new IllegalArgumentException("Выберите параллель от 1 до 11");
        }
        candidate.setFullName(fullName);
        candidate.setRequestedParallel(requestedParallel);
        if (candidate.getDocumentStatus() == null) candidate.setDocumentStatus(AdmissionDocumentStatus.MOS_RU_SUBMITTED);
        touch(candidate, actor);
    }

    private void setDocumentStatus(AdmissionCandidate candidate, AdmissionDocumentStatus status) {
        if (status == null) throw new IllegalArgumentException("Выберите новый этап заявления");
        if (status == AdmissionDocumentStatus.ENROLLED && normalize(candidate.getAssignedClass()).isBlank()) {
            throw new IllegalArgumentException("Перед этапом «Зачислен» согласуйте конкретный класс");
        }
        candidate.setDocumentStatus(status);
        if (status == AdmissionDocumentStatus.ENROLLED) {
            candidate.setDecisionStatus(AdmissionDecisionStatus.ENROLLED);
        }
    }

    private void agree(AdmissionCandidate candidate, AdmissionDtos.ActionRequest request) {
        String assignedClass = normalize(request == null ? null : request.getAssignedClass());
        if (assignedClass.isBlank()) throw new IllegalArgumentException("Выберите класс для согласования");
        Integer parallel = ClassNameNormalizer.extractParallel(assignedClass);
        if (!Objects.equals(parallel, candidate.getRequestedParallel())) {
            throw new IllegalArgumentException("Можно выбрать только класс " + candidate.getRequestedParallel() + " параллели");
        }
        candidate.setAssignedClass(ClassNameNormalizer.normalize(assignedClass));
        candidate.setComment(normalizeMultiline(request == null ? null : request.getComment()));
        candidate.setDecisionStatus(AdmissionDecisionStatus.AGREED);
    }

    private void refuse(AdmissionCandidate candidate, AdmissionDtos.ActionRequest request) {
        String reason = normalizeMultiline(request == null ? null : request.getComment());
        if (reason.isBlank()) throw new IllegalArgumentException("Укажите причину отказа");
        candidate.setComment(reason);
        candidate.setDecisionStatus(AdmissionDecisionStatus.REFUSED);
    }

    @Transactional(readOnly = true)
    public AdmissionDtos.RolesOverview rolesOverview(boolean canEdit) {
        Set<String> assignments = roleRepository.findAllByOrderByRoleAscUserIdAsc().stream()
                .map(row -> roleKey(row.getUserId(), row.getRole()))
                .collect(java.util.stream.Collectors.toSet());
        List<AdmissionDtos.RoleUserRow> rows = userRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(user -> toRoleRow(user, assignments))
                .toList();
        AdmissionDtos.RolesOverview overview = new AdmissionDtos.RolesOverview();
        overview.setUsers(rows);
        overview.setCanEdit(canEdit);
        return overview;
    }

    @Transactional
    public AdmissionDtos.RolesOverview updateRoles(AdmissionDtos.RolesUpdateRequest request, String actor, boolean canEdit) {
        Map<Long, AppUser> users = userRepository.findAll().stream()
                .filter(user -> user.getId() != null)
                .collect(java.util.stream.Collectors.toMap(AppUser::getId, user -> user));
        Set<String> desired = new HashSet<>();
        for (AdmissionDtos.RoleAssignmentRequest row : request == null || request.getAssignments() == null
                ? List.<AdmissionDtos.RoleAssignmentRequest>of() : request.getAssignments()) {
            if (row == null || row.getUserId() == null || !users.containsKey(row.getUserId())) continue;
            if (row.isSecretary()) desired.add(roleKey(row.getUserId(), AdmissionAccessRole.SECRETARY));
            if (row.isDecisionMaker()) desired.add(roleKey(row.getUserId(), AdmissionAccessRole.DECISION_MAKER));
        }
        List<AdmissionRoleAssignment> existing = roleRepository.findAllByOrderByRoleAscUserIdAsc();
        List<AdmissionRoleAssignment> removed = existing.stream()
                .filter(row -> !desired.contains(roleKey(row.getUserId(), row.getRole())))
                .toList();
        if (!removed.isEmpty()) roleRepository.deleteAll(removed);
        Set<String> existingKeys = existing.stream()
                .map(row -> roleKey(row.getUserId(), row.getRole()))
                .collect(java.util.stream.Collectors.toSet());
        List<AdmissionRoleAssignment> added = new ArrayList<>();
        for (String key : desired) {
            if (existingKeys.contains(key)) continue;
            String[] parts = key.split(":", 2);
            AdmissionRoleAssignment assignment = new AdmissionRoleAssignment();
            assignment.setUserId(Long.valueOf(parts[0]));
            assignment.setRole(AdmissionAccessRole.valueOf(parts[1]));
            assignment.setCreatedBy(displayActor(actor));
            added.add(assignment);
        }
        if (!added.isEmpty()) roleRepository.saveAll(added);
        return rolesOverview(canEdit);
    }

    private AdmissionDtos.RoleUserRow toRoleRow(AppUser user, Set<String> assignments) {
        AdmissionDtos.RoleUserRow row = new AdmissionDtos.RoleUserRow();
        row.setUserId(user.getId());
        row.setUsername(user.getUsername());
        row.setFullName(user.getFullName());
        row.setSystemRole(user.getRole());
        row.setSystemRoleName(user.getRole() == null ? "" : user.getRole().getDisplayName());
        row.setActive(user.isActive());
        boolean systemSecretary = user.getRole() == UserRole.SECRETARY;
        row.setSecretaryFromSystemRole(systemSecretary);
        row.setSecretary(systemSecretary || assignments.contains(roleKey(user.getId(), AdmissionAccessRole.SECRETARY)));
        row.setDecisionMaker(assignments.contains(roleKey(user.getId(), AdmissionAccessRole.DECISION_MAKER)));
        return row;
    }

    private String roleKey(Long userId, AdmissionAccessRole role) {
        return userId + ":" + role.name();
    }

    private void touch(AdmissionCandidate candidate, String actor) {
        candidate.setUpdatedAt(LocalDateTime.now());
        candidate.setUpdatedBy(displayActor(actor));
    }

    private AdmissionDtos.Overview toOverview(String academicYear, List<AdmissionCandidate> candidates) {
        List<AdmissionCandidate> source = candidates == null ? List.of() : candidates;
        Map<Integer, MutableParallelStats> byParallel = new TreeMap<>();
        Map<AdmissionDecisionStatus, Integer> decisions = new EnumMap<>(AdmissionDecisionStatus.class);
        int processed = 0;
        int active = 0;
        for (AdmissionCandidate candidate : source) {
            MutableParallelStats stats = byParallel.computeIfAbsent(
                    candidate.getRequestedParallel(), ignored -> new MutableParallelStats()
            );
            stats.total++;
            if (candidate.isProcessed()) {
                processed++;
            } else {
                active++;
                stats.active++;
            }
            AdmissionDecisionStatus decision = candidate.getDecisionStatus() == null
                    ? AdmissionDecisionStatus.PENDING
                    : candidate.getDecisionStatus();
            decisions.merge(decision, 1, Integer::sum);
            switch (decision) {
                case AGREED -> stats.agreed++;
                case ENROLLED -> stats.enrolled++;
                case REFUSED -> stats.refused++;
                default -> { }
            }
        }

        List<AdmissionDtos.ParallelStats> parallelRows = new ArrayList<>();
        byParallel.forEach((parallel, sourceStats) -> {
            AdmissionDtos.ParallelStats stats = new AdmissionDtos.ParallelStats();
            stats.setRequestedParallel(parallel);
            stats.setTotal(sourceStats.total);
            stats.setActive(sourceStats.active);
            stats.setAgreed(sourceStats.agreed);
            stats.setEnrolled(sourceStats.enrolled);
            stats.setRefused(sourceStats.refused);
            parallelRows.add(stats);
        });

        AdmissionDtos.Overview overview = new AdmissionDtos.Overview();
        overview.setAcademicYear(academicYear);
        overview.setTotal(source.size());
        overview.setActive(active);
        overview.setAgreed(decisions.getOrDefault(AdmissionDecisionStatus.AGREED, 0));
        overview.setEnrolled(decisions.getOrDefault(AdmissionDecisionStatus.ENROLLED, 0));
        overview.setRefused(decisions.getOrDefault(AdmissionDecisionStatus.REFUSED, 0));
        overview.setProcessed(processed);
        overview.setParallels(parallelRows);
        overview.setClassOptions(classOptions(academicYear));
        AdmissionHistoryContext history = admissionHistory(academicYear, source);
        overview.setCandidates(source.stream().map(candidate -> toRow(candidate, history)).toList());
        return overview;
    }

    private List<AdmissionDtos.ClassOption> classOptions(String academicYear) {
        try {
            ContingentDtos.StatsResponse stats = contingentService.getStats(academicYear, null);
            if (stats == null || stats.getColumns() == null) return List.of();
            Map<String, AdmissionDtos.ClassOption> byClass = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            stats.getColumns().stream()
                    .filter(Objects::nonNull)
                    .flatMap(building -> building.getAddresses() == null
                            ? java.util.stream.Stream.empty() : building.getAddresses().stream())
                    .filter(Objects::nonNull)
                    .flatMap(address -> address.getClasses() == null
                            ? java.util.stream.Stream.empty() : address.getClasses().stream())
                    .filter(Objects::nonNull)
                    .filter(item -> item.getClassName() != null && !item.getClassName().isBlank())
                    .forEach(item -> {
                        AdmissionDtos.ClassOption option = new AdmissionDtos.ClassOption();
                        option.setClassName(item.getClassName());
                        option.setParallel(item.getParallel());
                        option.setStudents(item.getStudents() == null ? 0 : item.getStudents());
                        AdmissionDtos.ClassOption previous = byClass.get(item.getClassName());
                        if (previous == null || option.getStudents() > previous.getStudents()) {
                            byClass.put(item.getClassName(), option);
                        }
                    });
            return new ArrayList<>(byClass.values());
        } catch (RuntimeException ignored) {
            // Приём остаётся доступным и до первой загрузки контингента.
            return List.of();
        }
    }

    private AdmissionDtos.CandidateRow toRow(AdmissionCandidate source, AdmissionHistoryContext history) {
        AdmissionDtos.CandidateRow row = new AdmissionDtos.CandidateRow();
        row.setId(source.getId());
        row.setFullName(source.getFullName());
        row.setRequestedParallel(source.getRequestedParallel());
        row.setDocumentStatus(source.getDocumentStatus());
        row.setProblems(source.getProblems());
        row.setComment(source.getComment());
        row.setAssignedClass(source.getAssignedClass());
        row.setDecisionStatus(source.getDecisionStatus());
        row.setProcessed(source.isProcessed());
        row.setTesting(source.isTesting());
        row.setAdditionalInfo(additionalInfo(source, history));
        row.setCreatedAt(source.getCreatedAt());
        row.setCreatedBy(source.getCreatedBy());
        row.setUpdatedAt(source.getUpdatedAt());
        row.setUpdatedBy(source.getUpdatedBy());
        return row;
    }

    private AdmissionHistoryContext admissionHistory(String academicYear, List<AdmissionCandidate> visibleCandidates) {
        Set<String> names = visibleCandidates.stream()
                .map(AdmissionCandidate::getFullName)
                .map(this::normalizeName)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (names.isEmpty()) return AdmissionHistoryContext.empty();

        Map<String, List<AdmissionCandidate>> admissionsByName = repository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(candidate -> names.contains(normalizeName(candidate.getFullName())))
                .sorted(Comparator.comparing(AdmissionCandidate::getAcademicYear,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdmissionCandidate::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(java.util.stream.Collectors.groupingBy(
                        candidate -> normalizeName(candidate.getFullName()), LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        List<StudentProfile> profiles = studentProfileRepository.findAllByNormalizedFullNameIn(names);
        Map<String, List<StudentProfile>> profilesByName = profiles.stream()
                .filter(Objects::nonNull)
                .filter(profile -> names.contains(profile.getNormalizedFullName()))
                .collect(java.util.stream.Collectors.groupingBy(
                        StudentProfile::getNormalizedFullName, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Set<Long> studentIds = profiles.stream().map(StudentProfile::getId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, List<StudentClassEnrollment>> enrollmentsByStudent = studentIds.isEmpty()
                ? Map.of()
                : enrollmentRepository.findAllByStudent_IdIn(studentIds).stream()
                .filter(enrollment -> enrollment.getStudent() != null && enrollment.getStudent().getId() != null)
                .collect(java.util.stream.Collectors.groupingBy(enrollment -> enrollment.getStudent().getId()));

        ContingentSnapshot latest = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null);
        List<ContingentStudent> currentRows = latest == null
                ? List.of() : contingentStudentRepository.findAllBySnapshotId(latest.getId());
        Set<Long> currentStudentIds = currentRows.stream()
                .map(ContingentStudent::getStudentId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> currentStudentNames = currentRows.stream()
                .map(ContingentStudent::getFullName).map(this::normalizeName).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return new AdmissionHistoryContext(admissionsByName, profilesByName, enrollmentsByStudent,
                currentStudentIds, currentStudentNames, latest != null);
    }

    private String additionalInfo(AdmissionCandidate source, AdmissionHistoryContext history) {
        String name = normalizeName(source.getFullName());
        List<String> details = new ArrayList<>();
        history.admissionsByName().getOrDefault(name, List.of()).stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), source.getId()))
                .map(this::previousAdmissionDescription)
                .distinct()
                .forEach(details::add);

        for (StudentProfile profile : history.profilesByName().getOrDefault(name, List.of())) {
            List<StudentClassEnrollment> enrollments = history.enrollmentsByStudent()
                    .getOrDefault(profile.getId(), List.of());
            LinkedHashSet<String> classes = enrollments.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(StudentClassEnrollment::getAcademicYear,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(StudentClassEnrollment::getValidFrom,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(enrollment -> normalize(enrollment.getClassName()) + " — " + normalize(enrollment.getAcademicYear()))
                    .filter(value -> !value.startsWith(" — ") && !value.endsWith(" — "))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (classes.isEmpty()) continue;
            boolean current = history.hasCurrentSnapshot()
                    && (history.currentStudentIds().contains(profile.getId()) || history.currentStudentNames().contains(name));
            String prefix = current
                    ? "История контингента (сейчас числится): "
                    : history.hasCurrentSnapshot()
                    ? "История контингента (в последней выгрузке отсутствует): "
                    : "История контингента: ";
            details.add(prefix + String.join("; ", classes));
        }
        return String.join("\n", new LinkedHashSet<>(details));
    }

    private String previousAdmissionDescription(AdmissionCandidate candidate) {
        String result;
        if (candidate.getDocumentStatus() == AdmissionDocumentStatus.DOCUMENTS_WITHDRAWN) {
            result = "документы отозваны";
        } else {
            AdmissionDecisionStatus decision = candidate.getDecisionStatus() == null
                    ? AdmissionDecisionStatus.PENDING : candidate.getDecisionStatus();
            result = switch (decision) {
                case AGREED -> "согласован" + assignedClassSuffix(candidate);
                case ENROLLED -> "зачислен" + assignedClassSuffix(candidate);
                case REFUSED -> "отказ";
                case PENDING -> "решение не принято";
            };
        }
        return "Ранее подавался (" + normalize(candidate.getAcademicYear()) + "): " + result;
    }

    private String assignedClassSuffix(AdmissionCandidate candidate) {
        String className = normalize(candidate.getAssignedClass());
        return className.isBlank() ? "" : " в " + className;
    }

    private String normalizeName(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
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

    private static final class MutableParallelStats {
        private int total;
        private int active;
        private int agreed;
        private int enrolled;
        private int refused;
    }

    private record AdmissionHistoryContext(
            Map<String, List<AdmissionCandidate>> admissionsByName,
            Map<String, List<StudentProfile>> profilesByName,
            Map<Long, List<StudentClassEnrollment>> enrollmentsByStudent,
            Set<Long> currentStudentIds,
            Set<String> currentStudentNames,
            boolean hasCurrentSnapshot
    ) {
        private static AdmissionHistoryContext empty() {
            return new AdmissionHistoryContext(Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), false);
        }
    }
}
