package org.school.personalLoad.masterfot;

import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/** Pure comparison. The system's curriculum and assignments are never modified. */
public final class FotComparison {
    private static final Pattern CLASS = Pattern.compile("(?<![\\p{L}\\d])(1[01]|[1-9])\\s*[-–—«\\\"]?\\s*([А-ЯЁA-Z])(?=$|[\\s»\\\"+])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern GROUP = Pattern.compile("(?:(\\d+)\\s*(?:гр|подгр)|(?:группа|подгруппа)\\s*(\\d+))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String VACANCY = "vacancy";
    public static final String ABSENT = "__ABSENT__";
    record Scope(String id, String building, String name) {}
    record Slot(String scope, String subject, String part, int group) {}
    record Assignment(Slot slot, String teacher) {}
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final Map<String, String> subjects = new LinkedHashMap<>(), teachers = new LinkedHashMap<>();
    private final Map<Slot, BigDecimal> plan = new LinkedHashMap<>();
    private final Map<Assignment, BigDecimal> load = new LinkedHashMap<>();
    private final Map<Assignment, String> blocked = new LinkedHashMap<>();
    private final Map<String, String> mappings = new HashMap<>();

    public FotComparison(List<CurriculumPlanEntry> curriculum, List<ManualLoadEntry> assignments,
                         Map<Long, MckoDtos.EligibilityRow> eligibility, List<FotMapping> mappings,
                         List<StudyPeriodSetting> periods, LocalDate date) {
        mappings.forEach(m -> this.mappings.put(mappingKey(m.getType(), m.getSource()), m.getTarget()));
        Map<Long, String> moduleSubjects = new HashMap<>();
        for (CurriculumPlanEntry p : curriculum) {
            if (p.isDeprecated() || !periodActive(p, periods, date)) continue;
            String scope = scope(p.getNumberSchoolBuilding(), p.getClassName());
            if (p.isExcludedFromManualLoad() && !p.isMetaGroup() && p.getMetaGroupId() == null) continue;
            if (p.isModularSystem() && !p.getModules().isEmpty()) {
                for (CurriculumModule m : p.getModules()) {
                    String subject = subject(null, p.getSubjectName() + " — " + m.getModuleName());
                    moduleSubjects.put(m.getId(), subject);
                    addPlan(scope, subject, part(p.getCurriculumPart()), m.getPlannedHours(), m.isSubgroupRequired(),
                            m.getSubgroupCount(), m.getSubgroup1Hours(), m.getSubgroup2Hours());
                }
            } else {
                addPlan(scope, subject(p.getSubjectId(), p.getSubjectName()), part(p.getCurriculumPart()),
                        p.getPlannedHours(), p.isSubgroupRequired(), p.getSubgroupCount(), p.getSubgroup1Hours(), p.getSubgroup2Hours());
            }
        }
        teachers.put(VACANCY, "Вакансия");
        for (ManualLoadEntry row : assignments) {
            if (row.isIupLoad() || !activeLoad(row, date)) continue;
            String scope = scope(row.getNumberSchoolBuilding(), row.getClassName());
            String subject = moduleSubjects.get(row.getCurriculumModuleId());
            if (subject == null) subject = subject(row.getSubjectId(), row.getSubjectName());
            String teacher = vacancy(row.getFioTeacher()) ? VACANCY : row.getTeacherId() == null ? "name:" + norm(row.getFioTeacher()) : "id:" + row.getTeacherId();
            teachers.putIfAbsent(teacher, row.getFioTeacher());
            Slot slot = new Slot(scope, subject, part(row.getCurriculumPart()), groupNumber(row.getGroupNameEducationalPlan()));
            Assignment key = new Assignment(slot, teacher);
            load.merge(key, row.getEffectiveLoadHours(), BigDecimal::add);
            MckoDtos.EligibilityRow mcko = eligibility.get(row.getId());
            if (mcko != null && "MISSING".equals(mcko.status()) && !VACANCY.equals(teacher)) {
                blocked.put(key, mcko.message() + ". На " + date + " нет действующего МЦКО по этому предмету");
            }
        }
    }

    public FotDtos.Options options(List<FotMapping> saved) {
        List<FotDtos.Choice> groupChoices = new ArrayList<>();
        for (Scope s : scopes.values()) {
            int max = Math.max(2, plan.keySet().stream().filter(k -> k.scope.equals(s.id)).mapToInt(Slot::group).max().orElse(0));
            for (int g = 0; g <= max; g++) groupChoices.add(new FotDtos.Choice(s.id + "~" + g, s.building + " · " + s.name + " · " + groupLabel(g)));
        }
        groupChoices.add(absentChoice());
        return new FotDtos.Options(groupChoices, withAbsent(choices(subjects)), withAbsent(choices(teachers)), saved.stream()
                .map(m -> new FotDtos.MappingRequest(m.getType(), m.getSource(), m.getTarget())).toList());
    }

    public FotDtos.Comparison compare(FotDtos.Source source) {
        Map<Slot, BigDecimal> actualPlan = new LinkedHashMap<>();
        Map<Assignment, BigDecimal> actualLoad = new LinkedHashMap<>();
        Map<Slot, List<Integer>> sourceRows = new HashMap<>();
        Map<String, FotDtos.Finding> findings = new LinkedHashMap<>();
        boolean complete = true;
        for (FotDtos.SourceRow row : source.rows()) {
            String subject = resolveSubject(row.subject());
            String group = resolveGroup(row.group());
            String teacher = resolveTeacher(row.teacher());
            if (subject == null) { addMapping(findings, "SUBJECT", row.subject(), row); complete = false; }
            if (group == null) { addMapping(findings, "GROUP", row.group(), row); complete = false; }
            if (teacher == null && row.assigned().signum() > 0) { addMapping(findings, "TEACHER", row.teacher(), row); complete = false; }
            if (subject == null || group == null) continue;
            int split = group.lastIndexOf('~');
            Slot slot = new Slot(group.substring(0, split), subject, row.part(), Integer.parseInt(group.substring(split + 1)));
            // One slot can legitimately be split between several teachers.
            // Its plan and assignment hours are therefore additive.
            actualPlan.merge(slot, row.total(), BigDecimal::add);
            sourceRows.computeIfAbsent(slot, k -> new ArrayList<>()).add(row.row());
            if (teacher != null && row.assigned().signum() > 0) actualLoad.merge(new Assignment(slot, teacher), row.assigned(), BigDecimal::add);
            if (row.unassigned().signum() > 0) actualLoad.merge(new Assignment(slot, "unassigned"), row.unassigned(), BigDecimal::add);
        }
        Set<Slot> slots = new LinkedHashSet<>(plan.keySet()); slots.addAll(actualPlan.keySet());
        for (Slot slot : slots) {
            BigDecimal expected = plan.getOrDefault(slot, BigDecimal.ZERO), actual = actualPlan.getOrDefault(slot, BigDecimal.ZERO);
            if (expected.compareTo(actual) != 0) add(findings, "PLAN", slot, "", hours(expected), hours(actual),
                    groupLabel(slot.group) + ". Часы учебного плана. " + location(sourceRows, slot));
        }
        Map<Slot, Set<Integer>> expectedGroups = groups(plan), actualGroups = groups(actualPlan);
        Set<Slot> bases = new LinkedHashSet<>(expectedGroups.keySet()); bases.addAll(actualGroups.keySet());
        for (Slot base : bases) {
            Set<Integer> e = expectedGroups.getOrDefault(base, Set.of()), a = actualGroups.getOrDefault(base, Set.of());
            if (!e.equals(a)) add(findings, "SUBGROUP", base, "", labels(e), labels(a), "Сверяется деление по учебным группам, а не коэффициент оплаты.");
        }
        Map<Assignment, BigDecimal> expectedLoad = new LinkedHashMap<>();
        load.forEach((key, value) -> expectedLoad.merge(blocked.containsKey(key) ? new Assignment(key.slot, VACANCY) : key, value, BigDecimal::add));
        // Plan hours without a system assignment must also remain unassigned in FOT.
        // They are not the named vacancy required for an MCKO exception.
        Map<Slot, BigDecimal> assignedBySlot = new HashMap<>();
        expectedLoad.forEach((key, value) -> assignedBySlot.merge(key.slot, value, BigDecimal::add));
        plan.forEach((slot, hours) -> {
            BigDecimal remaining = hours.subtract(assignedBySlot.getOrDefault(slot, BigDecimal.ZERO));
            if (remaining.signum() > 0) expectedLoad.merge(new Assignment(slot, "unassigned"), remaining, BigDecimal::add);
        });
        Set<Assignment> keys = new LinkedHashSet<>(expectedLoad.keySet()); keys.addAll(actualLoad.keySet());
        for (Assignment key : keys) {
            BigDecimal e = expectedLoad.getOrDefault(key, BigDecimal.ZERO), a = actualLoad.getOrDefault(key, BigDecimal.ZERO);
            if (e.compareTo(a) != 0) add(findings, "LOAD", key.slot, teacherLabel(key.teacher), hours(e), hours(a),
                    groupLabel(key.slot.group) + ". " + (VACANCY.equals(key.teacher) ? "Ожидаемая вакансия с учётом МЦКО. " : "Нагрузка педагога. ") + location(sourceRows, key.slot));
        }
        blocked.forEach((key, reason) -> {
            Assignment vacancy = new Assignment(key.slot, VACANCY);
            BigDecimal expected = expectedLoad.getOrDefault(vacancy, BigDecimal.ZERO), actual = actualLoad.getOrDefault(vacancy, BigDecimal.ZERO);
            BigDecimal assignedToTeacher = actualLoad.getOrDefault(key, BigDecimal.ZERO);
            boolean covered = actual.compareTo(expected) == 0 && assignedToTeacher.signum() == 0;
            add(findings, covered ? "MCKO_VACANCY" : "MCKO", key.slot, teacherLabel(key.teacher),
                    "На вакансии: " + hours(load.get(key)), covered ? "Вакансия учтена" : "Вакансия по группе: " + hours(actual) + "; на педагоге: " + hours(assignedToTeacher),
                    reason + ". Педагог в системе ведёт через замены; назначение в системе сохраняется. " + groupLabel(key.slot.group));
        });
        return new FotDtos.Comparison(new ArrayList<>(findings.values()), complete);
    }

    private String scope(String building, String name) {
        String id = hash(norm(building) + "|" + norm(name));
        scopes.putIfAbsent(id, new Scope(id, Objects.toString(building, ""), Objects.toString(name, "")));
        return id;
    }
    private String subject(Long id, String name) {
        String key = id == null ? "name:" + norm(name) : "id:" + id;
        subjects.putIfAbsent(key, Objects.toString(name, "")); return key;
    }
    private void addPlan(String scope, String subject, String part, BigDecimal hours, boolean split, Integer count, Integer h1, Integer h2) {
        int groups = split ? Math.max(2, count == null ? 2 : count) : 0;
        if (groups == 0) plan.merge(new Slot(scope, subject, part, 0), nonNull(hours), BigDecimal::add);
        else for (int i = 1; i <= groups; i++) {
            Integer override = i == 1 ? h1 : i == 2 ? h2 : null;
            plan.merge(new Slot(scope, subject, part, i), override == null ? nonNull(hours) : BigDecimal.valueOf(override), BigDecimal::add);
        }
    }
    private String resolveSubject(String raw) { return resolve("SUBJECT", raw, subjects); }
    private String resolveTeacher(String raw) {
        if (norm(raw).equals("не назначен") || norm(raw).equals("не назначено")) return "unassigned";
        if (vacancy(raw)) return VACANCY;
        return resolve("TEACHER", raw, teachers);
    }
    private String resolve(String type, String raw, Map<String, String> candidates) {
        String mapped = mappings.get(mappingKey(type, raw));
        if (ABSENT.equals(mapped)) {
            String key = "external:" + norm(raw);
            candidates.putIfAbsent(key, raw);
            return key;
        }
        if (mapped != null) return candidates.containsKey(mapped) ? mapped : null;
        List<String> match = candidates.entrySet().stream().filter(e -> norm(e.getValue()).equals(norm(raw))).map(Map.Entry::getKey).toList();
        return match.size() == 1 ? match.get(0) : null;
    }
    private String resolveGroup(String raw) {
        String mapped = mappings.get(mappingKey("GROUP", raw));
        if (ABSENT.equals(mapped)) return scope("Нет в системе", raw) + "~0";
        if (mapped != null) {
            int split = mapped.lastIndexOf('~');
            return split > 0 && scopes.containsKey(mapped.substring(0, split)) ? mapped : null;
        }
        List<Scope> exact = scopes.values().stream().filter(s -> norm(s.name).replaceFirst("^мг:\\s*", "").equals(norm(raw))).toList();
        if (exact.size() == 1) return exact.get(0).id + "~0";
        // Combined groups require explicit mapping to a metagroup even if they mention one class twice.
        if (raw.contains("+")) return null;
        Matcher matcher = CLASS.matcher(raw);
        if (!matcher.find()) return null;
        String className = classKey(matcher.group(1) + matcher.group(2));
        if (matcher.find()) return null;
        List<Scope> candidates = scopes.values().stream().filter(s -> classKey(s.name).equals(className)).toList();
        return candidates.size() == 1 ? candidates.get(0).id + "~" + groupNumber(raw) : null;
    }
    private void addMapping(Map<String, FotDtos.Finding> out, String type, String raw, FotDtos.SourceRow row) {
        String key = hash("MAPPING|" + type + "|" + norm(raw));
        out.putIfAbsent(key, new FotDtos.Finding(key, "MAPPING", "", row.group(), row.subject(), row.teacher(),
                "Выберите соответствие в системе", raw, "Строка " + row.row() + ". " + typeLabel(type), type, raw));
    }
    private void add(Map<String, FotDtos.Finding> out, String type, Slot slot, String teacher, String expected, String actual, String detail) {
        Scope scope = scopes.get(slot.scope);
        String key = hash(type + "|" + slot + "|" + norm(teacher));
        out.put(key, new FotDtos.Finding(key, type, scope.building, scope.name, subjects.get(slot.subject), teacher,
                expected, actual, partLabel(slot.part) + ". " + detail, "", ""));
    }
    private Map<Slot, Set<Integer>> groups(Map<Slot, BigDecimal> entries) {
        Map<Slot, Set<Integer>> result = new LinkedHashMap<>();
        entries.forEach((key, value) -> { if (value.signum() > 0) result.computeIfAbsent(new Slot(key.scope, key.subject, key.part, -1), k -> new TreeSet<>()).add(key.group); });
        return result;
    }
    private static boolean periodActive(CurriculumPlanEntry row, List<StudyPeriodSetting> periods, LocalDate date) {
        StudyPeriodSetting explicit = row.getStudyPeriodSettingId() == null ? null : periods.stream().filter(p -> Objects.equals(p.getId(), row.getStudyPeriodSettingId())).findFirst().orElse(null);
        if (explicit != null) return between(date, explicit.getStartDate(), explicit.getEndDate());
        if (row.getStudyPeriod() == null || row.getStudyPeriod() == StudyPeriod.YEAR) return true;
        int grade = Optional.ofNullable(org.school.personalLoad.util.CurriculumLoadStandard.parallelOf(row.getClassName())).orElse(0);
        List<StudyPeriodSetting> matches = periods.stream().filter(p -> p.getStudyPeriod() == row.getStudyPeriod())
                .filter(p -> grade >= p.getParallelFrom() && grade <= p.getParallelTo()).toList();
        if (!matches.isEmpty()) return matches.stream().anyMatch(p -> between(date, p.getStartDate(), p.getEndDate()));
        return row.getStudyPeriod() == StudyPeriod.H1 ? date.getMonthValue() >= 9 : date.getMonthValue() < 9;
    }
    public static boolean activeLoad(ManualLoadEntry row, LocalDate date) {
        if (!between(date, row.getLoadFromDate(), row.getLoadToDate())) return false;
        if (row.getLoadFromDate() != null || row.getLoadToDate() != null) return true;
        return row.getStudyPeriod() == null || row.getStudyPeriod() == StudyPeriod.YEAR
                || (row.getStudyPeriod() == StudyPeriod.H1 ? date.getMonthValue() >= 9 : date.getMonthValue() < 9);
    }
    private static boolean between(LocalDate date, LocalDate from, LocalDate to) { return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to)); }
    public static String mappingKey(String type, String source) { return type + "|" + norm(source); }
    public static String norm(String value) { return Objects.toString(value, "").replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " "); }
    private static String classKey(String value) { return norm(value).replaceAll("[-–—\\s«»\\\"]", ""); }
    public static int groupNumber(String value) { Matcher m = GROUP.matcher(Objects.toString(value, "")); return m.find() ? Integer.parseInt(m.group(1) == null ? m.group(2) : m.group(1)) : 0; }
    private static boolean vacancy(String value) { return norm(value).startsWith("вакансия"); }
    private static String part(CurriculumPart value) { return value == null ? "CORE" : value.name(); }
    private static String partLabel(String value) { return switch(value) { case "FORMABLE" -> "Формируемая часть"; case "EXTRACURRICULAR" -> "Внеурочная деятельность"; case "CORRECTIONAL" -> "Коррекционная часть"; default -> "Обязательная часть"; }; }
    private static String groupLabel(int n) { return n <= 0 ? "Весь класс / метагруппа" : "Подгруппа " + n; }
    private static String labels(Set<Integer> values) { return values.isEmpty() ? "Отсутствует" : values.stream().map(FotComparison::groupLabel).collect(Collectors.joining(", ")); }
    private static String hours(BigDecimal value) { return value.stripTrailingZeros().toPlainString() + " ч."; }
    private static BigDecimal nonNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String teacherLabel(String key) { return "unassigned".equals(key) ? "Не назначен" : teachers.getOrDefault(key, key); }
    private static String location(Map<Slot, List<Integer>> rows, Slot slot) { return rows.containsKey(slot) ? "Строки Excel: " + rows.get(slot) : "В выгрузке нет этой строки."; }
    private static String typeLabel(String type) { return switch(type) { case "GROUP" -> "Класс / метагруппа и подгруппа"; case "SUBJECT" -> "Предмет / модуль"; default -> "Педагог"; }; }
    private static List<FotDtos.Choice> choices(Map<String, String> values) { return values.entrySet().stream().map(e -> new FotDtos.Choice(e.getKey(), e.getValue())).sorted(Comparator.comparing(FotDtos.Choice::label)).toList(); }
    private static FotDtos.Choice absentChoice() { return new FotDtos.Choice(ABSENT, "Отсутствует в системе — лишняя запись Мастер ФОТ"); }
    private static List<FotDtos.Choice> withAbsent(List<FotDtos.Choice> choices) {
        List<FotDtos.Choice> result = new ArrayList<>(choices); result.add(absentChoice()); return result;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
