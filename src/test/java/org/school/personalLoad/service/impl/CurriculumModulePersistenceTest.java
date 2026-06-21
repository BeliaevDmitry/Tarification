package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.CurriculumPlanEntryResponse;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumModule;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.persistence.EntityManager;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(CurriculumPlanServiceImpl.class)
class CurriculumModulePersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.school")
    @EnableJpaRepositories("org.school.personalLoad.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private CurriculumPlanEntryRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CurriculumPlanServiceImpl service;
    @Autowired
    private StudyPeriodSettingRepository studyPeriodSettingRepository;
    @MockBean
    private StudyPeriodSettingService studyPeriodSettingService;

    @Test
    void fiveHourSubjectReloadsFromDatabaseAsTwoModulesAndSerializesThem() throws Exception {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding("СП1");
        entry.setClassName("7-А");
        entry.setSubjectName("Труд");
        entry.setPlannedHours(BigDecimal.valueOf(5));
        entry.setEducationLevel(EducationLevel.BASIC);
        entry.setCurriculumPart(CurriculumPart.CORE);
        entry.setStudyPeriod(StudyPeriod.YEAR);
        entry.setSubgroupRequired(false);
        entry.setSubgroupCount(0);
        entry.setModularSystem(true);
        entry.getModules().add(module(entry, 1, "Черчение", 2));
        entry.getModules().add(module(entry, 2, "Программирование", 3));

        Long id = repository.saveAndFlush(entry).getId();
        entityManager.clear();
        CurriculumPlanEntry reloaded = repository.findById(id).orElseThrow();

        assertTrue(reloaded.isModularSystem());
        assertEquals(2, reloaded.getModules().size());
        assertNotNull(reloaded.getModules().get(0).getId());
        assertEquals("Черчение", reloaded.getModules().get(0).getModuleName());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(reloaded.getModules().get(0).getPlannedHours()));
        assertEquals("Программирование", reloaded.getModules().get(1).getModuleName());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(reloaded.getModules().get(1).getPlannedHours()));

        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(CurriculumPlanEntryResponse.from(reloaded, null));
        assertTrue(json.contains("\"modularSystem\":true"));
        assertTrue(json.contains("\"moduleName\":\"Черчение\""));
        assertTrue(json.contains("\"moduleName\":\"Программирование\""));
    }

    @Test
    void serviceCreatesFiveHourSubjectAndFindAllReturnsTwoPersistedModules() {
        StudyPeriodSetting period = new StudyPeriodSetting();
        period.setAcademicYear("2026/2027");
        period.setCode("YEAR");
        period.setDisplayName("Учебный год");
        period.setStudyPeriod(StudyPeriod.YEAR);
        period.setParallelFrom(1);
        period.setParallelTo(11);
        period.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        period.setEndDate(java.time.LocalDate.of(2027, 5, 31));
        period = studyPeriodSettingRepository.saveAndFlush(period);

        CurriculumPlanEntryRequest request = new CurriculumPlanEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding("СП1");
        request.setClassName("7-А");
        request.setSubjectName("Труд");
        request.setPlannedHours(BigDecimal.valueOf(5));
        request.setEducationLevel(EducationLevel.BASIC);
        request.setCurriculumPart(CurriculumPart.CORE);
        request.setStudyPeriodSettingId(period.getId());
        request.setModularSystem(true);
        request.setModules(java.util.List.of(
                moduleRequest("Черчение", 2),
                moduleRequest("Программирование", 3)
        ));

        Long id = service.upsert(request).getId();
        entityManager.flush();
        entityManager.clear();
        CurriculumPlanEntry reloaded = service.findAll("2026/2027").stream()
                .filter(row -> row.getId().equals(id))
                .findFirst()
                .orElseThrow();

        assertTrue(reloaded.isModularSystem());
        assertEquals(2, reloaded.getModules().size());
        assertEquals("Черчение", reloaded.getModules().get(0).getModuleName());
        assertEquals("Программирование", reloaded.getModules().get(1).getModuleName());
    }

    private CurriculumModule module(CurriculumPlanEntry parent, int order, String name, int hours) {
        CurriculumModule module = new CurriculumModule();
        module.setCurriculumEntry(parent);
        module.setModuleOrder(order);
        module.setModuleName(name);
        module.setPlannedHours(BigDecimal.valueOf(hours));
        module.setEducationLevel(EducationLevel.BASIC);
        module.setSubgroupRequired(false);
        module.setSubgroupCount(0);
        return module;
    }

    private CurriculumPlanEntryRequest.ModuleRequest moduleRequest(String name, int hours) {
        CurriculumPlanEntryRequest.ModuleRequest module = new CurriculumPlanEntryRequest.ModuleRequest();
        module.setModuleName(name);
        module.setPlannedHours(BigDecimal.valueOf(hours));
        module.setEducationLevel(EducationLevel.BASIC);
        module.setSubgroupRequired(false);
        return module;
    }
}
