package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.LoadInRateDtos.RuleBandRequest;
import org.school.personalLoad.dto.LoadInRateDtos.RuleRequest;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoadInRateServiceRuleTest {
    private final LoadInRateRuleRepository rules = mock(LoadInRateRuleRepository.class);
    private final LoadInRateRuleBandRepository bands = mock(LoadInRateRuleBandRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final ManualLoadEntryRepository loads = mock(ManualLoadEntryRepository.class);
    private final TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
    private final LoadSalaryCalculationService salary = mock(LoadSalaryCalculationService.class);
    private final HrDocumentService documents = mock(HrDocumentService.class);
    private final LoadInRateService service = new LoadInRateService(
            rules, bands, contracts, loads, teachers, salary, documents);

    @Test
    void capacityUsesActualLoadOnlyWhenConfiguredRangeMatches() {
        LoadInRateRuleBand band = band("1", "4", "0.5");
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(7L)).thenReturn(List.of(band));

        assertDecimal("3", service.suggestedIncludedHours(7L, new BigDecimal("3")));
        assertDecimal("4", service.suggestedIncludedHours(7L, new BigDecimal("4")));
        assertDecimal("0", service.suggestedIncludedHours(7L, new BigDecimal("5")));
    }

    @Test
    void eachPositionRangeDefinesCapacityAndRateFraction() {
        LoadInRateRuleBand halfRate = band("1", "4", "0.5");
        LoadInRateRuleBand fullRate = band("5", "9", "1");
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(7L))
                .thenReturn(List.of(halfRate, fullRate));

        assertDecimal("3", service.suggestedIncludedHours(7L, new BigDecimal("3")));
        assertDecimal("7", service.suggestedIncludedHours(7L, new BigDecimal("7")));
        assertDecimal("0", service.suggestedIncludedHours(7L, new BigDecimal("12")));
    }

    @Test
    void overviewAutomaticallySelectsEmployeesByPositionAndShowsRemainingCapacity() {
        LoadInRateRule rule = rule(7L, "Преподаватель ОБЗР");
        LoadInRateRuleBand fullRate = band("5", "9", "1");
        when(rules.findAll()).thenReturn(List.of(rule));
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(7L)).thenReturn(List.of(fullRate));

        EmploymentContract contract = new EmploymentContract();
        contract.setId(21L);
        contract.setTeacherId(31L);
        contract.setContractNumber("15");
        contract.setPositionName("Преподаватель ОБЗР");
        contract.setActive(true);
        contract.setPrimaryContract(true);
        contract.setLoadHoursMayBeIncludedInRate(false);
        when(contracts.findAllByActiveTrueOrderByTeacherIdAsc()).thenReturn(List.of(contract));

        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(31L);
        teacher.setFioTeacher("Иванов Иван Иванович");
        when(teachers.findAll()).thenReturn(List.of(teacher));

        ManualLoadEntry first = load(101L, 31L, "Математика", "5-А", 3, true);
        first.setIncludedInRateHours(BigDecimal.ONE);
        ManualLoadEntry second = load(102L, 31L, "ОБЗР", "6-А", 2, false);
        when(loads.findAllByAcademicYear("2026/2027")).thenReturn(List.of(first, second));
        when(salary.totalHours(first)).thenReturn(new BigDecimal("3"));
        when(salary.totalHours(second)).thenReturn(new BigDecimal("2"));
        when(salary.calculate(eq("2026/2027"), anyCollection())).thenReturn(Map.of(
                101L, salaryLine(first, "3", "1", "2"),
                102L, salaryLine(second, "2", "0", "2")
        ));

        var overview = service.overview("2026/2027");

        assertEquals(2, overview.rows().size());
        assertEquals(1, overview.teachers().size());
        var summary = overview.teachers().get(0);
        assertEquals("Преподаватель ОБЗР", summary.positionName());
        assertDecimal("5", summary.totalHoursH1());
        assertDecimal("5", summary.capacityHoursH1());
        assertDecimal("4", summary.remainingCapacityHoursH1());
        assertDecimal("1", summary.rateFractionH1());
        assertEquals(1, summary.unresolvedRows());
        assertFalse(summary.complete());
    }

    @Test
    void ruleIsSavedForPositionWithItsBands() {
        when(rules.existsByNameIgnoreCase("Преподаватель ОБЗР")).thenReturn(false);
        when(rules.save(any())).thenAnswer(invocation -> {
            LoadInRateRule rule = invocation.getArgument(0);
            rule.setId(11L);
            return rule;
        });
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(11L))
                .thenReturn(List.of(band("1", "4", "0.5")));

        var saved = service.saveRule(null, new RuleRequest(
                "Преподаватель ОБЗР", null, true,
                List.of(new RuleBandRequest(
                        BigDecimal.ONE, new BigDecimal("4"), null, new BigDecimal("0.5")))
        ));

        assertEquals("Преподаватель ОБЗР", saved.name());
        assertEquals("Преподаватель ОБЗР", saved.documentLabel());
        assertEquals(1, saved.bands().size());
        verify(bands).save(argThat(savedBand -> savedBand.getRuleId().equals(11L)
                && savedBand.getSuggestedIncludedHours().compareTo(new BigDecimal("4")) == 0
                && savedBand.getRateFraction().compareTo(new BigDecimal("0.5")) == 0));
    }

    @Test
    void overlappingBandsAreRejectedBeforeSaving() {
        RuleRequest request = new RuleRequest("Психолог", null, true, List.of(
                new RuleBandRequest(BigDecimal.ZERO, new BigDecimal("10"), null, BigDecimal.ONE),
                new RuleBandRequest(new BigDecimal("10"), new BigDecimal("20"), null, BigDecimal.ONE)
        ));

        assertThrows(ResponseStatusException.class, () -> service.saveRule(null, request));
        verifyNoInteractions(rules);
    }

    @Test
    void rateFractionIsRequiredAndMustBePositive() {
        RuleRequest request = new RuleRequest("Психолог", null, true, List.of(
                new RuleBandRequest(BigDecimal.ONE, new BigDecimal("10"), null, BigDecimal.ZERO)
        ));

        assertThrows(ResponseStatusException.class, () -> service.saveRule(null, request));
        verifyNoInteractions(rules);
    }

    private LoadInRateRule rule(Long id, String name) {
        LoadInRateRule rule = new LoadInRateRule();
        rule.setId(id);
        rule.setName(name);
        rule.setDocumentLabel(name);
        rule.setActive(true);
        return rule;
    }

    private LoadInRateRuleBand band(String min, String max, String fraction) {
        LoadInRateRuleBand band = new LoadInRateRuleBand();
        band.setRuleId(7L);
        band.setMinTotalHours(new BigDecimal(min));
        band.setMaxTotalHours(max == null ? null : new BigDecimal(max));
        band.setSuggestedIncludedHours(max == null ? BigDecimal.ZERO : new BigDecimal(max));
        band.setRateFraction(new BigDecimal(fraction));
        return band;
    }

    private ManualLoadEntry load(Long id, Long teacherId, String subject, String className,
                                 int hours, boolean confirmed) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setId(id);
        row.setAcademicYear("2026/2027");
        row.setTeacherId(teacherId);
        row.setFioTeacher("Иванов Иван Иванович");
        row.setNumberSchoolBuilding("1");
        row.setSubjectName(subject);
        row.setClassName(className);
        row.setLoad(hours);
        row.setStudyPeriod(StudyPeriod.YEAR);
        row.setInRateAllocationConfirmed(confirmed);
        return row;
    }

    private LoadSalaryCalculationService.SalaryLine salaryLine(
            ManualLoadEntry row, String total, String included, String paid) {
        return new LoadSalaryCalculationService.SalaryLine(
                row.getId(), row.getTeacherId(), row.getEmploymentContractId(),
                new BigDecimal(total), new BigDecimal(included), new BigDecimal(paid),
                25, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                row.isInRateAllocationConfirmed(), row.getInRateReason());
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
