package org.school.personalLoad.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.CurriculumPlanEntryResponse;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.CurriculumImportService;
import org.school.personalLoad.service.CurriculumPlanService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumPlanControllerMetaGroupPhysicalSiteTest {

    private CurriculumPlanService curriculumPlanService;
    private MetaGroupRepository metaGroupRepository;
    private AcademicYearService academicYearService;
    private CurriculumPlanController controller;

    @BeforeEach
    void setUp() {
        curriculumPlanService = mock(CurriculumPlanService.class);
        CurriculumImportService curriculumImportService = mock(CurriculumImportService.class);
        academicYearService = mock(AcademicYearService.class);
        metaGroupRepository = mock(MetaGroupRepository.class);
        controller = new CurriculumPlanController(curriculumPlanService, curriculumImportService, academicYearService, metaGroupRepository);
    }

    @Test
    void curriculumApiExposesMetaGroupPhysicalSiteWithoutChangingOrganizationalSp() {
        CurriculumPlanEntry explicitMetaRow = curriculumRow();
        explicitMetaRow.setMetaGroupId(4L);
        MetaGroup metaGroup = metaGroup(4L, 37L);
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(curriculumPlanService.findAll("2026/2027", null)).thenReturn(List.of(explicitMetaRow));
        when(metaGroupRepository.findAllById(List.of(4L))).thenReturn(List.of(metaGroup));

        List<CurriculumPlanEntryResponse> rows = controller.findAll("2026/2027", null).getBody();

        assertEquals(1, rows.size());
        CurriculumPlanEntryResponse row = rows.get(0);
        assertEquals("МГ:4 4ЦЧ-СВЕТСКАЯ", row.getClassName());
        assertEquals("СП2", row.getNumberSchoolBuilding());
        assertNull(row.getClassId());
        assertEquals(4L, row.getMetaGroupId());
        assertEquals(37L, row.getSchoolBuildingId());
        assertEquals(BigDecimal.ONE, row.getPlannedHours());
        verify(metaGroupRepository).findAllById(List.of(4L));
    }

    private CurriculumPlanEntry curriculumRow() {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setId(10L);
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding("СП2");
        entry.setClassName("МГ:4 4ЦЧ-СВЕТСКАЯ");
        entry.setSubjectName("ОДНКНР");
        entry.setPlannedHours(BigDecimal.ONE);
        entry.setStudyPeriod(StudyPeriod.YEAR);
        entry.setCurriculumPart(CurriculumPart.CORE);
        entry.setEducationLevel(EducationLevel.BASIC);
        entry.setSubgroupRequired(false);
        entry.setSubgroupCount(0);
        entry.setMetaGroup(true);
        return entry;
    }

    private MetaGroup metaGroup(Long id, Long schoolBuildingId) {
        MetaGroup metaGroup = new MetaGroup();
        metaGroup.setId(id);
        metaGroup.setAcademicYear("2026/2027");
        metaGroup.setNumberSchoolBuilding("СП2");
        metaGroup.setParallel(4);
        metaGroup.setName("4 4ЦЧ-СВЕТСКАЯ");
        metaGroup.setSchoolBuilding(schoolBuilding(schoolBuildingId));
        return metaGroup;
    }

    private SchoolBuilding schoolBuilding(Long id) {
        SchoolBuilding schoolBuilding = new SchoolBuilding();
        schoolBuilding.setId(id);
        schoolBuilding.setCode("СП21");
        schoolBuilding.setAddress("Ломоносовский пр-кт, д. 3А");
        return schoolBuilding;
    }
}
