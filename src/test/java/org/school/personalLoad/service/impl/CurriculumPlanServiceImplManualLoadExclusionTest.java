package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurriculumPlanServiceImplManualLoadExclusionTest {

    @Mock
    private CurriculumPlanEntryRepository repository;
    @Mock
    private StudyPeriodSettingRepository studyPeriodSettingRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;
    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;

    private CurriculumPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CurriculumPlanServiceImpl(repository, studyPeriodSettingRepository, studyPeriodSettingService, subjectCatalogRepository);
        when(studyPeriodSettingService.resolveRuleForClassAndPeriod(anyString(), anyString(), any())).thenReturn(studyPeriodRule());
        when(subjectCatalogRepository.findAll()).thenReturn(List.of());
        when(repository.findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(CurriculumPlanEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ordinaryExcludedClassRowMirrorsExclusionToLegacyMetaGroupFlag() {
        CurriculumPlanEntryRequest request = request("4-Е");
        request.setExcludedFromManualLoad(true);

        service.upsert(request);

        CurriculumPlanEntry saved = captureSaved();
        assertTrue(saved.isExcludedFromManualLoad());
        assertTrue(saved.isMetaGroup());
    }

    @Test
    void explicitMetaGroupRowIsIncludedAndKeepsLegacyMetaGroupIdentity() {
        CurriculumPlanEntryRequest request = request("МГ:4 4ЦЧ-СВЕТСКАЯ");
        request.setExcludedFromManualLoad(false);

        service.upsert(request);

        CurriculumPlanEntry saved = captureSaved();
        assertFalse(saved.isExcludedFromManualLoad());
        assertTrue(saved.isMetaGroup());
    }

    private CurriculumPlanEntry captureSaved() {
        ArgumentCaptor<CurriculumPlanEntry> captor = ArgumentCaptor.forClass(CurriculumPlanEntry.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private CurriculumPlanEntryRequest request(String className) {
        CurriculumPlanEntryRequest request = new CurriculumPlanEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding("СП1");
        request.setClassName(className);
        request.setSubjectName("ОДНКНР");
        request.setPlannedHours(BigDecimal.ONE);
        request.setEducationLevel(EducationLevel.BASIC);
        request.setCurriculumPart(CurriculumPart.CORE);
        request.setStudyPeriod(StudyPeriod.YEAR);
        request.setSubgroupRequired(false);
        return request;
    }

    private StudyPeriodSetting studyPeriodRule() {
        StudyPeriodSetting rule = new StudyPeriodSetting();
        rule.setId(1L);
        rule.setAcademicYear("2026/2027");
        rule.setCode("YEAR");
        rule.setStudyPeriod(StudyPeriod.YEAR);
        rule.setParallelFrom(1);
        rule.setParallelTo(11);
        rule.setDisplayName("Учебный год");
        rule.setStartDate(LocalDate.of(2026, 9, 1));
        rule.setEndDate(LocalDate.of(2027, 5, 31));
        return rule;
    }
}
