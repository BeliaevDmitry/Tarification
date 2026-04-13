package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.AcademicYearRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicYearServiceImplContinuityTest {

    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    private AcademicYearServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicYearServiceImpl(
                academicYearRepository,
                manualLoadEntryRepository,
                curriculumPlanEntryRepository
        );
    }

    @Test
    void markContinuityAppliedCopiesAssignmentsFromPreviousYear() {
        AcademicYearConfig targetYear = new AcademicYearConfig();
        targetYear.setCode("2026/2027");

        CurriculumPlanEntry curriculum = new CurriculumPlanEntry();
        curriculum.setAcademicYear("2026/2027");
        curriculum.setNumberSchoolBuilding("B1");
        curriculum.setClassName("8-А");
        curriculum.setSubjectName("Алгебра");
        curriculum.setEducationLevel(EducationLevel.BASIC);
        curriculum.setStudyPeriod(StudyPeriod.YEAR);
        curriculum.setPlannedHours(BigDecimal.valueOf(6));
        curriculum.setDeprecated(false);

        ManualLoadEntry source = new ManualLoadEntry();
        source.setId(10L);
        source.setAcademicYear("2025/2026");
        source.setFioTeacher("Иванов И.И.");
        source.setNumberSchoolBuilding("B1");
        source.setClassName("8-А");
        source.setSubjectName("Алгебра");
        source.setEducationLevel(EducationLevel.BASIC);
        source.setStudyPeriod(StudyPeriod.YEAR);
        source.setLoad(6);

        when(academicYearRepository.findByCode("2026/2027")).thenReturn(Optional.of(targetYear));
        when(academicYearRepository.existsByCode("2025/2026")).thenReturn(true);
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of(curriculum));
        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(source));
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(academicYearRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markContinuityApplied("2026/2027");

        verify(manualLoadEntryRepository).saveAll(any());
        verify(academicYearRepository).save(targetYear);
    }

    @Test
    void markContinuityAppliedFailsWhenPreviousYearMissing() {
        AcademicYearConfig targetYear = new AcademicYearConfig();
        targetYear.setCode("2026/2027");
        when(academicYearRepository.findByCode("2026/2027")).thenReturn(Optional.of(targetYear));
        when(academicYearRepository.existsByCode("2025/2026")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.markContinuityApplied("2026/2027"));
    }
}

