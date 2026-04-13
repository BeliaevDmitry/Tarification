package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.school.personalLoad.service.TarifficationProcessingService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualLoadServiceImplBulkReplaceTest {

    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private TarifficationProcessingService tarifficationProcessingService;
    @Mock
    private DatabaseService databaseService;
    @Mock
    private CurriculumPlanService curriculumPlanService;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;

    private ManualLoadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManualLoadServiceImpl(
                manualLoadEntryRepository,
                tarifficationProcessingService,
                databaseService,
                curriculumPlanService,
                studyPeriodSettingService
        );
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createBulkReplacesRowsForAffectedBuilding() {
        ManualLoadEntryRequest request = new ManualLoadEntryRequest();
        request.setAcademicYear("2025/2026");
        request.setFioTeacher("Иванов И.И.");
        request.setNumberSchoolBuilding("B1");
        request.setSubjectName("Алгебра");
        request.setClassName("8-А");
        request.setLoad(6);
        request.setEducationLevel(EducationLevel.BASIC);
        request.setLoadFromDate(LocalDate.of(2025, 9, 1));
        request.setLoadToDate(LocalDate.of(2026, 5, 31));

        service.createBulk(List.of(request));

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.Set.of("b1"));
        verify(manualLoadEntryRepository).saveAll(any());
    }
}
