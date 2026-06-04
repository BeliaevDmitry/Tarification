package org.school.personalLoad.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.service.AcademicYearService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetaGroupControllerAcademicYearTest {

    private MetaGroupRepository metaGroupRepository;
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private ManualLoadEntryRepository manualLoadEntryRepository;
    private SchoolBuildingRepository schoolBuildingRepository;
    private AcademicYearService academicYearService;
    private MetaGroupController controller;

    @BeforeEach
    void setUp() {
        metaGroupRepository = mock(MetaGroupRepository.class);
        curriculumPlanEntryRepository = mock(CurriculumPlanEntryRepository.class);
        manualLoadEntryRepository = mock(ManualLoadEntryRepository.class);
        schoolBuildingRepository = mock(SchoolBuildingRepository.class);
        academicYearService = mock(AcademicYearService.class);
        controller = new MetaGroupController(
                metaGroupRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository,
                schoolBuildingRepository,
                academicYearService
        );
    }

    @Test
    void getReturnsOnlyRequestedAcademicYear() {
        MetaGroup archive = metaGroup(1L, "2025/2026");
        when(academicYearService.resolveRequestedOrDefault("2025/2026")).thenReturn("2025/2026");
        when(metaGroupRepository.findAllByAcademicYearOrderByNumberSchoolBuildingAscParallelAscNameAsc("2025/2026"))
                .thenReturn(List.of(archive));

        List<MetaGroup> result = controller.findAll("2025/2026").getBody();

        assertEquals(List.of(archive), result);
        verify(metaGroupRepository).findAllByAcademicYearOrderByNumberSchoolBuildingAscParallelAscNameAsc("2025/2026");
        verify(metaGroupRepository, never()).findAll();
    }

    @Test
    void createAllowsSameScopeInDifferentAcademicYearsButRejectsDuplicateInSameYear() {
        SchoolBuilding building = schoolBuilding(36L);
        when(schoolBuildingRepository.findById(36L)).thenReturn(Optional.of(building));
        when(academicYearService.resolveRequestedOrDefault("2025/2026")).thenReturn("2025/2026");
        when(metaGroupRepository.existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(
                "2025/2026", "СП1", 4, "4 ФИЗИКА", "NORMAL"))
                .thenReturn(false);
        when(metaGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MetaGroup saved = controller.create("2025/2026", createRequest()).getBody();

        assertNotNull(saved);
        assertEquals("2025/2026", saved.getAcademicYear());
        verify(metaGroupRepository).existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(
                "2025/2026", "СП1", 4, "4 ФИЗИКА", "NORMAL");
        verify(metaGroupRepository, never()).existsByNumberSchoolBuildingIgnoreCase(anyString());

        when(metaGroupRepository.existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(
                "2025/2026", "СП1", 4, "4 ФИЗИКА", "NORMAL"))
                .thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> controller.create("2025/2026", createRequest()));
    }

    @Test
    void updateRejectsMetaGroupFromAnotherSelectedYear() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2025/2026")).thenReturn("2025/2026");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.update(4L, "2025/2026", updateRequest()));

        assertEquals("Метагруппа относится к другому учебному году", error.getMessage());
        verify(metaGroupRepository, never()).save(any());
    }

    @Test
    void deleteRejectsMetaGroupFromAnotherSelectedYear() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2025/2026")).thenReturn("2025/2026");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.delete(4L, "2025/2026"));

        assertEquals("Метагруппа относится к другому учебному году", error.getMessage());
        verify(metaGroupRepository, never()).delete(any());
    }

    @Test
    void updateOnlyTouchesCurriculumRowsFromSelectedYear() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        CurriculumPlanEntry current = curriculumEntry("2026/2027");
        CurriculumPlanEntry archive = curriculumEntry("2025/2026");
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(curriculumPlanEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(current, archive));
        when(metaGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.update(4L, "2026/2027", new MetaGroupController.UpdateMetaGroupRequest(
                null, null, null, null, 99L, null
        ));

        assertEquals(99L, current.getStudyPeriodSettingId());
        assertNull(archive.getStudyPeriodSettingId());
        verify(curriculumPlanEntryRepository).saveAll(List.of(current));
    }

    @Test
    void deleteRejectsCrossYearDependentRowsEvenWhenMetaGroupYearMatches() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(curriculumPlanEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(curriculumEntry("2025/2026")));
        when(manualLoadEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(manualEntry("2026/2027")));

        assertThrows(IllegalStateException.class, () -> controller.delete(4L, "2026/2027"));

        verify(curriculumPlanEntryRepository, never()).deleteByMetaGroupId(anyLong());
        verify(manualLoadEntryRepository, never()).deleteByMetaGroupId(anyLong());
        verify(metaGroupRepository, never()).delete(any());
    }

    private MetaGroupController.CreateMetaGroupRequest createRequest() {
        return new MetaGroupController.CreateMetaGroupRequest("СП1", 4, "Физика", "NORMAL", 11L, 36L);
    }

    private MetaGroupController.UpdateMetaGroupRequest updateRequest() {
        return new MetaGroupController.UpdateMetaGroupRequest(null, null, null, null, null, null);
    }

    private MetaGroup metaGroup(Long id, String academicYear) {
        MetaGroup metaGroup = new MetaGroup();
        metaGroup.setId(id);
        metaGroup.setAcademicYear(academicYear);
        metaGroup.setNumberSchoolBuilding("СП1");
        metaGroup.setParallel(4);
        metaGroup.setName("4 ФИЗИКА");
        metaGroup.setClassType("NORMAL");
        metaGroup.setStudyPeriodSettingId(11L);
        metaGroup.setSchoolBuilding(schoolBuilding(36L));
        return metaGroup;
    }

    private SchoolBuilding schoolBuilding(Long id) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode("СП1");
        building.setName("СП1");
        building.setAddress("Адрес");
        return building;
    }

    private CurriculumPlanEntry curriculumEntry(String academicYear) {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setAcademicYear(academicYear);
        return entry;
    }

    private ManualLoadEntry manualEntry(String academicYear) {
        ManualLoadEntry entry = new ManualLoadEntry();
        entry.setAcademicYear(academicYear);
        return entry;
    }
}
