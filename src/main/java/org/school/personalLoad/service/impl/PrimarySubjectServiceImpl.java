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
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> toRow(teacher, assignments.get(teacher.getId()), subjectsByTeacher.getOrDefault(teacher.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public PrimarySubjectDtos.DetermineResult determine(String academicYear) {
        List<PrimarySubjectRule> rules = getRules();
        Map<Long, List<ManualLoadEntry>> loadByTeacher = manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> row.getTeacherId() != null)
                .collect(Collectors.groupingBy(ManualLoadEntry::getTeacherId));
        Map<Long, TeacherPrimarySubjectAssignment> existing = assignmentRepository.findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.toMap(TeacherPrimarySubjectAssignment::getTeacherId, Function.identity()));
        int assigned = 0;
        int preservedManual = 0;
        int unresolved = 0;
        List<TeacherDirectoryEntry> teachers = teacherRepository.findAll();
        for (TeacherDirectoryEntry teacher : teachers) {
            TeacherPrimarySubjectAssignment current = existing.get(teacher.getId());
            if (current != null && current.getMode() == PrimarySubjectAssignmentMode.MANUAL) {
                preservedManual++;
                continue;
            }
            String detected = detect(loadByTeacher.getOrDefault(teacher.getId(), List.of()), rules);
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
    public Map<String, String> resolveForExport(String academicYear) {
        List<TeacherPrimarySubjectAssignment> assignments = assignmentRepository.findAllByAcademicYear(academicYear);
        Set<Long> assignedTeacherIds = assignments.stream()
                .map(TeacherPrimarySubjectAssignment::getTeacherId)
                .collect(Collectors.toSet());
        boolean hasMissing = manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .map(ManualLoadEntry::getTeacherId)
                .filter(Objects::nonNull)
                .anyMatch(teacherId -> !assignedTeacherIds.contains(teacherId));
        if (hasMissing) {
            determine(academicYear);
            assignments = assignmentRepository.findAllByAcademicYear(academicYear);
        }
        Map<Long, String> fioById = teacherRepository.findAll().stream()
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, TeacherDirectoryEntry::getFioTeacher));
        Map<String, String> result = new HashMap<>();
        for (TeacherPrimarySubjectAssignment assignment : assignments) {
            String fio = fioById.get(assignment.getTeacherId());
            if (fio != null) {
                result.put(normalizeKey(fio), assignment.getPrimarySubject());
            }
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
        String normalizedSubject = normalizeKey(subjectName).replace('Ё', 'Е');
        if (normalizedSubject.isBlank()) {
            return "";
        }
        for (PrimarySubjectRule rule : rules) {
            if (rule.getRuleType() != PrimarySubjectRuleType.KEYWORDS) {
                continue;
            }
            boolean matches = Arrays.stream(String.valueOf(rule.getRuleValue()).split("[,;\\n]"))
                    .map(this::normalizeKey)
                    .filter(keyword -> !keyword.isBlank())
                    .anyMatch(normalizedSubject::contains);
            if (matches) {
                return rule.getPrimarySubject();
            }
        }
        return normalizeDisplay(subjectName);
    }

    private Map<Long, List<String>> loadSubjectsByTeacher(String academicYear) {
        return manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> row.getTeacherId() != null)
                .filter(row -> !normalizeDisplay(row.getSubjectName()).isBlank())
                .collect(Collectors.groupingBy(
                        ManualLoadEntry::getTeacherId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(row -> normalizeDisplay(row.getSubjectName()), Collectors.toCollection(TreeSet::new)),
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
        if (ruleRepository.count() > 0) {
            return;
        }
        saveDefault("Начальная школа", PrimarySubjectRuleType.PRIMARY_GRADES, "1-4 классы", 10);
        saveDefault("Русский язык и литература", PrimarySubjectRuleType.KEYWORDS, "русск, литерат", 20);
        saveDefault("Математика", PrimarySubjectRuleType.KEYWORDS, "математ, алгебр, лгебр, геометр, вероятн", 30);
        saveDefault("Информатика", PrimarySubjectRuleType.KEYWORDS, "информат", 40);
        saveDefault("Физ-ра (ОБЗР)", PrimarySubjectRuleType.KEYWORDS, "физическ, физ-ра, физра, физкультур, обзр, обж, основы безопасности", 50);
        saveDefault("Физика", PrimarySubjectRuleType.KEYWORDS, "физик", 60);
        saveDefault("Биология", PrimarySubjectRuleType.KEYWORDS, "биолог", 70);
        saveDefault("Химия", PrimarySubjectRuleType.KEYWORDS, "хими", 80);
        saveDefault("География", PrimarySubjectRuleType.KEYWORDS, "географ", 90);
        saveDefault("История", PrimarySubjectRuleType.KEYWORDS, "истор", 100);
        saveDefault("Ин. Язык", PrimarySubjectRuleType.KEYWORDS, "иностран, ин. язык, англий, немец, француз, испан, китай", 110);
        saveDefault("Обществознание", PrimarySubjectRuleType.KEYWORDS, "обществ", 120);
        saveDefault("Труд (технология)", PrimarySubjectRuleType.KEYWORDS, "труд, технолог", 130);
        saveDefault("ИЗО", PrimarySubjectRuleType.KEYWORDS, "изо, изобраз", 140);
        saveDefault("Музыка", PrimarySubjectRuleType.KEYWORDS, "музык", 150);
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
        return normalizeDisplay(value).toUpperCase(Locale.ROOT);
    }
}
