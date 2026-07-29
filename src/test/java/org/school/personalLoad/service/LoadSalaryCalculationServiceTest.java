package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ManualLoadSource;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.school.personalLoad.model.EducationStage;
import org.school.personalLoad.repository.SalaryGroupCoefficientSubjectRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void completedContingentDistributionOverridesLegacyHalfClassCount() {
        StudentDataExchangeService exchangeService = mock(StudentDataExchangeService.class);
        ReflectionTestUtils.setField(service, "studentDataExchangeService", exchangeService);
        ManualLoadEntry row = new ManualLoadEntry();
        row.setId(2L);
        row.setAcademicYear("2026/2027");
        row.setSubjectName("Иностранный язык");
        row.setClassName("7-А");
        row.setGroupNameEducationalPlan("Группа 1");
        row.setLoad(2);
        when(exchangeService.resolveStudentCounts("2026/2027", List.of(row)))
                .thenReturn(new StudentDataExchangeService.StudentCountResolution(
                        true,
                        "Фактический контингент применяется автоматически",
                        Map.of(2L, 7)
                ));

        LoadSalaryCalculationService.SalaryLine line = service.calculate("2026/2027", row);

        assertEquals(7, line.children());
        BigDecimal expected = new BigDecimal("37")
                .multiply(new BigDecimal("7"))
                .multiply(new BigDecimal("2"))
                .multiply(BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP));
        assertEquals(0, expected.compareTo(line.amount()));
    }

    @Test
    void iupUsesPreciseHoursAndSeparatePreliminaryFormula() {
        SubjectLevelCoefficientEntry coefficient = new SubjectLevelCoefficientEntry();
        coefficient.setSubjectName("Математика");
        coefficient.setEducationStage(EducationStage.OOO);
        coefficient.setCoefficient(new BigDecimal("1.2"));
        when(coefficientRepository.findAll()).thenReturn(List.of(coefficient));

        ManualLoadEntry row = new ManualLoadEntry();
        row.setId(3L);
        row.setTeacherId(10L);
        row.setAcademicYear("2026/2027");
        row.setSubjectName("Математика");
        row.setClassName("ИУП-5-А-Иванов И.И.");
        row.setLoadSource(ManualLoadSource.IUP);
        row.setLoad(2);
        row.setPreciseLoadHours(new BigDecimal("1.5"));
        row.setIupStudentCategory(StudentCategory.K2);

        LoadSalaryCalculationService.SalaryLine line = service.calculate("2026/2027", row);

        assertEquals(new BigDecimal("1.5"), line.totalHours());
        assertEquals(0, new BigDecimal("12.5").compareTo(line.groupCoefficient()));
        BigDecimal expected = new BigDecimal("1.5")
                .multiply(new BigDecimal("1.2"))
                .multiply(BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP))
                .multiply(new BigDecimal("12.5"));
        assertEquals(0, expected.compareTo(line.amount()));
    }
}
