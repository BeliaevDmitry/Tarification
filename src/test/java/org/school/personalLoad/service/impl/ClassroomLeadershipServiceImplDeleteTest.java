package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.ClassroomBuildingScopeUpdateRequest;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomLeadershipServiceImplDeleteTest {

    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private BuildingGroupRepository buildingGroupRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;

    private ClassroomLeadershipServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomLeadershipServiceImpl(
                classroomLeadershipRepository,
                teacherDirectoryRepository,
                schoolBuildingRepository,
                buildingGroupRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository
        );
    }

    @Test
    void deleteOneRemovesClassAndAllLoadAndCurriculumTails() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП3", "7-А");
        when(classroomLeadershipRepository.findByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А"))
                .thenReturn(Optional.of(entry));

        service.deleteOne("2026/2027", "СП3", "7-А");

        verify(curriculumPlanEntryRepository).deleteByAcademicYearAndClassId("2026/2027", 42L);
        verify(manualLoadEntryRepository).deleteByAcademicYearAndClassIds("2026/2027", List.of(42L));
        verify(classroomLeadershipRepository).delete(entry);
    }

    @Test
    void updateOneChangesBuildingByIdWithoutPropagatingLoadAndCurriculumTails() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        ClassroomLeadershipEntryRequest request = new ClassroomLeadershipEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding("СП2|ЛЕНИНА,Д.1");
        request.setClassName("7-А");
        request.setClassDirection("Инженерный");
        request.setTeacherId(2L);
        request.setFioTeacher("Петров П.П.");
        request.setCampusAddress("Ленина, д.1");
        request.setClassType("REGULAR");

        TeacherDirectoryEntry teacher = teacher(2L, "Петров П.П.");
        SchoolBuilding building = new SchoolBuilding();
        building.setId(36L);
        building.setCode("СП-2");
        building.setName("СП2");
        building.setAddress("Ленина, д.1");
        building.setManagerFio("Директор");

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findByCode("СП2")).thenReturn(Optional.empty());
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(building));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП2", saved.getNumberSchoolBuilding());
        assertEquals("Инженерный", saved.getClassDirection());
        assertEquals("Петров П.П.", saved.getFioTeacher());
        assertEquals("Ленина, д.1", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП2");
        verify(curriculumPlanEntryRepository, never()).deleteByAcademicYearAndClassId(any(), any());
        verify(manualLoadEntryRepository, never()).deleteByAcademicYearAndClassIds(any(), any());
        verify(schoolBuildingRepository, never()).save(any(SchoolBuilding.class));
    }

    @Test
    void updateOneKeepsSpWhenPhysicalSiteBelongsToSameSp() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);

        stubSuccessfulUpdate(entry, request, sp1, sp1);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(10L, saved.getSchoolBuildingId());
        assertEquals("Обручева, д.1", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП1");
    }

    @Test
    void updateOneKeepsSp1WhenPhysicalSiteBelongsToSp2() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp2Site = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 36L);

        stubSuccessfulUpdate(entry, request, sp1, sp2Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
        assertEquals("Марии Ульяновой, д.5А", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП1");
    }

    @Test
    void updateOneKeepsSp1WhenPhysicalSiteBelongsToSp3() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp3Site = schoolBuilding(77L, "СП3", "СП3", "Кравченко, д.14, корп.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 77L);

        stubSuccessfulUpdate(entry, request, sp1, sp3Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(77L, saved.getSchoolBuildingId());
        assertEquals("Кравченко, д.14, корп.1", saved.getCampusAddress());
    }

    @Test
    void changingPhysicalSiteDoesNotChangeClassSp() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        entry.setSchoolBuilding(schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1"));
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp2Site = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 36L);

        stubSuccessfulUpdate(entry, request, sp1, sp2Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
    }

    @Test
    void changingClassSpDoesNotChangePhysicalSiteAutomatically() {
        SchoolBuilding originalSite = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        entry.setSchoolBuilding(originalSite);
        SchoolBuilding sp3 = schoolBuilding(3L, "СП3", "СП3", "Кравченко, д.14, корп.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП3", 36L);

        stubSuccessfulUpdate(entry, request, sp3, originalSite);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП3", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
        assertEquals("Марии Ульяновой, д.5А", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП3");
    }

    @Test
    void updateOneRejectsUnknownSchoolBuildingId() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 999L);

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(2L)).thenReturn(Optional.of(teacher(2L, "Петров П.П.")));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.of(sp1));
        when(schoolBuildingRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateOne(42L, request));

        assertEquals("Площадка не найдена: 999", error.getMessage());
        verify(classroomLeadershipRepository, never()).save(any(ClassroomLeadershipEntry.class));
    }

    @Test
    void updateOnePersistsTeacherIdAndTeacherSnapshot() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        TeacherDirectoryEntry newTeacher = teacher(23L, "Новый Педагог");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);
        request.setTeacherId(23L);
        request.setFioTeacher("Старое ФИО из формы игнорируется");

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(23L)).thenReturn(Optional.of(newTeacher));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.of(sp1));
        when(schoolBuildingRepository.findById(10L)).thenReturn(Optional.of(sp1));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals(23L, saved.getTeacherId());
        assertEquals("Новый Педагог", saved.getFioTeacher());
    }

    @Test
    void updateOneRejectsUnknownTeacherId() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);
        request.setTeacherId(999L);

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateOne(42L, request));

        assertEquals("Педагог не найден: 999", error.getMessage());
        verify(classroomLeadershipRepository, never()).save(any(ClassroomLeadershipEntry.class));
    }

    @Test
    void replaceAllPersistsSelectedTeacherId() {
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        TeacherDirectoryEntry teacher = teacher(23L, "Новый Педагог");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);
        request.setId(null);
        request.setTeacherId(23L);
        request.setFioTeacher("ФИО из формы");

        when(classroomLeadershipRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(teacherDirectoryRepository.findById(23L)).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findById(10L)).thenReturn(Optional.of(sp1));
        when(classroomLeadershipRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ClassroomLeadershipEntry> saved = service.replaceAll(List.of(request));

        assertEquals(1, saved.size());
        assertEquals(23L, saved.get(0).getTeacherId());
        assertEquals("Новый Педагог", saved.get(0).getFioTeacher());
    }

    @Test
    void responseAccessorReturnsSelectedTeacherIdAfterUpdate() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        TeacherDirectoryEntry teacher = teacher(23L, "Новый Педагог");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);
        request.setTeacherId(23L);

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(23L)).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.of(sp1));
        when(schoolBuildingRepository.findById(10L)).thenReturn(Optional.of(sp1));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals(23L, saved.getTeacherId());
        assertEquals("Новый Педагог", saved.getFioTeacher());
    }

    @Test
    void importWithLegacyTeacherNameStoresResolvedTeacherId() throws Exception {
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        TeacherDirectoryEntry teacher = teacher(23L, "Новый Педагог");
        org.springframework.mock.web.MockMultipartFile file = classImportFile("СП1", "7-А", "Инженерный", "Новый Педагог", "Обручева, д.1");

        when(classroomLeadershipRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.of(sp1));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(sp1));
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of(teacher));
        when(teacherDirectoryRepository.findById(23L)).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findById(10L)).thenReturn(Optional.of(sp1));
        when(classroomLeadershipRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.importFromExcel("2026/2027", file);

        assertEquals("ok", result.get("status"));
        verify(classroomLeadershipRepository).saveAll(org.mockito.ArgumentMatchers.argThat(entries -> {
            List<ClassroomLeadershipEntry> saved = (List<ClassroomLeadershipEntry>) entries;
            return saved.size() == 1
                    && Long.valueOf(23L).equals(saved.get(0).getTeacherId())
                    && "Новый Педагог".equals(saved.get(0).getFioTeacher());
        }));
    }

    @Test
    void classesFrontendSendsTeacherIdWithFioTeacher() throws Exception {
        String js = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/classes.js"));

        assertTrue(js.contains("function teacherIdForName(fioTeacher)"));
        assertTrue(js.contains("const teacherId = teacherIdForName(teacherName);"));
        assertTrue(js.contains("teacherId,"));
        assertTrue(js.contains("fioTeacher: teacherName"));
        assertTrue(js.contains("Выберите педагога из справочника"));
    }

    @Test
    void dependencySummaryCountsLoadAndCurriculumBeforeDelete() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП3", "7-А");
        when(classroomLeadershipRepository.findByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А"))
                .thenReturn(Optional.of(entry));
        when(curriculumPlanEntryRepository.countClassTails("2026/2027", 42L)).thenReturn(5L);
        when(manualLoadEntryRepository.countClassTails("2026/2027", 42L)).thenReturn(3L);

        Map<String, Object> summary = service.dependencySummary("2026/2027", "СП3", "7-А");

        assertEquals(5L, summary.get("curriculumRows"));
        assertEquals(3L, summary.get("manualLoadRows"));
        assertEquals(8L, summary.get("totalRows"));
    }


    @Test
    void dependencySummaryByIdIgnoresMatchingLegacyStringsWithDifferentClassId() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП3", "7-А");
        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(curriculumPlanEntryRepository.countClassTails("2026/2027", 42L)).thenReturn(2L);
        when(manualLoadEntryRepository.countClassTails("2026/2027", 42L)).thenReturn(1L);

        Map<String, Object> summary = service.dependencySummary(42L, "2026/2027");

        assertEquals(42L, summary.get("classId"));
        assertEquals(2L, summary.get("curriculumRows"));
        assertEquals(1L, summary.get("manualLoadRows"));
    }


    @Test
    void updateBuildingScopeMovesClassToSelectedBuildingGroupAndDifferentPhysicalSite() {
        ClassroomLeadershipEntry entry = classEntry(9130L, "СП3", "7-М");
        BuildingGroup selectedGroup = buildingGroup(19L, "СП3 МЕХМАТ");
        BuildingGroup physicalSiteGroup = buildingGroup(12L, "СП3");
        SchoolBuilding targetSite = schoolBuilding(48L, "СП3|КРАВЧЕНКО", "СП3", "Кравченко, д.14, корп.1");
        targetSite.setBuildingGroup(physicalSiteGroup);
        ClassroomBuildingScopeUpdateRequest request = new ClassroomBuildingScopeUpdateRequest();
        request.setBuildingGroupId(19L);
        request.setSchoolBuildingId(48L);

        when(classroomLeadershipRepository.findById(9130L)).thenReturn(Optional.of(entry));
        when(schoolBuildingRepository.findById(48L)).thenReturn(Optional.of(targetSite));
        when(buildingGroupRepository.findById(19L)).thenReturn(Optional.of(selectedGroup));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);

        ClassroomLeadershipEntry saved = service.updateBuildingScope(9130L, request);

        assertEquals("СП3МЕХМАТ", saved.getNumberSchoolBuilding());
        assertEquals(48L, saved.getSchoolBuildingId());
        assertEquals("Кравченко, д.14, корп.1", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingScopeById(9130L, "СП3МЕХМАТ", 19L, 48L, "Кравченко, д.14, корп.1");
        verify(curriculumPlanEntryRepository).updateClassBuildingScope("2026/2027", 9130L, "СП3МЕХМАТ", 19L, "7-М");
        verify(manualLoadEntryRepository).updateClassBuildingScope("2026/2027", 9130L, "СП3МЕХМАТ", 19L, 48L, "7-М");
        verify(curriculumPlanEntryRepository, never()).deleteByAcademicYearAndClassId(any(), any());
        verify(manualLoadEntryRepository, never()).deleteByAcademicYearAndClassIds(any(), any());
    }

    @Test
    void updateBuildingScopeRejectsUnknownSchoolBuildingId() {
        ClassroomLeadershipEntry entry = classEntry(9130L, "СП3", "7-М");
        ClassroomBuildingScopeUpdateRequest request = new ClassroomBuildingScopeUpdateRequest();
        request.setSchoolBuildingId(999L);

        when(classroomLeadershipRepository.findById(9130L)).thenReturn(Optional.of(entry));
        when(schoolBuildingRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateBuildingScope(9130L, request));

        assertEquals("Площадка не найдена: 999", error.getMessage());
        verify(classroomLeadershipRepository, never()).save(any(ClassroomLeadershipEntry.class));
        verify(curriculumPlanEntryRepository, never()).updateClassBuildingScope(any(), any(), any(), any(), any());
        verify(manualLoadEntryRepository, never()).updateClassBuildingScope(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateBuildingScopeRejectsUnknownBuildingGroupIdFromRequest() {
        ClassroomLeadershipEntry entry = classEntry(9130L, "СП3", "7-М");
        BuildingGroup physicalSiteGroup = buildingGroup(19L, "СП3 МЕХМАТ");
        SchoolBuilding targetSite = schoolBuilding(48L, "СП3 МЕХМАТ|КРАВЧЕНКО", "СП3 мехмат", "Кравченко, д.14, корп.1");
        targetSite.setBuildingGroup(physicalSiteGroup);
        ClassroomBuildingScopeUpdateRequest request = new ClassroomBuildingScopeUpdateRequest();
        request.setSchoolBuildingId(48L);
        request.setBuildingGroupId(999L);

        when(classroomLeadershipRepository.findById(9130L)).thenReturn(Optional.of(entry));
        when(schoolBuildingRepository.findById(48L)).thenReturn(Optional.of(targetSite));
        when(buildingGroupRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateBuildingScope(9130L, request));

        assertEquals("Основной корпус не найден: 999", error.getMessage());
        verify(classroomLeadershipRepository, never()).save(any(ClassroomLeadershipEntry.class));
    }

    @Test
    void deleteByIdUsesOnlyClassIdForDependentRows() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));

        service.deleteOne(42L, "2026/2027");

        verify(curriculumPlanEntryRepository).deleteByAcademicYearAndClassId("2026/2027", 42L);
        verify(manualLoadEntryRepository).deleteByAcademicYearAndClassIds("2026/2027", List.of(42L));
        verify(classroomLeadershipRepository).delete(entry);
    }

    private void stubSuccessfulUpdate(ClassroomLeadershipEntry entry, ClassroomLeadershipEntryRequest request, SchoolBuilding spBuilding, SchoolBuilding physicalSite) {
        TeacherDirectoryEntry teacher = teacher(2L, "Петров П.П.");
        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findByCode(request.getNumberSchoolBuilding())).thenReturn(Optional.of(spBuilding));
        when(schoolBuildingRepository.findById(request.getSchoolBuildingId())).thenReturn(Optional.of(physicalSite));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);
    }

    private ClassroomLeadershipEntryRequest updateRequest(String numberSchoolBuilding, Long schoolBuildingId) {
        ClassroomLeadershipEntryRequest request = new ClassroomLeadershipEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding(numberSchoolBuilding);
        request.setSchoolBuildingId(schoolBuildingId);
        request.setClassName("7-А");
        request.setClassDirection("Инженерный");
        request.setTeacherId(2L);
        request.setFioTeacher("Петров П.П.");
        request.setCampusAddress("legacy ignored");
        request.setClassType("NORMAL");
        return request;
    }

    private org.springframework.mock.web.MockMultipartFile classImportFile(String building, String className, String direction, String teacher, String address) throws Exception {
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Классы");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Корпус");
            header.createCell(1).setCellValue("Класс");
            header.createCell(2).setCellValue("Направление класса");
            header.createCell(3).setCellValue("Классный руководитель");
            header.createCell(4).setCellValue("Адрес площадки (если отличается)");
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(building);
            row.createCell(1).setCellValue(className);
            row.createCell(2).setCellValue(direction);
            row.createCell(3).setCellValue(teacher);
            row.createCell(4).setCellValue(address);
            workbook.write(out);
            return new org.springframework.mock.web.MockMultipartFile(
                    "file",
                    "classes.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private BuildingGroup buildingGroup(Long id, String code) {
        BuildingGroup group = new BuildingGroup();
        group.setId(id);
        group.setCode(code);
        group.setName(code);
        return group;
    }

    private SchoolBuilding schoolBuilding(Long id, String code, String name, String address) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode(code);
        building.setName(name);
        building.setAddress(address);
        building.setManagerFio("Директор");
        return building;
    }

    private ClassroomLeadershipEntry classEntry(Long id, String building, String className) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setId(id);
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection("Универсальный");
        entry.setTeacher(teacher(1L, "Иванов И.И."));
        entry.setFioTeacher("Иванов И.И.");
        return entry;
    }
}
