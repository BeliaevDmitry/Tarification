package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.repository.SalaryGroupCoefficientSubjectRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadSalaryCalculationServiceTest {
    private final ClassSizeService classSizeService = mock(ClassSizeService.class);
    private final SalarySettingsRepository salarySettingsRepository = mock(SalarySettingsRepository.class);
    private final SubjectLevelCoefficientRepository coefficientRepository = mock(SubjectLevelCoefficientRepository.class);
    private final SalaryGroupCoefficientSubjectRepository groupSubjectRepository = mock(SalaryGroupCoefficientSubjectRepository.class);
    private LoadSalaryCalculationService service;

    @BeforeEach
    void setUp() {
        service = new LoadSalaryCalculationService(classSizeService, salarySettingsRepository,
                coefficientRepository, groupSubjectRepository);
        when(classSizeService.effectiveClassSizes("2026/2027")).thenReturn(Map.of("7-А", 20));
        SalarySettings settings = new SalarySettings();
        settings.setStudentHourRate(new BigDecimal("37"));
        when(salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)).thenReturn(Optional.of(settings));
        when(coefficientRepository.findAll()).thenReturn(List.of());
        when(groupSubjectRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void includedHoursRemainInLoadButAreExcludedFromStudentHourSalary() {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setId(1L);
        row.setTeacherId(10L);
        row.setAcademicYear("2026/2027");
        row.setSubjectName("ОБЗР");
        row.setClassName("7-А");
        row.setLoad(10);
        row.setIncludedInRateHours(new BigDecimal("4"));
        row.setInRateAllocationConfirmed(true);
        row.setInRateReason("внутри ставки преподавателя ОБЗР");

        LoadSalaryCalculationService.SalaryLine line = service.calculate("2026/2027", row);

        assertEquals(new BigDecimal("10"), line.totalHours());
        assertEquals(new BigDecimal("4"), line.includedHours());
        assertEquals(new BigDecimal("6"), line.paidHours());
        BigDecimal expected = new BigDecimal("37")
                .multiply(new BigDecimal("20"))
                .multiply(new BigDecimal("6"))
                .multiply(BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP));
        assertEquals(0, expected.compareTo(line.amount()));
    }
}
