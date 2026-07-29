package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.LoadInRateDtos.RuleBandRequest;
import org.school.personalLoad.dto.LoadInRateDtos.RuleRequest;
import org.school.personalLoad.model.LoadInRateRule;
import org.school.personalLoad.model.LoadInRateRuleBand;
import org.school.personalLoad.repository.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

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
    void suggestionUsesActualLoadWhenBandMaximumIsHigher() {
        LoadInRateRuleBand band = new LoadInRateRuleBand();
        band.setMinTotalHours(new BigDecimal("1"));
        band.setMaxTotalHours(new BigDecimal("4"));
        band.setSuggestedIncludedHours(new BigDecimal("4"));
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(7L)).thenReturn(List.of(band));

        assertEquals(0, service.suggestedIncludedHours(7L, new BigDecimal("3")).compareTo(new BigDecimal("3")));
        assertEquals(0, service.suggestedIncludedHours(7L, new BigDecimal("4")).compareTo(new BigDecimal("4")));
        assertEquals(BigDecimal.ZERO, service.suggestedIncludedHours(7L, new BigDecimal("5")));
    }

    @Test
    void obzrOpenEndedBandKeepsOnlyNineHoursInsideSalary() {
        LoadInRateRuleBand halfRate = new LoadInRateRuleBand();
        halfRate.setMinTotalHours(new BigDecimal("1"));
        halfRate.setMaxTotalHours(new BigDecimal("4"));
        halfRate.setSuggestedIncludedHours(new BigDecimal("4"));
        halfRate.setRateFraction(new BigDecimal("0.5"));
        LoadInRateRuleBand fullRate = new LoadInRateRuleBand();
        fullRate.setMinTotalHours(new BigDecimal("5"));
        fullRate.setMaxTotalHours(null);
        fullRate.setSuggestedIncludedHours(new BigDecimal("9"));
        fullRate.setRateFraction(BigDecimal.ONE);
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(7L))
                .thenReturn(List.of(halfRate, fullRate));

        assertEquals(0, service.suggestedIncludedHours(7L, new BigDecimal("3"))
                .compareTo(new BigDecimal("3")));
        assertEquals(0, service.suggestedIncludedHours(7L, new BigDecimal("7"))
                .compareTo(new BigDecimal("7")));
        assertEquals(0, service.suggestedIncludedHours(7L, new BigDecimal("12"))
                .compareTo(new BigDecimal("9")));
    }

    @Test
    void ruleIsSavedForPositionWithItsBands() {
        when(rules.existsByNameIgnoreCase("Преподаватель ОБЗР")).thenReturn(false);
        when(rules.save(any())).thenAnswer(invocation -> {
            LoadInRateRule rule = invocation.getArgument(0);
            rule.setId(11L);
            return rule;
        });
        when(bands.findAllByRuleIdOrderByMinTotalHoursAsc(11L)).thenAnswer(invocation -> {
            LoadInRateRuleBand band = new LoadInRateRuleBand();
            band.setRuleId(11L);
            band.setMinTotalHours(BigDecimal.ONE);
            band.setMaxTotalHours(new BigDecimal("4"));
            band.setSuggestedIncludedHours(new BigDecimal("4"));
            band.setRateFraction(new BigDecimal("0.5"));
            return List.of(band);
        });

        var saved = service.saveRule(null, new RuleRequest(
                "Преподаватель ОБЗР",
                "внутри ставки преподавателя ОБЗР",
                true,
                List.of(new RuleBandRequest(BigDecimal.ONE, new BigDecimal("4"),
                        new BigDecimal("4"), new BigDecimal("0.5")))
        ));

        assertEquals("Преподаватель ОБЗР", saved.name());
        assertEquals(1, saved.bands().size());
        verify(bands).save(argThat(band -> band.getRuleId().equals(11L)
                && band.getSuggestedIncludedHours().compareTo(new BigDecimal("4")) == 0));
    }

    @Test
    void overlappingBandsAreRejectedBeforeSaving() {
        RuleRequest request = new RuleRequest("Психолог", "внутри ставки психолога", true, List.of(
                new RuleBandRequest(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ONE),
                new RuleBandRequest(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("10"), BigDecimal.ONE)
        ));

        assertThrows(ResponseStatusException.class, () -> service.saveRule(null, request));
        verifyNoInteractions(rules);
    }
}
