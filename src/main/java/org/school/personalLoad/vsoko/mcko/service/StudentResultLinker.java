package org.school.personalLoad.vsoko.mcko.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentNameHistory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentLinkStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentResultLinker {
    private final StudentProfileRepository profileRepository;
    private final StudentNameHistoryRepository nameHistoryRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public LinkIndex buildIndex() {
        List<StudentProfile> profiles = profileRepository.findAll();
        Map<Long, StudentProfile> byId = new LinkedHashMap<>();
        Map<String, LinkedHashSet<Long>> byCode = new HashMap<>();
        Map<String, LinkedHashSet<Long>> byName = new HashMap<>();
        for (StudentProfile profile : profiles) {
            if (profile.getId() == null) continue;
            byId.put(profile.getId(), profile);
            add(byCode, normalizeCode(profile.getNormalizedRecordNumber()), profile.getId());
            addNameKeys(byName, profile.getCurrentFullName(), profile.getId());
        }
        for (StudentNameHistory history : nameHistoryRepository.findAll()) {
            if (history.getStudent() != null && history.getStudent().getId() != null) {
                addNameKeys(byName, history.getFullName(), history.getStudent().getId());
            }
        }
        Map<String, LinkedHashSet<Long>> byYearClass = new HashMap<>();
        for (StudentClassEnrollment enrollment : enrollmentRepository.findAll()) {
            if (enrollment.getStudent() == null || enrollment.getStudent().getId() == null) continue;
            add(byYearClass, yearClassKey(enrollment.getAcademicYear(), enrollment.getClassName()),
                    enrollment.getStudent().getId());
        }
        return new LinkIndex(byId, byCode, byName, byYearClass);
    }

    private static void addNameKeys(Map<String, LinkedHashSet<Long>> target, String fullName, Long studentId) {
        String normalized = normalizeName(fullName);
        add(target, normalized, studentId);
        String[] parts = normalized.split(" ");
        if (parts.length >= 2) {
            add(target, parts[0] + " " + parts[1], studentId);
        }
    }

    private static void add(Map<String, LinkedHashSet<Long>> target, String key, Long studentId) {
        if (key == null || key.isBlank() || studentId == null) return;
        target.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(studentId);
    }

    public static String normalizeName(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9 -]", " ").replaceAll("\\s+", " ").trim();
    }

    public static String normalizeCode(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static String normalizeClass(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", "");
        return normalized.replaceAll("^(1[01]|[1-9])-?([а-яa-z])$", "$1-$2");
    }

    private static String yearClassKey(String academicYear, String className) {
        return clean(academicYear).replace('-', '/') + "|" + normalizeClass(className);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record LinkResult(Long studentId, MckoStudentLinkStatus status, String message) {}

    public static final class LinkIndex {
        private final Map<Long, StudentProfile> byId;
        private final Map<String, LinkedHashSet<Long>> byCode;
        private final Map<String, LinkedHashSet<Long>> byName;
        private final Map<String, LinkedHashSet<Long>> byYearClass;

        private LinkIndex(Map<Long, StudentProfile> byId,
                          Map<String, LinkedHashSet<Long>> byCode,
                          Map<String, LinkedHashSet<Long>> byName,
                          Map<String, LinkedHashSet<Long>> byYearClass) {
            this.byId = byId;
            this.byCode = byCode;
            this.byName = byName;
            this.byYearClass = byYearClass;
        }

        public LinkResult resolve(String studentCode, String fullName, String academicYear, String className) {
            String code = normalizeCode(studentCode);
            if (!code.isBlank()) {
                LinkResult byCodeResult = single(byCode.getOrDefault(code, new LinkedHashSet<>()),
                        MckoStudentLinkStatus.LINKED_BY_CODE,
                        "Карточка найдена по коду ученика");
                if (byCodeResult != null) return byCodeResult;
            }

            String name = normalizeName(fullName);
            LinkedHashSet<Long> nameCandidates = new LinkedHashSet<>(byName.getOrDefault(name, new LinkedHashSet<>()));
            if (nameCandidates.isEmpty()) {
                String[] parts = name.split(" ");
                if (parts.length >= 2) {
                    nameCandidates.addAll(byName.getOrDefault(parts[0] + " " + parts[1], new LinkedHashSet<>()));
                }
            }
            if (!nameCandidates.isEmpty() && !normalizeClass(className).isBlank()) {
                Set<Long> classCandidates = byYearClass.getOrDefault(yearClassKey(academicYear, className), new LinkedHashSet<>());
                if (!classCandidates.isEmpty()) {
                    nameCandidates.retainAll(classCandidates);
                }
            }
            LinkResult byNameResult = single(nameCandidates, MckoStudentLinkStatus.LINKED_BY_NAME_AND_CLASS,
                    "Карточка найдена по ФИО и классу в учебном году");
            if (byNameResult != null) return byNameResult;

            if (nameCandidates.size() > 1) {
                return new LinkResult(null, MckoStudentLinkStatus.AMBIGUOUS,
                        "Найдено несколько подходящих карточек ребёнка");
            }
            return new LinkResult(null, MckoStudentLinkStatus.NOT_FOUND,
                    code.isBlank()
                            ? "Карточка ребёнка не найдена по ФИО и классу"
                            : "Код ученика и ФИО не найдены в карточках контингента");
        }

        public StudentProfile profile(Long id) {
            return byId.get(id);
        }

        private LinkResult single(Set<Long> candidates, MckoStudentLinkStatus status, String message) {
            if (candidates == null || candidates.isEmpty()) return null;
            if (candidates.size() == 1) return new LinkResult(candidates.iterator().next(), status, message);
            return new LinkResult(null, MckoStudentLinkStatus.AMBIGUOUS,
                    "Найдено несколько подходящих карточек ребёнка");
        }
    }
}
