package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.SchoolBuilding;
import org.springframework.data.repository.query.parser.PartTree;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolBuildingServiceImplFkCutoverTest {

    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private BuildingGroupRepository buildingGroupRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private MetaGroupRepository metaGroupRepository;

    private SchoolBuildingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolBuildingServiceImpl(
                schoolBuildingRepository,
                buildingGroupRepository,
                appUserRepository,
                classroomLeadershipRepository,
                metaGroupRepository
        );
    }

    @Test
    void directPhysicalSiteFkRepositoryMethodsUseNestedSchoolBuildingIdPath() {
        new PartTree("existsBySchoolBuilding_Id", ClassroomLeadershipEntry.class);
        new PartTree("existsBySchoolBuilding_Id", org.school.personalLoad.model.MetaGroup.class);
    }

    @Test
    void upsertWithBuildingGroupIdPersistsWritableBuildingGroupFk() {
        BuildingGroup group = group(7L, "СП7", "СП7");
        when(buildingGroupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп7|ул. новая, 1")).thenReturn(java.util.List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(null, 7L, "СП7", "СП7 — корпус", "ул. Новая, 1"));

        assertEquals(7L, saved.getBuildingGroupId());
        assertEquals("сп7|ул. новая, 1", saved.getCode());
        verify(schoolBuildingRepository).save(any(SchoolBuilding.class));
    }

    @Test
    void upsertWithoutBuildingGroupIdFailsBeforeDatabaseSave() {
        SchoolBuildingRequest request = request(null, null, "СП7", "СП7 — корпус", "ул. Новая, 1");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.upsert(request));

        assertEquals("buildingGroupId is required for school building", error.getMessage());
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void upsertWithUnknownBuildingGroupIdFailsBeforeDatabaseSave() {
        when(buildingGroupRepository.findById(77L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert(request(null, 77L, "СП7", "СП7 — корпус", "ул. Новая, 1")));

        assertEquals("BuildingGroup not found: 77", error.getMessage());
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void editUnusedBuildingCanChangeSchoolBuildingGroupAndRecomputesSiteCode() {
        SchoolBuilding existing = building(12L, group(1L, "СП1", "СП1"), "сп1|ул. старая, 1", "Старый", "ул. Старая, 1");
        BuildingGroup newGroup = group(2L, "СП2", "СП2");
        when(schoolBuildingRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(buildingGroupRepository.findById(2L)).thenReturn(Optional.of(newGroup));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп2|ул. другая, 2")).thenReturn(java.util.List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(12L, 2L, "СП2", "СП2 — корпус", "ул. Другая, 2"));

        assertEquals(2L, saved.getBuildingGroupId());
        assertEquals("сп2|ул. другая, 2", saved.getCode());
    }

    @Test
    void createAdditionalPhysicalSiteInsideSameOrganizationalSpUsesAddressScopedCode() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(sp3));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп3|мехмат")).thenReturn(java.util.List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(null, 3L, "СП3", "Мехмат", "мехмат"));

        assertEquals("сп3|мехмат", saved.getCode());
        assertEquals(3L, saved.getBuildingGroupId());
        assertEquals("Мехмат", saved.getName());
        assertEquals("мехмат", saved.getAddress());
        verify(classroomLeadershipRepository, never()).save(any());
    }

    @Test
    void createSecondPhysicalSiteInSameOrganizationalSpWithDifferentAddressIsAllowed() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(sp3));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп3|другой адрес")).thenReturn(java.util.List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(null, 3L, null, "Другой", "другой адрес"));

        assertEquals("сп3|другой адрес", saved.getCode());
    }

    @Test
    void createDuplicatePhysicalSiteInSameGroupAndAddressIsRejected() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        SchoolBuilding duplicate = building(38L, sp3, "сп3|мехмат", "Мехмат", "мехмат");
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(sp3));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп3|мехмат")).thenReturn(java.util.List.of(duplicate));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert(request(null, 3L, "СП3", "Мехмат", "мехмат")));

        assertEquals("Физическая площадка с таким адресом уже существует в выбранном СП", error.getMessage());
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void editDisplayNameKeepsCodeWhenAddressAndGroupDoNotChange() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        SchoolBuilding existing = building(47L, sp3, "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(sp3));
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(47L, 3L, "СП3", "Мехмат новый", "мехмат"));

        assertEquals("сп3|мехмат", saved.getCode());
        assertEquals("Мехмат новый", saved.getName());
        verify(schoolBuildingRepository, never()).findAllByCodeIgnoreCase(any());
    }

    @Test
    void editAddressOfUsedByClassPhysicalSiteIsRejected() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        SchoolBuilding existing = building(47L, sp3, "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(sp3));
        when(classroomLeadershipRepository.existsBySchoolBuilding_Id(47L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.upsert(request(47L, 3L, null, "Мехмат", "новый адрес")));

        assertTrue(error.getMessage().contains("Нельзя изменить адрес или основное СП используемой площадки"));
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void editGroupOfUsedByMetaGroupPhysicalSiteIsRejected() {
        BuildingGroup sp3 = group(3L, "СП3", "СП3");
        BuildingGroup sp4 = group(4L, "СП4", "СП4");
        SchoolBuilding existing = building(47L, sp3, "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));
        when(buildingGroupRepository.findById(4L)).thenReturn(Optional.of(sp4));
        when(metaGroupRepository.existsBySchoolBuilding_Id(47L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.upsert(request(47L, 4L, null, "Мехмат", "мехмат")));

        assertTrue(error.getMessage().contains("Нельзя изменить адрес или основное СП используемой площадки"));
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void updateWithUnknownIdDoesNotCreatePhysicalSite() {
        when(schoolBuildingRepository.findById(99L)).thenReturn(Optional.empty());
        when(buildingGroupRepository.findById(3L)).thenReturn(Optional.of(group(3L, "СП3", "СП3")));

        assertThrows(IllegalArgumentException.class, () -> service.upsert(request(99L, 3L, null, "Мехмат", "мехмат")));

        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void deleteUnusedPhysicalSiteIsAllowed() {
        SchoolBuilding existing = building(47L, group(3L, "СП3", "СП3"), "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));

        service.deleteById(47L);

        verify(schoolBuildingRepository).deleteById(47L);
    }

    @Test
    void deletePhysicalSiteReferencedByClassIsRejected() {
        SchoolBuilding existing = building(47L, group(3L, "СП3", "СП3"), "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));
        when(classroomLeadershipRepository.existsBySchoolBuilding_Id(47L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.deleteById(47L));

        assertEquals("Нельзя удалить площадку: к ней привязаны классы", error.getMessage());
        verify(schoolBuildingRepository, never()).deleteById(any());
    }

    @Test
    void deletePhysicalSiteReferencedByMetaGroupIsRejected() {
        SchoolBuilding existing = building(47L, group(3L, "СП3", "СП3"), "сп3|мехмат", "Мехмат", "мехмат");
        when(schoolBuildingRepository.findById(47L)).thenReturn(Optional.of(existing));
        when(metaGroupRepository.existsBySchoolBuilding_Id(47L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.deleteById(47L));

        assertEquals("Нельзя удалить площадку: к ней привязаны метагруппы", error.getMessage());
        verify(schoolBuildingRepository, never()).deleteById(any());
    }

    @Test
    void excelImportRequiresBuildingGroupIdAndNeverSavesNullFk() throws Exception {
        when(buildingGroupRepository.findById(7L)).thenReturn(Optional.of(group(7L, "СП7", "СП7")));
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("сп7|ул. новая, 1")).thenReturn(java.util.List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = buildingImportFile(true, 7L);

        service.importFromExcel(file);

        ArgumentCaptor<SchoolBuilding> captor = ArgumentCaptor.forClass(SchoolBuilding.class);
        verify(schoolBuildingRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getBuildingGroupId());
        assertEquals("сп7|ул. новая, 1", captor.getValue().getCode());
    }

    @Test
    void oldExcelImportWithoutBuildingGroupIdIsRejected() throws Exception {
        MockMultipartFile file = buildingImportFile(false, null);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.importFromExcel(file));

        assertEquals(true, error.getMessage().contains("старом формате"));
        verify(schoolBuildingRepository, never()).save(any());
    }

    private SchoolBuildingRequest request(Long id, Long buildingGroupId, String code, String name, String address) {
        SchoolBuildingRequest request = new SchoolBuildingRequest();
        request.setId(id);
        request.setBuildingGroupId(buildingGroupId);
        request.setCode(code);
        request.setName(name);
        request.setAddress(address);
        return request;
    }

    private BuildingGroup group(Long id, String code, String name) {
        BuildingGroup group = new BuildingGroup();
        group.setId(id);
        group.setCode(code);
        group.setName(name);
        return group;
    }

    private SchoolBuilding building(Long id, BuildingGroup group, String code, String name, String address) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setBuildingGroup(group);
        building.setCode(code);
        building.setName(name);
        building.setAddress(address);
        return building;
    }

    private MockMultipartFile buildingImportFile(boolean includeBuildingGroupId, Long buildingGroupId) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Корпуса");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Код");
            header.createCell(1).setCellValue("Название");
            header.createCell(2).setCellValue("Адрес");
            if (includeBuildingGroupId) {
                header.createCell(3).setCellValue("BUILDING_GROUP_ID");
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("СП7");
            row.createCell(1).setCellValue("СП7 — корпус");
            row.createCell(2).setCellValue("ул. Новая, 1");
            if (buildingGroupId != null) {
                row.createCell(3).setCellValue(buildingGroupId);
            }
            workbook.write(out);
            return new MockMultipartFile("file", "buildings.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
