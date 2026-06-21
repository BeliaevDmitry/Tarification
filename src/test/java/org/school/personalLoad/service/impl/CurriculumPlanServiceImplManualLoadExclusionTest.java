package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.school.personalLoad.repository.CurriculumModuleRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurriculumPlanServiceImplManualLoadExclusionTest {

    @Mock
    private CurriculumPlanEntryRepository repository;
    @Mock
    private CurriculumModuleRepository curriculumModuleRepository;
    @Mock
    private StudyPeriodSettingRepository studyPeriodSettingRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;
    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;

    private CurriculumPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CurriculumPlanServiceImpl(repository, curriculumModuleRepository, studyPeriodSettingRepository, studyPeriodSettingService, subjectCatalogRepository);
        lenient().when(studyPeriodSettingService.resolveRuleForClassAndPeriod(anyString(), anyString(), any())).thenReturn(studyPeriodRule());
        lenient().when(subjectCatalogRepository.findAll()).thenReturn(List.of());
        lenient().when(repository.findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(repository.save(any(CurriculumPlanEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    @Test
    void modularSubjectPersistsTwoOrMoreModulesWithExactTotal() {
        CurriculumPlanEntryRequest request = request("7-А");
        request.setSubjectName("Труд");
        request.setPlannedHours(BigDecimal.valueOf(2));
        request.setModularSystem(true);
        request.setModules(List.of(module("Черчение", 1), module("Программирование", 1)));

        service.upsert(request);

        CurriculumPlanEntry saved = captureSaved();
        assertTrue(saved.isModularSystem());
        assertFalse(saved.isSubgroupRequired());
        assertEquals(2, saved.getModules().size());
        assertEquals("Черчение", saved.getModules().get(0).getModuleName());
    }

    @Test
    void modularSubjectRejectsModuleHoursDifferentFromSubjectHours() {
        CurriculumPlanEntryRequest request = request("7-А");
        request.setPlannedHours(BigDecimal.valueOf(3));
        request.setModularSystem(true);
        request.setModules(List.of(module("Черчение", 1), module("Программирование", 1)));

        assertThrows(IllegalArgumentException.class, () -> service.upsert(request));
    }

    @Test
    void existingOrdinarySubjectKeepsNewModulesAfterUpdate() {
        CurriculumPlanEntry existing = new CurriculumPlanEntry();
        existing.setId(42L);
        existing.setSubjectName("Труд");
        existing.setPlannedHours(BigDecimal.valueOf(5));
        when(repository.findById(42L)).thenReturn(Optional.of(existing));
        CurriculumPlanEntryRequest request = request("7-А");
        request.setSubjectName("Труд");
        request.setPlannedHours(BigDecimal.valueOf(5));
        request.setModularSystem(true);
        request.setModules(List.of(module("Черчение", 2), module("Программирование", 3)));

        CurriculumPlanEntry saved = service.updateById(42L, request);

        assertTrue(saved.isModularSystem());
        assertEquals(2, saved.getModules().size());
        assertEquals("Черчение", saved.getModules().get(0).getModuleName());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(saved.getModules().get(0).getPlannedHours()));
        assertEquals("Программирование", saved.getModules().get(1).getModuleName());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(saved.getModules().get(1).getPlannedHours()));
        verify(curriculumModuleRepository).saveAll(saved.getModules());
    }

    @Test
    void browserJsonBindsModularFiveHourSubjectAsTwoPlusThree() throws Exception {
        String json = """
                {
                  "academicYear":"2026/2027",
                  "numberSchoolBuilding":"СП1",
                  "className":"7-А",
                  "subjectName":"Труд",
                  "plannedHours":5,
                  "educationLevel":"BASIC",
                  "curriculumPart":"CORE",
                  "modularSystem":true,
                  "modules":[
                    {"moduleOrder":1,"moduleName":"Черчение","plannedHours":2,"educationLevel":"BASIC","subgroupRequired":false},
                    {"moduleOrder":2,"moduleName":"Программирование","plannedHours":3,"educationLevel":"BASIC","subgroupRequired":false}
                  ]
                }
                """;

        CurriculumPlanEntryRequest request = new ObjectMapper().readValue(json, CurriculumPlanEntryRequest.class);

        assertTrue(request.isModularSystem());
        assertEquals(2, request.getModules().size());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(request.getPlannedHours()));
        assertEquals(0, BigDecimal.valueOf(2).compareTo(request.getModules().get(0).getPlannedHours()));
        assertEquals(0, BigDecimal.valueOf(3).compareTo(request.getModules().get(1).getPlannedHours()));
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

    private CurriculumPlanEntryRequest.ModuleRequest module(String name, int hours) {
        CurriculumPlanEntryRequest.ModuleRequest module = new CurriculumPlanEntryRequest.ModuleRequest();
        module.setModuleName(name);
        module.setPlannedHours(BigDecimal.valueOf(hours));
        module.setEducationLevel(EducationLevel.BASIC);
        return module;
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
