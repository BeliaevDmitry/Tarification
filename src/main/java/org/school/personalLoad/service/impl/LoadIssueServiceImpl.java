package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.LoadIssueDtos;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.LoadIssueState;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
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
    private static final String VACANCY_TEACHER = "Вакансия";

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final LoadIssueStateRepository stateRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;

    @Override
    @Transactional(readOnly = true)
    public LoadIssueDtos.LoadIssueResponse findIssues(String academicYear, String building) {
        String buildingFilter = normalize(building);
        LocalDate checkDate = defaultLoadDate(academicYear);
        List<ClassroomLeadershipEntry> classes = classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> buildingFilter.isBlank() || normalize(row.getNumberSchoolBuilding()).equals(buildingFilter))
                .toList();
        List<ManualLoadEntry> yearLoad = manualLoadEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> buildingFilter.isBlank() || normalize(row.getNumberSchoolBuilding()).equals(buildingFilter))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(this::loadDuplicateKey, row -> row, (first, second) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
        List<ManualLoadEntry> activeLoad = yearLoad.stream()
                .filter(row -> isActiveOn(row, checkDate))
                .toList();
        List<CurriculumPlanEntry> curriculum = curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> !entry.isDeprecated())
                .filter(entry -> buildingFilter.isBlank() || normalize(entry.getNumberSchoolBuilding()).equals(buildingFilter))
                .toList();
        Map<String, LoadIssueState> states = stateRepository.findAll().stream()
                .collect(Collectors.toMap(LoadIssueState::getIssueKey, state -> state, (first, second) -> first));

        List<LoadIssueDtos.LoadIssueRow> rows = new ArrayList<>();
        for (ClassroomLeadershipEntry classroom : classes) {
            addClassTeacherSubjectIssue(rows, states, academicYear, classroom, yearLoad, curriculum, IMPORTANT_TALKS, "Разговоры о важном");
            addClassTeacherSubjectIssue(rows, states, academicYear, classroom, yearLoad, curriculum, RUSSIA_HORIZONS, "Россия мои горизонты");
        }
        addMetaGroupIssues(rows, states, academicYear, classes, activeLoad);
        addMaximumLoadIssues(rows, states, academicYear, classes);
        addDismissalHandoffIssues(rows, states, academicYear, yearLoad);
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
                                             List<CurriculumPlanEntry> curriculum,
                                             String subjectName,
                                             String type) {
        boolean existsInManualLoadCurriculum = curriculum.stream()
                .filter(row -> same(row.getNumberSchoolBuilding(), classroom.getNumberSchoolBuilding()))
                .filter(row -> same(row.getClassName(), classroom.getClassName()))
                .filter(row -> sameSubject(row.getSubjectName(), subjectName))
                .filter(this::contributesToManualLoad)
                .anyMatch(this::hasPositiveHours);
        if (!existsInManualLoadCurriculum) {
            return;
        }
        List<ManualLoadEntry> subjectRows = activeLoad.stream()
                .filter(row -> same(row.getNumberSchoolBuilding(), classroom.getNumberSchoolBuilding()))
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
                + "; в нагрузке по предмету стоит: " + (inLoad.isBlank() ? "не назначено" : inLoad) + ".";
        String key = String.join("|", "CLASS_TEACHER_SUBJECT", academicYear, normalize(classroom.getNumberSchoolBuilding()),
                normalize(classroom.getClassName()), normalize(subjectName));
        rows.add(withState(key, classroom.getNumberSchoolBuilding(), type, description, states.get(key),
                "load", classroom.getClassName(), subjectName));
    }

    private boolean contributesToManualLoad(CurriculumPlanEntry row) {
        return row.getMetaGroupId() != null
                || display(row.getClassName()).toUpperCase(Locale.ROOT).startsWith("МГ:")
                || !row.isExcludedFromManualLoad();
    }

    private boolean hasPositiveHours(CurriculumPlanEntry row) {
        return (row.getPlannedHours() != null && row.getPlannedHours().compareTo(BigDecimal.ZERO) > 0)
                || (row.getSubgroup1Hours() != null && row.getSubgroup1Hours() > 0)
                || (row.getSubgroup2Hours() != null && row.getSubgroup2Hours() > 0);
    }

    private void addDismissalHandoffIssues(List<LoadIssueDtos.LoadIssueRow> rows,
                                           Map<String, LoadIssueState> states,
                                           String academicYear,
                                           List<ManualLoadEntry> yearLoad) {
        List<TeacherDirectoryEntry> teachers = teacherDirectoryRepository.findAll();
        Map<Long, TeacherDirectoryEntry> teachersById = teachers.stream()
                .filter(teacher -> teacher.getId() != null)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, teacher -> teacher, (first, second) -> first));
        Map<String, TeacherDirectoryEntry> teachersByName = teachers.stream()
                .filter(teacher -> !normalize(teacher.getFioTeacher()).isBlank())
                .collect(Collectors.toMap(teacher -> normalize(teacher.getFioTeacher()), teacher -> teacher, (first, second) -> first));

        for (ManualLoadEntry source : yearLoad) {
            TeacherDirectoryEntry teacher = resolveTeacher(source, teachersById, teachersByName);
            LocalDate dismissalDate = dismissalDate(teacher);
            LocalDate periodEnd = originalLoadToDate(source);
            if (dismissalDate == null || periodEnd == null || !periodEnd.isAfter(dismissalDate)) {
                continue;
            }
            LocalDate handoffFrom = dismissalDate.plusDays(1);
            if (isLoadCoveredAfterDismissal(source, yearLoad, handoffFrom, periodEnd)) {
                continue;
            }

            String description = "Корпус " + display(source.getNumberSchoolBuilding())
                    + ", класс " + display(source.getClassName())
                    + ", предмет " + display(source.getSubjectName())
                    + ". " + display(source.getFioTeacher()) + " увольняется " + dismissalDate
                    + ", нагрузка не закрыта другим педагогом с " + handoffFrom + " по " + periodEnd + ".";
            String key = String.join("|", "DISMISSAL_LOAD_HANDOFF", academicYear,
                    normalize(source.getNumberSchoolBuilding()), normalize(source.getClassName()), normalize(source.getSubjectName()),
                    normalize(source.getGroupNameEducationalPlan()), String.valueOf(source.getCurriculumModuleId()),
                    source.getCurriculumPart() == null ? "" : source.getCurriculumPart().name(),
                    source.getStudyPeriod() == null ? "" : source.getStudyPeriod().name(),
                    normalize(source.getFioTeacher()), String.valueOf(dismissalDate), String.valueOf(periodEnd));
            rows.add(withState(key, source.getNumberSchoolBuilding(), "Не закрыта нагрузка после увольнения", description,
                    states.get(key), "load", source.getClassName(), source.getSubjectName()));
        }
    }

    private TeacherDirectoryEntry resolveTeacher(ManualLoadEntry row,
                                                 Map<Long, TeacherDirectoryEntry> teachersById,
                                                 Map<String, TeacherDirectoryEntry> teachersByName) {
        if (row.getTeacherId() != null && teachersById.containsKey(row.getTeacherId())) {
            return teachersById.get(row.getTeacherId());
        }
        return teachersByName.get(normalize(row.getFioTeacher()));
    }

    private LocalDate dismissalDate(TeacherDirectoryEntry teacher) {
        if (teacher == null) {
            return null;
        }
        return teacher.getDismissalDate() == null ? teacher.getPlannedDismissalDate() : teacher.getDismissalDate();
    }

    private LocalDate originalLoadToDate(ManualLoadEntry row) {
        return row.getBackupLoadToDate() == null ? row.getLoadToDate() : row.getBackupLoadToDate();
    }

    private boolean isLoadCoveredAfterDismissal(ManualLoadEntry source,
                                                List<ManualLoadEntry> yearLoad,
                                                LocalDate requiredFrom,
                                                LocalDate requiredTo) {
        List<ManualLoadEntry> handoffRows = yearLoad.stream()
                .filter(row -> row.getLoadFromDate() != null && row.getLoadToDate() != null)
                .filter(row -> sameLoadAssignment(source, row))
                .filter(row -> !sameManualLoadRow(source, row))
                .filter(row -> !sameLoadTeacher(source, row))
                .filter(row -> !isVacancyTeacher(row))
                .filter(row -> !row.getLoadToDate().isBefore(requiredFrom))
                .filter(row -> !row.getLoadFromDate().isAfter(requiredTo))
                .sorted(Comparator.comparing(ManualLoadEntry::getLoadFromDate))
                .toList();

        LocalDate cursor = requiredFrom;
        for (ManualLoadEntry row : handoffRows) {
            if (row.getLoadFromDate().isAfter(cursor)) {
                return false;
            }
            if (!row.getLoadToDate().isBefore(cursor)) {
                cursor = row.getLoadToDate().plusDays(1);
            }
            if (cursor.isAfter(requiredTo)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameLoadAssignment(ManualLoadEntry left, ManualLoadEntry right) {
        return same(left.getAcademicYear(), right.getAcademicYear())
                && sameBuilding(left, right)
                && sameClassScope(left, right)
                && Objects.equals(left.getMetaGroupId(), right.getMetaGroupId())
                && sameSubject(left.getSubjectName(), right.getSubjectName())
                && same(left.getGroupNameEducationalPlan(), right.getGroupNameEducationalPlan())
                && Objects.equals(left.getCurriculumPart(), right.getCurriculumPart())
                && Objects.equals(left.getStudyPeriod(), right.getStudyPeriod());
    }

    private boolean sameBuilding(ManualLoadEntry left, ManualLoadEntry right) {
        Long leftId = left.getSchoolBuildingId();
        Long rightId = right.getSchoolBuildingId();
        if (leftId != null && rightId != null) {
            return leftId.equals(rightId);
        }
        return same(left.getNumberSchoolBuilding(), right.getNumberSchoolBuilding());
    }

    private boolean sameClassScope(ManualLoadEntry left, ManualLoadEntry right) {
        Long leftId = left.getClassId();
        Long rightId = right.getClassId();
        if (leftId != null && rightId != null) {
            return leftId.equals(rightId);
        }
        return same(left.getClassName(), right.getClassName());
    }

    private boolean sameManualLoadRow(ManualLoadEntry left, ManualLoadEntry right) {
        return left.getId() != null && left.getId().equals(right.getId());
    }

    private boolean sameLoadTeacher(ManualLoadEntry left, ManualLoadEntry right) {
        Long leftId = left.getTeacherId();
        Long rightId = right.getTeacherId();
        if (leftId != null && rightId != null) {
            return leftId.equals(rightId);
        }
        return same(left.getFioTeacher(), right.getFioTeacher());
    }

    private boolean isVacancyTeacher(ManualLoadEntry row) {
        return same(row.getFioTeacher(), VACANCY_TEACHER);
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
                String.valueOf(row.getCurriculumModuleId()),
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
