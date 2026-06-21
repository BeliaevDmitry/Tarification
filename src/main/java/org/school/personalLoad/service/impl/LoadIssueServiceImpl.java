package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.LoadIssueDtos;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.LoadIssueState;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.LoadIssueStateRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.service.LoadIssueService;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.util.CurriculumLoadStandard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class LoadIssueServiceImpl implements LoadIssueService {

    private static final String IMPORTANT_TALKS = "Разговоры о важном";
    private static final String RUSSIA_HORIZONS = "Россия мои горизонты";

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final LoadIssueStateRepository stateRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public LoadIssueDtos.LoadIssueResponse findIssues(String academicYear, String building) {
        String buildingFilter = normalize(building);
        LocalDate checkDate = defaultLoadDate(academicYear);
        List<ClassroomLeadershipEntry> classes = classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> buildingFilter.isBlank() || normalize(row.getNumberSchoolBuilding()).equals(buildingFilter))
                .toList();
        List<ManualLoadEntry> activeLoad = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> buildingFilter.isBlank() || normalize(row.getNumberSchoolBuilding()).equals(buildingFilter))
                .filter(row -> isActiveOn(row, checkDate))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(this::loadDuplicateKey, row -> row, (first, second) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
        Map<String, LoadIssueState> states = stateRepository.findAll().stream()
                .collect(Collectors.toMap(LoadIssueState::getIssueKey, state -> state, (first, second) -> first));

        List<LoadIssueDtos.LoadIssueRow> rows = new ArrayList<>();
        for (ClassroomLeadershipEntry classroom : classes) {
            addClassTeacherSubjectIssue(rows, states, academicYear, classroom, activeLoad, IMPORTANT_TALKS, "Разговоры о важном");
            addClassTeacherSubjectIssue(rows, states, academicYear, classroom, activeLoad, RUSSIA_HORIZONS, "Россия мои горизонты");
        }
        addMetaGroupIssues(rows, states, academicYear, classes, activeLoad);
        addMaximumLoadIssues(rows, states, academicYear, classes);
        rows.sort(Comparator.comparing(LoadIssueDtos.LoadIssueRow::building, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LoadIssueDtos.LoadIssueRow::type, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LoadIssueDtos.LoadIssueRow::description, String.CASE_INSENSITIVE_ORDER));
        int unresolved = (int) rows.stream().filter(row -> !row.resolved()).count();
        return new LoadIssueDtos.LoadIssueResponse(rows, unresolved);
    }

    private void addMaximumLoadIssues(List<LoadIssueDtos.LoadIssueRow> rows,
                                      Map<String, LoadIssueState> states,
                                      String academicYear,
                                      List<ClassroomLeadershipEntry> classes) {
        List<CurriculumPlanEntry> curriculum = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> !entry.isDeprecated())
                .filter(entry -> entry.getCurriculumPart() == null
                        || entry.getCurriculumPart() == CurriculumPart.CORE
                        || entry.getCurriculumPart() == CurriculumPart.FORMABLE)
                .toList();
        for (ClassroomLeadershipEntry classroom : classes) {
            Integer parallel = CurriculumLoadStandard.parallelOf(classroom.getClassName());
            if (parallel == null) continue;
            BigDecimal maximum = CurriculumLoadStandard.maxHours(parallel);
            List<CurriculumPlanEntry> classEntries = curriculum.stream()
                    .filter(entry -> same(entry.getNumberSchoolBuilding(), classroom.getNumberSchoolBuilding()))
                    .filter(entry -> same(entry.getClassName(), classroom.getClassName()))
                    .toList();
            BigDecimal year = sumCurriculumHours(classEntries, StudyPeriod.YEAR);
            BigDecimal firstHalf = year.add(sumCurriculumHours(classEntries, StudyPeriod.H1));
            BigDecimal secondHalf = year.add(sumCurriculumHours(classEntries, StudyPeriod.H2));
            if (firstHalf.compareTo(maximum) <= 0 && secondHalf.compareTo(maximum) <= 0) continue;

            String actual = firstHalf.compareTo(secondHalf) == 0
                    ? formatHours(firstHalf)
                    : formatHours(firstHalf) + "/" + formatHours(secondHalf);
            String description = "Корпус " + display(classroom.getNumberSchoolBuilding())
                    + ", класс " + display(classroom.getClassName())
                    + ". Основная + формируемая часть: " + actual
                    + " ч., максимально допустимо: " + formatHours(maximum) + " ч.";
            String key = String.join("|", "MAXIMUM_CURRICULUM_LOAD", academicYear,
                    normalize(classroom.getNumberSchoolBuilding()), normalize(classroom.getClassName()));
            rows.add(withState(key, classroom.getNumberSchoolBuilding(), "Превышена максимальная нагрузка", description,
                    states.get(key), "curriculum", classroom.getClassName(), ""));
        }
    }

    private BigDecimal sumCurriculumHours(List<CurriculumPlanEntry> entries, StudyPeriod period) {
        return entries.stream()
                .filter(entry -> entry.getStudyPeriod() == period)
                .map(CurriculumPlanEntry::getPlannedHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatHours(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    @Override
    @Transactional
    public LoadIssueDtos.LoadIssueRow updateState(LoadIssueDtos.LoadIssueUpdateRequest request) {
        String issueKey = request == null ? "" : display(request.key());
        if (issueKey.isBlank()) {
            throw new IllegalArgumentException("Ключ ошибки не указан");
        }
        LoadIssueState state = stateRepository.findById(issueKey).orElseGet(() -> {
            LoadIssueState created = new LoadIssueState();
            created.setIssueKey(issueKey);
            return created;
        });
        if (request != null && request.comment() != null) {
            state.setComment(request.comment());
        }
        if (request != null && request.resolved() != null) {
            state.setResolved(request.resolved());
        }
        state.setUpdatedAt(LocalDateTime.now());
        LoadIssueState saved = stateRepository.save(state);
        return new LoadIssueDtos.LoadIssueRow(saved.getIssueKey(), "", "", "", saved.getComment(), saved.isResolved(), "", "", "");
    }

    private void addClassTeacherSubjectIssue(List<LoadIssueDtos.LoadIssueRow> rows,
                                             Map<String, LoadIssueState> states,
                                             String academicYear,
                                             ClassroomLeadershipEntry classroom,
                                             List<ManualLoadEntry> activeLoad,
                                             String subjectName,
                                             String type) {
        List<ManualLoadEntry> subjectRows = activeLoad.stream()
                .filter(row -> same(row.getClassName(), classroom.getClassName()))
                .filter(row -> sameSubject(row.getSubjectName(), subjectName))
                .toList();
        boolean hasClassTeacher = subjectRows.stream()
                .anyMatch(row -> sameTeacher(row, classroom));
        if (hasClassTeacher) {
            return;
        }
        String inLoad = subjectRows.isEmpty()
                ? "не назначено"
                : subjectRows.stream()
                .map(row -> display(row.getFioTeacher()))
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        String description = "Корпус " + display(classroom.getNumberSchoolBuilding())
                + ", класс " + display(classroom.getClassName())
                + ". Классный руководитель: " + display(classroom.getFioTeacher())
                + "; в нагрузке: " + (inLoad.isBlank() ? "не назначено" : inLoad) + ".";
        String key = String.join("|", "CLASS_TEACHER_SUBJECT", academicYear, normalize(classroom.getNumberSchoolBuilding()),
                normalize(classroom.getClassName()), normalize(subjectName));
        rows.add(withState(key, classroom.getNumberSchoolBuilding(), type, description, states.get(key),
                "load", classroom.getClassName(), subjectName));
    }

    private void addMetaGroupIssues(List<LoadIssueDtos.LoadIssueRow> rows,
                                    Map<String, LoadIssueState> states,
                                    String academicYear,
                                    List<ClassroomLeadershipEntry> classes,
                                    List<ManualLoadEntry> activeLoad) {
        Map<String, List<ClassroomLeadershipEntry>> byTeacher = classes.stream()
                .filter(row -> !normalize(row.getFioTeacher()).isBlank())
                .collect(Collectors.groupingBy(this::teacherKey, LinkedHashMap::new, Collectors.toList()));
        byTeacher.values().stream()
                .filter(list -> list.size() > 1)
                .forEach(list -> {
                    ClassroomLeadershipEntry first = list.get(0);
                    List<String> classNames = list.stream()
                            .map(ClassroomLeadershipEntry::getClassName)
                            .map(this::display)
                            .distinct()
                            .toList();
                    boolean hasClassLevelRequiredSubjects = activeLoad.stream()
                            .filter(row -> sameTeacher(row, first))
                            .filter(row -> sameSubject(row.getSubjectName(), IMPORTANT_TALKS) || sameSubject(row.getSubjectName(), RUSSIA_HORIZONS))
                            .anyMatch(row -> row.getMetaGroupId() == null && !display(row.getClassName()).toUpperCase(Locale.ROOT).startsWith("МГ:"));
                    if (!hasClassLevelRequiredSubjects) {
                        return;
                    }
                    String description = "Корпус " + display(first.getNumberSchoolBuilding())
                            + ". У " + display(first.getFioTeacher()) + " два класса: " + String.join(", ", classNames)
                            + ". Разговоры о важном и Россия мои горизонты должны стоять на метагруппе.";
                    String key = String.join("|", "METAGROUP_REQUIRED", academicYear, normalize(first.getFioTeacher()), String.join(",", classNames));
                    rows.add(withState(key, first.getNumberSchoolBuilding(), "Требуется метагруппа", description,
                            states.get(key), "load", first.getClassName(), IMPORTANT_TALKS));
                });
    }

    private LoadIssueDtos.LoadIssueRow withState(String key,
                                                 String building,
                                                 String type,
                                                 String description,
                                                 LoadIssueState state,
                                                 String targetPage,
                                                 String targetClass,
                                                 String targetSubject) {
        return new LoadIssueDtos.LoadIssueRow(
                key,
                display(building),
                type,
                description,
                state == null ? "" : Optional.ofNullable(state.getComment()).orElse(""),
                state != null && state.isResolved(),
                targetPage,
                display(targetClass),
                display(targetSubject)
        );
    }

    private boolean isActiveOn(ManualLoadEntry row, LocalDate date) {
        return (row.getLoadFromDate() == null || !row.getLoadFromDate().isAfter(date))
                && (row.getLoadToDate() == null || !row.getLoadToDate().isBefore(date));
    }

    private LocalDate defaultLoadDate(String academicYear) {
        try {
            int year = Integer.parseInt(String.valueOf(academicYear).split("/")[0]);
            LocalDate start = LocalDate.of(year, 9, 1);
            LocalDate end = LocalDate.of(year + 1, 8, 31);
            LocalDate today = LocalDate.now();
            return (!today.isBefore(start) && !today.isAfter(end)) ? today : start;
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private String teacherKey(ClassroomLeadershipEntry row) {
        Long teacherId = row.getTeacherId();
        return teacherId == null ? "fio:" + normalize(row.getFioTeacher()) : "id:" + teacherId;
    }

    private boolean sameTeacher(ManualLoadEntry loadRow, ClassroomLeadershipEntry classroom) {
        Long loadTeacherId = loadRow.getTeacherId();
        Long classTeacherId = classroom.getTeacherId();
        if (loadTeacherId != null && classTeacherId != null) {
            return loadTeacherId.equals(classTeacherId);
        }
        return same(loadRow.getFioTeacher(), classroom.getFioTeacher());
    }

    private String loadDuplicateKey(ManualLoadEntry row) {
        return String.join("|",
                normalize(row.getFioTeacher()),
                normalize(row.getNumberSchoolBuilding()),
                normalize(row.getClassName()),
                normalize(row.getSubjectName()),
                row.getCurriculumPart() == null ? CurriculumPart.CORE.name() : row.getCurriculumPart().name(),
                normalize(row.getGroupNameEducationalPlan()),
                row.getStudyPeriod() == null ? StudyPeriod.YEAR.name() : row.getStudyPeriod().name(),
                String.valueOf(row.getLoadFromDate()),
                String.valueOf(row.getLoadToDate()),
                String.valueOf(row.getGroupLoad() == null ? row.getLoad() : row.getGroupLoad())
        );
    }

    private boolean sameSubject(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return display(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private String display(String value) {
        return Objects.toString(value, "").trim().replaceAll("\\s+", " ");
    }
}
