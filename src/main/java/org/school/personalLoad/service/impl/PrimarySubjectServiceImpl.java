package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.PrimarySubjectDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.PrimarySubjectRuleRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherPrimarySubjectAssignmentRepository;
import org.school.personalLoad.service.PrimarySubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrimarySubjectServiceImpl implements PrimarySubjectService {

    private static final Map<String, String> LEGACY_DEFAULT_RULE_VALUES = Map.ofEntries(
            Map.entry("Русский язык и литература", "русск, литерат"),
            Map.entry("Математика", "математ, алгебр, лгебр, геометр, вероятн"),
            Map.entry("Информатика", "информат"),
            Map.entry("Физ-ра (ОБЗР)", "физическ, физ-ра, физра, физкультур, обзр, обж, основы безопасности"),
            Map.entry("Физика", "физик"),
            Map.entry("Биология", "биолог"),
            Map.entry("Химия", "хими"),
            Map.entry("География", "географ"),
            Map.entry("История", "истор"),
            Map.entry("Ин. Язык", "иностран, ин. язык, англий, немец, француз, испан, китай"),
            Map.entry("Обществознание", "обществ"),
            Map.entry("Труд (технология)", "труд, технолог"),
            Map.entry("ИЗО", "изо, изобраз"),
            Map.entry("Музыка", "музык")
    );

    private static final Map<String, String> COMPLETE_DEFAULT_RULE_VALUES = Map.ofEntries(
            Map.entry("Русский язык и литература",
                    "русск, литерат, комплексный анализ текста"),
            Map.entry("Математика",
                    "математ, матемамик, алгебр, лгебр, геометр, вероятн, статистик, инструменты компьютерной математики"),
            Map.entry("Информатика",
                    "информат, алгоритмик, ит спеиальност, программирован, програмирован, информационная безопасность, искусственный интеллект"),
            Map.entry("Физ-ра (ОБЗР)",
                    "физическая культура, физ-ра, физра, физкультур, спортивные игры, обзр, обж, основы безопасности"),
            Map.entry("Физика",
                    "физик, физический практикум"),
            Map.entry("Биология",
                    "биолог, методы познания окружающего мира, окружающий мир"),
            Map.entry("Химия",
                    "хими, лабораторный практикум по химии"),
            Map.entry("География",
                    "географ"),
            Map.entry("История",
                    "истор"),
            Map.entry("Ин. Язык",
                    "иностран, ин. язык, англий, немец, француз, испан, китай, лингвистистический практикум"),
            Map.entry("Обществознание",
                    "обществ, духовно-нравствен, религиозных культур, светской этики, журналистика, медиа, индивидуальный проект, лидерство, предприниматель, практикум по праву, проектная и исследовательская деятельность, разговоры о важном, россия мои горизонты, функциональная грамотность, экономика"),
            Map.entry("Труд (технология)",
                    "труд, технолог, 3d-моделирование, 3d-печать, робототехник, автономных систем"),
            Map.entry("ИЗО",
                    "изо, изобраз, исскуство, искусство"),
            Map.entry("Музыка",
                    "музык")
    );

    private final PrimarySubjectRuleRepository ruleRepository;
    private final TeacherPrimarySubjectAssignmentRepository assignmentRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final ManualLoadEntryRepository manualLoadRepository;

    @Override
    @Transactional
    public List<PrimarySubjectDtos.TeacherPrimarySubjectRow> getTeacherAssignments(String academicYear) {
        ensureDefaultRules();
        Map<Long, TeacherPrimarySubjectAssignment> assignments = assignmentRepository.findAllByAcademicYear(academicYear)
                .stream()
                .collect(Collectors.toMap(TeacherPrimarySubjectAssignment::getTeacherId, Function.identity()));
        Map<Long, List<String>> subjectsByTeacher = loadSubjectsByTeacher(academicYear);
        return teacherRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> toRow(teacher, assignments.get(teacher.getId()), subjectsByTeacher.getOrDefault(teacher.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public PrimarySubjectDtos.DetermineResult determine(String academicYear) {
        List<PrimarySubjectRule> rules = getRules();
        List<ManualLoadEntry> loadRows = manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> !row.isIupLoad())
                .toList();
        Map<Long, List<ManualLoadEntry>> loadByTeacher = loadRows.stream()
                .filter(row -> row.getTeacherId() != null)
                .collect(Collectors.groupingBy(ManualLoadEntry::getTeacherId));
        Map<String, List<ManualLoadEntry>> loadByTeacherFio = loadRows.stream()
                .filter(row -> !normalizeKey(row.getFioTeacher()).isBlank())
                .collect(Collectors.groupingBy(row -> normalizeKey(row.getFioTeacher())));
        Map<Long, TeacherPrimarySubjectAssignment> existing = assignmentRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.toMap(TeacherPrimarySubjectAssignment::getTeacherId, Function.identity()));
        int assigned = 0;
        int preservedManual = 0;
        int unresolved = 0;
        List<TeacherDirectoryEntry> teachers = teacherRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .toList();
        for (TeacherDirectoryEntry teacher : teachers) {
            TeacherPrimarySubjectAssignment current = existing.get(teacher.getId());
            if (current != null && current.getMode() == PrimarySubjectAssignmentMode.MANUAL) {
                preservedManual++;
                continue;
            }
            List<ManualLoadEntry> teacherRows = loadByTeacher.getOrDefault(teacher.getId(), List.of());
            if (teacherRows.isEmpty()) {
                teacherRows = loadByTeacherFio.getOrDefault(normalizeKey(teacher.getFioTeacher()), List.of());
            }
            String detected = detect(teacherRows, rules);
            if (detected.isBlank()) {
                unresolved++;
                continue;
            }
            TeacherPrimarySubjectAssignment assignment = current == null ? new TeacherPrimarySubjectAssignment() : current;
            assignment.setAcademicYear(academicYear);
            assignment.setTeacherId(teacher.getId());
            assignment.setPrimarySubject(detected);
            assignment.setMode(PrimarySubjectAssignmentMode.AUTO);
            assignment.setUpdatedAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
            assigned++;
        }
        return new PrimarySubjectDtos.DetermineResult(teachers.size(), assigned, preservedManual, unresolved);
    }

    @Override
    @Transactional
    public PrimarySubjectDtos.TeacherPrimarySubjectRow setManual(String academicYear, Long teacherId, String primarySubject) {
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Педагог не найден"));
        String normalizedSubject = normalizeDisplay(primarySubject);
        if (normalizedSubject.isBlank()) {
            throw new IllegalArgumentException("Основной предмет обязателен");
        }
        TeacherPrimarySubjectAssignment assignment = assignmentRepository.findByAcademicYearAndTeacherId(academicYear, teacherId)
                .orElseGet(TeacherPrimarySubjectAssignment::new);
        assignment.setAcademicYear(academicYear);
        assignment.setTeacherId(teacherId);
        assignment.setPrimarySubject(normalizedSubject);
        assignment.setMode(PrimarySubjectAssignmentMode.MANUAL);
        assignment.setUpdatedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        return toRow(teacher, assignment, loadSubjectsByTeacher(academicYear).getOrDefault(teacherId, List.of()));
    }

    @Override
    @Transactional
    public void clearAssignment(String academicYear, Long teacherId) {
        assignmentRepository.findByAcademicYearAndTeacherId(academicYear, teacherId)
                .ifPresent(assignmentRepository::delete);
    }

    @Override
    @Transactional
    public List<PrimarySubjectRule> getRules() {
        ensureDefaultRules();
        return ruleRepository.findAllByOrderByPriorityAscPrimarySubjectAsc();
    }

    @Override
    @Transactional
    public PrimarySubjectRule saveRule(Long id, PrimarySubjectDtos.RuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Данные правила обязательны");
        }
        String primarySubject = normalizeDisplay(request.primarySubject());
        if (primarySubject.isBlank()) {
            throw new IllegalArgumentException("Основной предмет обязателен");
        }
        PrimarySubjectRule rule = id == null
                ? ruleRepository.findByPrimarySubjectIgnoreCase(primarySubject).orElseGet(PrimarySubjectRule::new)
                : ruleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Правило не найдено"));
        rule.setPrimarySubject(primarySubject);
        rule.setRuleType(request.ruleType() == null ? PrimarySubjectRuleType.KEYWORDS : request.ruleType());
        rule.setRuleValue(normalizeDisplay(request.ruleValue()));
        rule.setPriority(request.priority() == null ? 100 : request.priority());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Map<Long, String> resolveForExport(String academicYear) {
        List<TeacherPrimarySubjectAssignment> assignments = assignmentRepository.findAllByAcademicYear(academicYear);
        Set<Long> assignedTeacherIds = assignments.stream()
                .map(TeacherPrimarySubjectAssignment::getTeacherId)
                .collect(Collectors.toSet());
        List<ManualLoadEntry> loadRows = manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> !row.isIupLoad())
                .toList();
        loadRows.stream()
                .filter(row -> row.getTeacherId() == null)
                .findFirst()
                .ifPresent(row -> {
                    throw new IllegalStateException("У педагога не заполнен teacherId: " + row.getFioTeacher());
                });
        boolean hasMissing = loadRows.stream()
                .map(ManualLoadEntry::getTeacherId)
                .anyMatch(teacherId -> !assignedTeacherIds.contains(teacherId));
        if (hasMissing) {
            determine(academicYear);
            assignments = assignmentRepository.findAllByAcademicYear(academicYear);
        }
        Map<Long, String> result = new HashMap<>();
        for (TeacherPrimarySubjectAssignment assignment : assignments) {
            result.put(assignment.getTeacherId(), assignment.getPrimarySubject());
        }
        return result;
    }

    private String detect(List<ManualLoadEntry> rows, List<PrimarySubjectRule> rules) {
        if (rows.isEmpty()) {
            return "";
        }
        Optional<PrimarySubjectRule> primaryGradesRule = rules.stream()
                .filter(rule -> rule.getRuleType() == PrimarySubjectRuleType.PRIMARY_GRADES)
                .findFirst();
        boolean hasPrimaryGrades = rows.stream()
                .map(ManualLoadEntry::getClassName)
                .map(ClassNameNormalizer::extractParallel)
                .filter(Objects::nonNull)
                .anyMatch(parallel -> parallel >= 1 && parallel <= 4);
        if (hasPrimaryGrades && primaryGradesRule.isPresent()) {
            return primaryGradesRule.get().getPrimarySubject();
        }
        Map<String, Integer> hoursByPrimarySubject = new LinkedHashMap<>();
        for (ManualLoadEntry row : rows) {
            String primarySubject = classify(row.getSubjectName(), rules);
            if (!primarySubject.isBlank()) {
                int hours = row.getGroupLoad() != null ? row.getGroupLoad() : Optional.ofNullable(row.getLoad()).orElse(0);
                hoursByPrimarySubject.merge(primarySubject, hours, Integer::sum);
            }
        }
        return hoursByPrimarySubject.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private String classify(String subjectName, List<PrimarySubjectRule> rules) {
        String normalizedSubject = normalizeKey(subjectName);
        if (normalizedSubject.isBlank()) {
            return "";
        }
        PrimarySubjectRule bestRule = null;
        int bestKeywordLength = -1;
        for (PrimarySubjectRule rule : rules) {
            if (rule.getRuleType() != PrimarySubjectRuleType.KEYWORDS) {
                continue;
            }
            int matchingKeywordLength = Arrays.stream(String.valueOf(rule.getRuleValue()).split("[,;\\n]"))
                    .map(this::normalizeKey)
                    .filter(keyword -> !keyword.isBlank())
                    .filter(normalizedSubject::contains)
                    .mapToInt(String::length)
                    .max()
                    .orElse(-1);
            if (matchingKeywordLength > bestKeywordLength) {
                bestKeywordLength = matchingKeywordLength;
                bestRule = rule;
            }
        }
        return bestRule == null ? normalizeDisplay(subjectName) : bestRule.getPrimarySubject();
    }

    private Map<Long, List<String>> loadSubjectsByTeacher(String academicYear) {
        Map<String, Long> teacherIdByFio = teacherRepository.findAll().stream()
                .collect(Collectors.toMap(
                        teacher -> normalizeKey(teacher.getFioTeacher()),
                        TeacherDirectoryEntry::getId,
                        (first, second) -> first
                ));
        return manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> !row.isIupLoad())
                .filter(row -> !normalizeDisplay(row.getSubjectName()).isBlank())
                .map(row -> new AbstractMap.SimpleEntry<>(
                        row.getTeacherId() != null ? row.getTeacherId() : teacherIdByFio.get(normalizeKey(row.getFioTeacher())),
                        normalizeDisplay(row.getSubjectName())
                ))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.collectingAndThen(
                                Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(TreeSet::new)),
                                ArrayList::new
                        )
                ));
    }

    private PrimarySubjectDtos.TeacherPrimarySubjectRow toRow(TeacherDirectoryEntry teacher,
                                                               TeacherPrimarySubjectAssignment assignment,
                                                               List<String> loadSubjects) {
        return new PrimarySubjectDtos.TeacherPrimarySubjectRow(
                teacher.getId(),
                teacher.getFioTeacher(),
                assignment == null ? "" : assignment.getPrimarySubject(),
                assignment == null ? null : assignment.getMode(),
                loadSubjects
        );
    }

    private void ensureDefaultRules() {
        if (ruleRepository.count() == 0) {
            saveDefault("Начальная школа", PrimarySubjectRuleType.PRIMARY_GRADES, "1-4 классы", 10);
            saveDefault("Русский язык и литература", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Русский язык и литература"), 20);
            saveDefault("Математика", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Математика"), 30);
            saveDefault("Информатика", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Информатика"), 40);
            saveDefault("Физ-ра (ОБЗР)", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Физ-ра (ОБЗР)"), 50);
            saveDefault("Физика", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Физика"), 60);
            saveDefault("Биология", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Биология"), 70);
            saveDefault("Химия", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Химия"), 80);
            saveDefault("География", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("География"), 90);
            saveDefault("История", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("История"), 100);
            saveDefault("Ин. Язык", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Ин. Язык"), 110);
            saveDefault("Обществознание", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Обществознание"), 120);
            saveDefault("Труд (технология)", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Труд (технология)"), 130);
            saveDefault("ИЗО", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("ИЗО"), 140);
            saveDefault("Музыка", PrimarySubjectRuleType.KEYWORDS, COMPLETE_DEFAULT_RULE_VALUES.get("Музыка"), 150);
            return;
        }
        for (Map.Entry<String, String> entry : COMPLETE_DEFAULT_RULE_VALUES.entrySet()) {
            ruleRepository.findByPrimarySubjectIgnoreCase(entry.getKey()).ifPresent(rule -> {
                String legacyValue = LEGACY_DEFAULT_RULE_VALUES.get(entry.getKey());
                if (legacyValue != null && normalizeDisplay(rule.getRuleValue()).equals(normalizeDisplay(legacyValue))) {
                    rule.setRuleValue(entry.getValue());
                    rule.setUpdatedAt(LocalDateTime.now());
                    ruleRepository.save(rule);
                }
            });
        }
    }

    private void saveDefault(String subject, PrimarySubjectRuleType type, String value, int priority) {
        PrimarySubjectRule rule = new PrimarySubjectRule();
        rule.setPrimarySubject(subject);
        rule.setRuleType(type);
        rule.setRuleValue(value);
        rule.setPriority(priority);
        ruleRepository.save(rule);
    }

    private String normalizeDisplay(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ");
    }

    private String normalizeKey(String value) {
        return normalizeDisplay(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
