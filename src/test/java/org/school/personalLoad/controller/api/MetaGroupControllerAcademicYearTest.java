package org.school.personalLoad.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
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
    private BuildingGroupRepository buildingGroupRepository;
    private StudyPeriodSettingRepository studyPeriodSettingRepository;
    private AcademicYearService academicYearService;
    private MetaGroupController controller;

    @BeforeEach
    void setUp() {
        metaGroupRepository = mock(MetaGroupRepository.class);
        curriculumPlanEntryRepository = mock(CurriculumPlanEntryRepository.class);
        manualLoadEntryRepository = mock(ManualLoadEntryRepository.class);
        schoolBuildingRepository = mock(SchoolBuildingRepository.class);
        buildingGroupRepository = mock(BuildingGroupRepository.class);
        studyPeriodSettingRepository = mock(StudyPeriodSettingRepository.class);
        academicYearService = mock(AcademicYearService.class);
        controller = new MetaGroupController(
                metaGroupRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository,
                schoolBuildingRepository,
                buildingGroupRepository,
                studyPeriodSettingRepository,
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
        BuildingGroup buildingGroup = buildingGroup(1L, "СП1");
        SchoolBuilding building = schoolBuilding(36L, buildingGroup);
        when(buildingGroupRepository.findByCodeIgnoreCase("СП1")).thenReturn(Optional.of(buildingGroup));
        when(schoolBuildingRepository.findById(36L)).thenReturn(Optional.of(building));
        when(academicYearService.resolveRequestedOrDefault("2025/2026")).thenReturn("2025/2026");
        when(metaGroupRepository.existsByAcademicYearAndNumberSchoolBuildingAndParallelAndNameIgnoreCaseAndClassType(
                "2025/2026", "СП1", 4, "4 ФИЗИКА", "NORMAL"))
                .thenReturn(false);
        when(metaGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MetaGroup saved = controller.create("2025/2026", createRequest()).getBody();

        assertNotNull(saved);
        assertEquals("2025/2026", saved.getAcademicYear());
        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(1L, saved.getBuildingGroupId());
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
        StudyPeriodSetting setting = studyPeriodSetting(99L, StudyPeriod.H1);
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(buildingGroupRepository.findByCodeIgnoreCase("СП1")).thenReturn(Optional.of(existing.getBuildingGroup()));
        when(studyPeriodSettingRepository.findById(99L)).thenReturn(Optional.of(setting));
        when(curriculumPlanEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(current, archive));
        when(manualLoadEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of());
        when(metaGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.update(4L, "2026/2027", new MetaGroupController.UpdateMetaGroupRequest(
                null, null, null, null, 99L, null
        ));

        assertEquals(99L, current.getStudyPeriodSettingId());
        assertEquals(StudyPeriod.H1, current.getStudyPeriod());
        assertNull(archive.getStudyPeriodSettingId());
        verify(curriculumPlanEntryRepository).saveAll(List.of(current));
    }

    @Test
    void updatePersistsOnlyPhysicalSchoolBuildingWithoutChangingOrganizationalSp() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        SchoolBuilding originalPhysicalSite = existing.getSchoolBuilding();
        SchoolBuilding newPhysicalSite = schoolBuilding(37L, existing.getBuildingGroup());
        newPhysicalSite.setCode("СП21");
        newPhysicalSite.setAddress("Ломоносовский пр-кт, д. 3А");
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(buildingGroupRepository.findByCodeIgnoreCase("СП1")).thenReturn(Optional.of(existing.getBuildingGroup()));
        when(studyPeriodSettingRepository.findById(11L)).thenReturn(Optional.of(studyPeriodSetting(11L, StudyPeriod.YEAR)));
        when(schoolBuildingRepository.findById(37L)).thenReturn(Optional.of(newPhysicalSite));
        when(metaGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MetaGroup saved = controller.update(4L, "2026/2027", new MetaGroupController.UpdateMetaGroupRequest(
                null, null, null, null, null, 37L
        )).getBody();

        assertNotNull(saved);
        assertEquals(37L, saved.getSchoolBuildingId());
        assertEquals("СП21", saved.getSchoolBuilding().getCode());
        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(existing.getBuildingGroup(), saved.getBuildingGroup());
        assertNotEquals(originalPhysicalSite, saved.getSchoolBuilding());
        verify(schoolBuildingRepository).findById(37L);
        verify(curriculumPlanEntryRepository, never()).saveAll(anyList());
        verify(manualLoadEntryRepository, never()).saveAll(anyList());
        verify(metaGroupRepository).saveAndFlush(existing);
    }

    @Test
    void updateOrganizationalSpPersistsBuildingGroupAndKeepsPhysicalSite() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        BuildingGroup sp3 = buildingGroup(3L, "СП3");
        SchoolBuilding originalPhysicalSite = existing.getSchoolBuilding();
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(buildingGroupRepository.findByCodeIgnoreCase("СП3")).thenReturn(Optional.of(sp3));
        when(studyPeriodSettingRepository.findById(11L)).thenReturn(Optional.of(studyPeriodSetting(11L, StudyPeriod.YEAR)));
        when(curriculumPlanEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of());
        when(manualLoadEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of());
        when(metaGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MetaGroup saved = controller.update(4L, "2026/2027", new MetaGroupController.UpdateMetaGroupRequest(
                "СП3", null, null, null, null, null
        )).getBody();

        assertNotNull(saved);
        assertEquals("СП3", saved.getNumberSchoolBuilding());
        assertEquals(3L, saved.getBuildingGroupId());
        assertEquals(originalPhysicalSite, saved.getSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
        verify(metaGroupRepository).saveAndFlush(existing);
    }

    @Test
    void updateOrganizationalSpSynchronizesLinkedExplicitRowsAndPreservesAssignments() {
        MetaGroup existing = metaGroup(4L, "2026/2027");
        BuildingGroup sp3 = buildingGroup(3L, "СП3");
        CurriculumPlanEntry current = curriculumEntry("2026/2027");
        current.setId(100L);
        current.setNumberSchoolBuilding("СП2");
        current.setClassName("МГ:4 4ЦЧ-СВЕТСКАЯ");
        current.setStudyPeriod(StudyPeriod.YEAR);
        current.setStudyPeriodSettingId(11L);
        current.setMetaGroupId(4L);
        current.setClassId(777L);
        current.setMetaGroup(false);
        current.setExcludedFromManualLoad(true);
        SubjectCatalogEntry curriculumSubject = subject(501L);
        current.setSubject(curriculumSubject);
        current.setSubjectName("ОДНКНР");
        ManualLoadEntry manual = manualEntry("2026/2027");
        manual.setId(200L);
        manual.setMetaGroupId(4L);
        manual.setTeacherId(300L);
        manual.setFioTeacher("Иванов И.И.");
        manual.setSubject(subject(502L));
        manual.setSubjectName("ОДНКНР");
        manual.setLoad(1);
        manual.setGroupLoad(1);
        manual.setStudyPeriod(StudyPeriod.YEAR);
        manual.setClassId(888L);
        manual.setClassName("МГ:4 4ЦЧ-СВЕТСКАЯ");
        java.time.LocalDate from = java.time.LocalDate.of(2026, 9, 1);
        java.time.LocalDate to = java.time.LocalDate.of(2027, 5, 31);
        manual.setLoadFromDate(from);
        manual.setLoadToDate(to);
        StudyPeriodSetting setting = studyPeriodSetting(99L, StudyPeriod.H1);
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(academicYearService.resolveRequestedOrDefault("2026/2027")).thenReturn("2026/2027");
        when(buildingGroupRepository.findByCodeIgnoreCase("СП3")).thenReturn(Optional.of(sp3));
        when(studyPeriodSettingRepository.findById(99L)).thenReturn(Optional.of(setting));
        when(curriculumPlanEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(current, curriculumEntry("2025/2026")));
        when(manualLoadEntryRepository.findAllByMetaGroupId(4L)).thenReturn(List.of(manual, manualEntry("2025/2026")));
        when(metaGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.update(4L, "2026/2027", new MetaGroupController.UpdateMetaGroupRequest(
                "СП3", null, "Светская", null, 99L, null
        ));

        assertEquals("СП3", current.getNumberSchoolBuilding());
        assertEquals("МГ:4 СВЕТСКАЯ", current.getClassName());
        assertEquals(99L, current.getStudyPeriodSettingId());
        assertEquals(StudyPeriod.H1, current.getStudyPeriod());
        assertTrue(current.isMetaGroup());
        assertFalse(current.isExcludedFromManualLoad());
        assertNull(current.getClassId());
        assertEquals(4L, current.getMetaGroupId());
        assertEquals(501L, current.getSubjectId());

        assertEquals("СП3", manual.getNumberSchoolBuilding());
        assertEquals("МГ:4 СВЕТСКАЯ", manual.getClassName());
        assertNull(manual.getClassId());
        assertEquals(4L, manual.getMetaGroupId());
        assertEquals(300L, manual.getTeacherId());
        assertEquals(502L, manual.getSubjectId());
        assertEquals(1, manual.getLoad());
        assertEquals(1, manual.getGroupLoad());
        assertEquals(StudyPeriod.YEAR, manual.getStudyPeriod());
        assertEquals(from, manual.getLoadFromDate());
        assertEquals(to, manual.getLoadToDate());
        verify(curriculumPlanEntryRepository).saveAll(List.of(current));
        verify(manualLoadEntryRepository).saveAll(List.of(manual));
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
        BuildingGroup buildingGroup = buildingGroup(1L, "СП1");
        metaGroup.setNumberSchoolBuilding("СП1");
        metaGroup.setBuildingGroup(buildingGroup);
        metaGroup.setParallel(4);
        metaGroup.setName("4 ФИЗИКА");
        metaGroup.setClassType("NORMAL");
        metaGroup.setStudyPeriodSettingId(11L);
        metaGroup.setSchoolBuilding(schoolBuilding(36L, buildingGroup));
        return metaGroup;
    }

    private BuildingGroup buildingGroup(Long id, String code) {
        BuildingGroup group = new BuildingGroup();
        group.setId(id);
        group.setCode(code);
        group.setName(code);
        return group;
    }

    private SchoolBuilding schoolBuilding(Long id, BuildingGroup buildingGroup) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode(buildingGroup.getCode());
        building.setName(buildingGroup.getCode());
        building.setAddress("Адрес");
        building.setBuildingGroup(buildingGroup);
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

    private StudyPeriodSetting studyPeriodSetting(Long id, StudyPeriod studyPeriod) {
        StudyPeriodSetting setting = new StudyPeriodSetting();
        setting.setId(id);
        setting.setAcademicYear("2026/2027");
        setting.setCode(studyPeriod.name());
        setting.setStudyPeriod(studyPeriod);
        setting.setParallelFrom(1);
        setting.setParallelTo(11);
        return setting;
    }

    private SubjectCatalogEntry subject(Long id) {
        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setId(id);
        subject.setSubjectName("ОДНКНР");
        return subject;
    }
}
