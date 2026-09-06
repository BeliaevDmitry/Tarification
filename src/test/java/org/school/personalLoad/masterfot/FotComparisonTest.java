package org.school.personalLoad.masterfot;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class FotComparisonTest {
    static final LocalDate DATE = LocalDate.of(2026, 9, 5);
    static CurriculumPlanEntry plan(int hours) {
        CurriculumPlanEntry p = new CurriculumPlanEntry();
        p.setNumberSchoolBuilding("СП1"); p.setClassName("7-А"); p.setSubjectName("Алгебра");
        p.setCurriculumPart(CurriculumPart.CORE); p.setPlannedHours(BigDecimal.valueOf(hours));
        return p;
    }
    static ManualLoadEntry load(int hours) {
        ManualLoadEntry l = new ManualLoadEntry(); l.setId(1L); l.setTeacherId(1L);
        l.setNumberSchoolBuilding("СП1"); l.setClassName("7-А"); l.setSubjectName("Алгебра");
        l.setFioTeacher("Иванов Иван Иванович"); l.setLoad(hours); return l;
    }
    static FotDtos.SourceRow row(String teacher, String group, int hours) {
        return new FotDtos.SourceRow(7, teacher, group, "CORE", "Алгебра", BigDecimal.valueOf(hours), BigDecimal.valueOf(hours), BigDecimal.ZERO);
    }
    static FotDtos.Source source(FotDtos.SourceRow... rows) { return new FotDtos.Source("2026/2027", DATE, "Школа", List.of(rows)); }
    static FotMapping mapping(String type, String source, String target) {
        FotMapping m = new FotMapping(); m.setType(type); m.setSource(source); m.setTarget(target); return m;
    }
    private FotComparison compareWith(List<CurriculumPlanEntry> plan, List<ManualLoadEntry> load) {
        return new FotComparison(plan, load, Map.of(), List.of(), List.of(), DATE);
    }
    @Test void equalPlanAndLoadHaveNoIssues() {
        var result = compareWith(List.of(plan(3)), List.of(load(3))).compare(source(row("Иванов Иван Иванович", "7-А", 3)));
        assertThat(result.complete()).isTrue(); assertThat(result.findings()).isEmpty();
    }
    @Test void comparesPlanAndTeacherHoursSeparately() {
        var result = compareWith(List.of(plan(3)), List.of(load(3))).compare(source(row("Иванов Иван Иванович", "7-А", 2)));
        assertThat(result.findings()).extracting(FotDtos.Finding::type).containsExactly("PLAN", "LOAD");
        assertThat(result.findings()).allSatisfy(f -> { assertThat(f.expected()).isEqualTo("3 ч."); assertThat(f.actual()).isEqualTo("2 ч."); });
    }
    @Test void subgroupHoursAreNotDoubledAgain() {
        var p = plan(3); p.setSubgroupRequired(true); p.setSubgroupCount(2);
        var a = load(3); a.setGroupNameEducationalPlan("Группа 1"); a.setGroupLoad(3);
        var b = load(3); b.setId(2L); b.setGroupNameEducationalPlan("Группа 2"); b.setGroupLoad(3);
        var result = compareWith(List.of(p), List.of(a,b)).compare(source(row(a.getFioTeacher(), "Алгебра 7-А 1гр", 3), row(b.getFioTeacher(), "Алгебра 7-А 2гр", 3)));
        assertThat(result.complete()).isTrue(); assertThat(result.findings()).isEmpty();
        var wrong = compareWith(List.of(p), List.of(a,b)).compare(source(row(a.getFioTeacher(), "7-А", 6)));
        assertThat(wrong.findings()).anyMatch(f -> f.type().equals("SUBGROUP"));
    }
    @Test void subgroupOverridesAndDecimalSourceRemainExact() {
        var p = plan(3); p.setSubgroupRequired(true); p.setSubgroup1Hours(2); p.setSubgroup2Hours(4);
        var a = load(2); a.setGroupNameEducationalPlan("Группа 1");
        var b = load(4); b.setId(2L); b.setGroupNameEducationalPlan("Группа 2");
        assertThat(compareWith(List.of(p), List.of(a,b)).compare(source(row(a.getFioTeacher(), "7-А 1гр", 2), row(b.getFioTeacher(), "7-А 2гр", 4))).findings()).isEmpty();
    }
    @Test void missingMckoDoesNotReplaceAssignedTeacherWithVacancy() {
        var l = load(3);
        var missing = new MckoDtos.EligibilityRow(1L, l.getFioTeacher(), null, "Алгебра", "MISSING", "НЕТ МЦКО", null, null, null);
        var engine = new FotComparison(List.of(plan(3)), List.of(l), Map.of(1L,missing), List.of(), List.of(), DATE);
        var assigned = engine.compare(source(row(l.getFioTeacher(), "7-А", 3)));
        assertThat(assigned.findings()).extracting(FotDtos.Finding::type).containsExactly("MCKO");
        assertThat(assigned.findings().get(0).expected()).isEqualTo("3 ч.");
        assertThat(assigned.findings().get(0).actual()).isEqualTo("3 ч.");
        assertThat(assigned.findings().get(0).detail()).isEqualTo("НЕТ МЦКО");
        var vacancy = engine.compare(source(row("Вакансия математика", "7-А", 3)));
        assertThat(vacancy.findings()).extracting(FotDtos.Finding::type).contains("MCKO", "LOAD").doesNotContain("MCKO_VACANCY");
        var partial = engine.compare(source(row("Вакансия", "7-А", 2)));
        assertThat(partial.findings()).extracting(FotDtos.Finding::type).contains("MCKO", "PLAN", "LOAD");
        assertThat(l.getTeacherId()).isEqualTo(1L); assertThat(l.getLoad()).isEqualTo(3);
    }
    @Test void firstIClassKeepsSibilyovaAndReportsOnlyMissingMcko() {
        var p = plan(4); p.setClassName("1-И"); p.setSubjectName("Математика");
        var l = load(4); l.setClassName("1-И"); l.setSubjectName("Математика");
        l.setFioTeacher("Сибилева Александра Николаевна");
        var missing = new MckoDtos.EligibilityRow(1L, l.getFioTeacher(), null, "Математика", "MISSING", "НЕТ МЦКО", null, null, null);
        var sourceRow = new FotDtos.SourceRow(18, l.getFioTeacher(), "1-И", "CORE", "Математика",
                BigDecimal.valueOf(4), BigDecimal.valueOf(4), BigDecimal.ZERO);

        var result = new FotComparison(List.of(p), List.of(l), Map.of(1L, missing), List.of(), List.of(), DATE)
                .compare(source(sourceRow));

        assertThat(result.findings()).extracting(FotDtos.Finding::type).containsExactly("MCKO");
        assertThat(result.findings().get(0).subject()).isEqualTo("Математика");
        assertThat(result.findings().get(0).teacher()).isEqualTo("Сибилева Александра Николаевна");
        assertThat(result.findings().get(0).expected()).isEqualTo("4 ч.");
        assertThat(result.findings().get(0).actual()).isEqualTo("4 ч.");
        assertThat(result.findings().get(0).detail()).isEqualTo("НЕТ МЦКО");
    }
    @Test void unassignedIsNotVacancyAndPublishedWarningDoesNotBlock() {
        var l = load(3);
        var warning = new MckoDtos.EligibilityRow(1L, l.getFioTeacher(), null, "Алгебра", "WARNING", "Скоро истекает", "Высокий", DATE.minusYears(3).plusMonths(1), DATE.plusMonths(1));
        var engine = new FotComparison(List.of(plan(3)), List.of(l), Map.of(1L,warning), List.of(), List.of(), DATE);
        assertThat(engine.compare(source(row(l.getFioTeacher(), "7-А", 3))).findings()).isEmpty();
        assertThat(engine.compare(source(row("Вакансия", "7-А", 3))).findings()).extracting(FotDtos.Finding::type).containsOnly("LOAD");
        var missing = new MckoDtos.EligibilityRow(1L, l.getFioTeacher(), null, "Алгебра", "MISSING", "НЕТ МЦКО", null,null,null);
        var unassigned = new FotDtos.SourceRow(7, "Не назначен", "7-А", "CORE", "Алгебра", BigDecimal.valueOf(3), BigDecimal.ZERO, BigDecimal.valueOf(3));
        assertThat(new FotComparison(List.of(plan(3)),List.of(l),Map.of(1L,missing),List.of(),List.of(),DATE).compare(source(unassigned)).findings()).extracting(FotDtos.Finding::type).contains("MCKO").doesNotContain("MCKO_VACANCY");
    }
    @Test void combinedGroupRequiresOneExplicitMetagroupMapping() {
        var p = plan(3); p.setClassName("МГ: Алгебра 7АБ"); p.setMetaGroup(true);
        var l = load(3); l.setClassName(p.getClassName());
        String raw = "Алгебра 7-А 1гр + Алгебра 7-Б 1гр";
        var engine = compareWith(List.of(p), List.of(l));
        assertThat(engine.compare(source(row(l.getFioTeacher(),raw,3))).complete()).isFalse();
        String target = engine.options(List.of()).groups().stream().filter(c -> c.id().endsWith("~0")).findFirst().orElseThrow().id();
        var result = new FotComparison(List.of(p),List.of(l),Map.of(),List.of(mapping("GROUP",raw,target)),List.of(),DATE).compare(source(row(l.getFioTeacher(),raw,3)));
        assertThat(result.complete()).isTrue(); assertThat(result.findings()).isEmpty();
    }
    @Test void ambiguousBuildingNeverMatchesAutomatically() {
        var p2 = plan(3); p2.setNumberSchoolBuilding("СП2");
        var result = compareWith(List.of(plan(3),p2),List.of(load(3))).compare(source(row("Иванов Иван Иванович","7-А",3)));
        assertThat(result.complete()).isFalse();
        assertThat(result.findings()).anyMatch(f -> f.type().equals("MAPPING") && f.mappingType().equals("GROUP"));
    }
    @Test void confirmedExtraEntryBecomesDiscrepancyRatherThanIgnored() {
        var result = new FotComparison(List.of(plan(3)),List.of(load(3)),Map.of(), List.of(mapping("TEACHER","Петров Пётр Петрович",FotComparison.ABSENT)),List.of(),DATE)
                .compare(source(row("Петров Пётр Петрович","7-А",3)));
        assertThat(result.complete()).isTrue();
        assertThat(result.findings()).extracting(FotDtos.Finding::type).containsOnly("LOAD");
        assertThat(result.findings()).anyMatch(f -> f.teacher().contains("Петров") && f.expected().equals("0 ч."));
    }
    @Test void ignoresIupAndInactiveDatesAndPeriods() {
        var iup = load(9); iup.setLoadSource(ManualLoadSource.IUP);
        var expired = load(9); expired.setLoadToDate(DATE.minusDays(1));
        var second = load(9); second.setStudyPeriod(StudyPeriod.H2);
        var p2 = plan(9); p2.setStudyPeriod(StudyPeriod.H2);
        var result = compareWith(List.of(plan(3),p2),List.of(load(3),iup,expired,second)).compare(source(row("Иванов Иван Иванович","7-А",3)));
        assertThat(result.findings()).isEmpty();
    }
    @Test void oneSlotSplitAcrossRowsIsSummed() {
        var r = row("Иванов Иван Иванович","7-А",3);
        var result = compareWith(List.of(plan(6)),List.of(load(6))).compare(source(r,r));
        assertThat(result.complete()).isTrue(); assertThat(result.findings()).isEmpty();
    }
    @Test void unfilledSystemPlanMatchesUnassignedFotHours() {
        var unassigned = new FotDtos.SourceRow(7,"Не назначен","7-А","CORE","Алгебра",BigDecimal.valueOf(3),BigDecimal.ZERO,BigDecimal.valueOf(3));
        assertThat(compareWith(List.of(plan(3)),List.of()).compare(source(unassigned)).findings()).isEmpty();
        var partial = new FotDtos.SourceRow(7,"Иванов Иван Иванович","7-А","CORE","Алгебра",BigDecimal.valueOf(3),BigDecimal.valueOf(2),BigDecimal.ONE);
        assertThat(compareWith(List.of(plan(3)),List.of(load(2))).compare(source(partial)).findings()).isEmpty();
    }
}
