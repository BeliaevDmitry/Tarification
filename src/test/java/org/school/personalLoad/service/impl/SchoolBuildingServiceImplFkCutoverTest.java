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
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private MetaGroupRepository metaGroupRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;

    private SchoolBuildingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolBuildingServiceImpl(
                schoolBuildingRepository,
                buildingGroupRepository,
                appUserRepository,
                classroomLeadershipRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository,
                metaGroupRepository,
                teacherDirectoryRepository
        );
    }

    @Test
    void upsertWithBuildingGroupIdPersistsWritableBuildingGroupFk() {
        BuildingGroup group = group(7L, "СП7", "СП7");
        when(buildingGroupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(null, 7L, "СП7", "СП7 — корпус", "ул. Новая, 1"));

        assertEquals(7L, saved.getBuildingGroupId());
        assertEquals("СП7", saved.getCode());
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
    void editCanChangeSchoolBuildingGroupExplicitly() {
        SchoolBuilding existing = new SchoolBuilding();
        existing.setId(12L);
        existing.setBuildingGroup(group(1L, "СП1", "СП1"));
        BuildingGroup newGroup = group(2L, "СП2", "СП2");
        when(schoolBuildingRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(buildingGroupRepository.findById(2L)).thenReturn(Optional.of(newGroup));
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBuilding saved = service.upsert(request(12L, 2L, "СП2", "СП2 — корпус", "ул. Другая, 2"));

        assertEquals(2L, saved.getBuildingGroupId());
    }

    @Test
    void excelImportRequiresBuildingGroupIdAndNeverSavesNullFk() throws Exception {
        when(buildingGroupRepository.findById(7L)).thenReturn(Optional.of(group(7L, "СП7", "СП7")));
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = buildingImportFile(true, 7L);

        service.importFromExcel(file);

        ArgumentCaptor<SchoolBuilding> captor = ArgumentCaptor.forClass(SchoolBuilding.class);
        verify(schoolBuildingRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getBuildingGroupId());
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
