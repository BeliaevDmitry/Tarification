package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.school.personalLoad.dto.ManualLoadBulkRequest;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadHealthResponse;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.school.personalLoad.service.TarifficationProcessingService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.anyString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;
    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private ContingentSnapshotRepository contingentSnapshotRepository;
    @Mock
    private ContingentStudentRepository contingentStudentRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private SalarySettingsRepository salarySettingsRepository;
    @Mock
    private MetaGroupRepository metaGroupRepository;

    private ManualLoadServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(metaGroupRepository.findById(any()))
                .thenAnswer(invocation -> Optional.of(metaGroup(invocation.getArgument(0), 36L)));
        TeacherDirectoryEntry teacher = teacher(10L, "Иванов И.И.");
        TeacherDirectoryEntry vacancy = teacher(11L, "Вакансия");
        SubjectCatalogEntry algebra = subject(20L, "Алгебра");
        SubjectCatalogEntry math = subject(21L, "Математика");
        SubjectCatalogEntry ethics = subject(22L, "ОРКСЭ");
        SubjectCatalogEntry physics = subject(23L, "Физика");
        SubjectCatalogEntry odnknr = subject(24L, "ОДНКНР");
        lenient().when(teacherDirectoryRepository.findAll()).thenReturn(List.of(teacher, vacancy));
        lenient().when(teacherDirectoryRepository.findByFioTeacherIgnoreCase("Иванов И.И.")).thenReturn(Optional.of(teacher));
        lenient().when(teacherDirectoryRepository.findByFioTeacherIgnoreCase("Вакансия")).thenReturn(Optional.of(vacancy));
        lenient().when(teacherDirectoryRepository.findById(10L)).thenReturn(Optional.of(teacher));
        lenient().when(teacherDirectoryRepository.findById(11L)).thenReturn(Optional.of(vacancy));
        lenient().when(subjectCatalogRepository.findAll()).thenReturn(List.of(algebra, math, ethics, physics, odnknr));
        lenient().when(subjectCatalogRepository.findById(20L)).thenReturn(Optional.of(algebra));
        lenient().when(subjectCatalogRepository.findById(21L)).thenReturn(Optional.of(math));
        lenient().when(subjectCatalogRepository.findById(22L)).thenReturn(Optional.of(ethics));
        lenient().when(subjectCatalogRepository.findById(23L)).thenReturn(Optional.of(physics));
        lenient().when(subjectCatalogRepository.findById(24L)).thenReturn(Optional.of(odnknr));
        lenient().when(classroomLeadershipRepository.findById(any()))
                .thenAnswer(invocation -> Optional.of(classEntry(invocation.getArgument(0), "СП1", "7-А", "ул. Первая, 1")));

        service = new ManualLoadServiceImpl(
                manualLoadEntryRepository,
                tarifficationProcessingService,
                databaseService,
                curriculumPlanService,
                curriculumPlanEntryRepository,
                studyPeriodSettingService,
                teacherDirectoryRepository,
                subjectCatalogRepository,
                classroomLeadershipRepository,
                contingentSnapshotRepository,
                contingentStudentRepository,
                schoolBuildingRepository,
                salarySettingsRepository,
                metaGroupRepository
        );
    }

    @Test
    void createOrdinaryManualLoadPersistsTeacherIdAndSubjectIdWithSnapshots() {
        ManualLoadEntryRequest request = manualRequest("СП1", "7-А", 701L);
        request.setTeacherId(10L);
        request.setSubjectId(20L);
        when(manualLoadEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ManualLoadEntry saved = service.create(request);

        assertEquals(10L, saved.getTeacherId());
        assertEquals("Иванов И.И.", saved.getFioTeacher());
        assertEquals(20L, saved.getSubjectId());
        assertEquals("Алгебра", saved.getSubjectName());
        assertEquals(701L, saved.getClassId());
        assertNull(saved.getMetaGroupId());
    }

    @Test
    void createExplicitMetaGroupManualLoadPersistsMetaTeacherAndSubjectFks() {
        ManualLoadEntryRequest request = manualRequest("СП1", "МГ:5 ФИЗИКА", null);
        request.setTeacherId(10L);
        request.setSubjectId(23L);
        request.setSubjectName("Физика");
        request.setMetaGroupId(501L);
        when(metaGroupRepository.findById(501L)).thenReturn(Optional.of(metaGroup(501L, 36L)));
        when(manualLoadEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ManualLoadEntry saved = service.create(request);

        assertNull(saved.getClassId());
        assertEquals(501L, saved.getMetaGroupId());
        assertEquals(10L, saved.getTeacherId());
        assertEquals(23L, saved.getSubjectId());
    }

    @Test
    void buildingGroupCreateBulkForSingleAddressBuildingIsAllowed() {
        ManualLoadEntryRequest request = manualRequest("B1", "8-А", 1L);
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026"))
                .thenReturn(List.of(classEntry(1L, "B1", "8-А", "ул. Одна, 1")));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBulk(List.of(request));

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.Set.of("b1"));
        verify(manualLoadEntryRepository).saveAll(any());
    }

    @Test
    void buildingGroupCreateBulkForMultiAddressBuildingIsRejectedWithoutDeleteOrSave() {
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", 1L);
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026"))
                .thenReturn(List.of(
                        classEntry(1L, "СП3", "4-Д", "Кравченко, д.14, корп.1"),
                        classEntry(2L, "СП3", "4-И", "Марии Ульяновой, д.7")
                ));

        assertThrows(IllegalArgumentException.class, () -> service.createBulk(List.of(request)));

        verify(manualLoadEntryRepository, never())
                .deleteByAcademicYearAndBuildingCodes(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void explicitBuildingGroupCreateBulkForMultiAddressBuildingIsAllowed() {
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", 1L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_GROUP");
        bulk.setNumberSchoolBuilding("СП3");
        bulk.setRows(List.of(request));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBulk(bulk);

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.Set.of("сп3"));
        verify(manualLoadEntryRepository).saveAll(any());
    }

    @Test
    void createBulkForAddressScopeReplacesOnlySelectedClassIds() {
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", 9119L);

        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП3");
        bulk.setCampusAddress("Марии Ульяновой, д.7");
        bulk.setSchoolBuildingId(36L);
        bulk.setClassIds(new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L, 9139L)));
        bulk.setRows(List.of(request));

        when(classroomLeadershipRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        classEntry(9119L, "СП3", "4-Д", "Марии Ульяновой, д.7", 36L),
                        classEntry(9166L, "СП3", "4-И", "Марии Ульяновой, д.7", 36L),
                        classEntry(9139L, "СП3", "4-К", "Марии Ульяновой, д.7", 36L)
                ));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBulk(bulk);

        verify(manualLoadEntryRepository).deleteByAcademicYearAndClassIds(
                "2025/2026",
                new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L, 9139L))
        );
        verify(manualLoadEntryRepository, org.mockito.Mockito.never())
                .deleteByAcademicYearAndBuildingCodes(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(manualLoadEntryRepository).saveAll(any());
    }

    @Test
    void createBulkForAddressScopeRejectsClassIdFromAnotherAddress() {
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", 9119L);

        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП3");
        bulk.setCampusAddress("Марии Ульяновой, д.7");
        bulk.setSchoolBuildingId(36L);
        bulk.setClassIds(new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L)));
        bulk.setRows(List.of(request));

        when(classroomLeadershipRepository.findAllById(any()))
                .thenReturn(List.of(
                        classEntry(9119L, "СП3", "4-Д", "Марии Ульяновой, д.7", 36L),
                        classEntry(9166L, "СП3", "4-И", "Кравченко, д.14, корп.1", 38L)
                ));

        assertThrows(IllegalArgumentException.class, () -> service.createBulk(bulk));

        verify(manualLoadEntryRepository, never()).deleteByAcademicYearAndClassIds(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void deleteMultiAddressBuildingRequiresExplicitBuildingGroupScope() {
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026"))
                .thenReturn(List.of(
                        classEntry(1L, "СП3", "4-Д", "Кравченко, д.14, корп.1"),
                        classEntry(2L, "СП3", "4-И", "Марии Ульяновой, д.7")
                ));

        assertThrows(IllegalArgumentException.class, () -> service.clearByBuilding("2025/2026", "СП3"));

        verify(manualLoadEntryRepository, never())
                .deleteByAcademicYearAndBuildingCodes(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        service.clearByBuilding("2025/2026", "СП3", "BUILDING_GROUP");

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.List.of("сп3"));
    }


    @Test
    void exportTemplateIncludesStandaloneClassOnceAndSkipsOrdinaryMetaMemberRows() throws Exception {
        CurriculumPlanEntry standalone = curriculumRow("СП1", "5-А", "Математика", 5);
        standalone.setClassId(101L);
        CurriculumPlanEntry metaMember = curriculumRow("СП1", "5-Б", "Физика", 3);
        metaMember.setClassId(102L);
        metaMember.setMetaGroup(true);
        CurriculumPlanEntry explicitMeta = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMeta.setMetaGroupId(501L);

        when(curriculumPlanService.findAll("2025/2026")).thenReturn(List.of(standalone, metaMember, explicitMeta));
        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        when(studyPeriodSettingService.resolveDateRange(anyString(), anyString(), any()))
                .thenReturn(new StudyPeriodSettingService.DateRange(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31)));

        byte[] body = service.exportWorkbook("2025/2026");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("LOAD_EDITABLE");
            assertEquals(2, sheet.getLastRowNum());
            assertEquals("Математика", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals(101L, (long) sheet.getRow(1).getCell(12).getNumericCellValue());
            assertEquals("МГ:5 ФИЗИКА", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals(501L, (long) sheet.getRow(2).getCell(13).getNumericCellValue());
            assertFalse(sheet.getRow(1).getCell(2).getStringCellValue().equals("5-Б"));
            assertEquals("CLASS_ID", sheet.getRow(0).getCell(12).getStringCellValue());
            assertEquals("META_GROUP_ID", sheet.getRow(0).getCell(13).getStringCellValue());
        }
    }

    @Test
    void explicitMetaGroupLoadRequestIsSavedByMetaGroupIdWithoutClassId() {
        ManualLoadEntryRequest request = manualRequest("СП1", "МГ:5 ФИЗИКА", null);
        request.setMetaGroupId(501L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_GROUP");
        bulk.setNumberSchoolBuilding("СП1");
        bulk.setRows(List.of(request));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.createBulk(bulk);

        assertEquals(1, saved.size());
        assertEquals(501L, saved.get(0).getMetaGroupId());
        assertEquals(null, saved.get(0).getClassId());
    }

    @Test
    void statsAndHealthCountExplicitMetaGroupPlanWithoutOrdinaryMemberDuplicate() {
        CurriculumPlanEntry metaMember = curriculumRow("СП1", "5-Б", "Физика", 3);
        metaMember.setClassId(102L);
        metaMember.setMetaGroup(true);
        CurriculumPlanEntry explicitMeta = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMeta.setMetaGroupId(501L);
        ManualLoadEntry assignedMeta = manualRow("Иванов И.И.", "СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        assignedMeta.setMetaGroupId(501L);

        when(curriculumPlanService.findAll("2025/2026", "СП1")).thenReturn(List.of(metaMember, explicitMeta));
        when(manualLoadEntryRepository.findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase("2025/2026", "СП1"))
                .thenReturn(List.of(assignedMeta));
        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setSubjectName("Физика");
        subject.setSubjectAreaName("Естественные науки");
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject));

        ManualLoadStatsResponse stats = service.buildStats("2025/2026", "СП1", 0, 20);
        ManualLoadHealthResponse health = service.buildHealth("2025/2026", "СП1");

        assertEquals(3, stats.getTotalPlanned());
        assertEquals(3, stats.getTotalAssigned());
        assertEquals(0, stats.getTotalUnassigned());
        assertEquals(0, health.getUnassignedHours());
    }

    @Test
    void validateMetaGroupLoadUsesMetaGroupIdRule() {
        ManualLoadEntry assignedMeta = manualRow("Иванов И.И.", "СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        assignedMeta.setMetaGroupId(501L);
        assignedMeta.setSubject(subject(23L, "Физика"));
        CurriculumPlanEntry explicitMeta = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMeta.setMetaGroupId(501L);
        explicitMeta.setSubject(subject(23L, "Физика"));
        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(assignedMeta));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, 23L, EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(explicitMeta));
        when(tarifficationProcessingService.addingGroup(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processCurrentManualLoad("2025/2026");

        verify(curriculumPlanEntryRepository).findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, 23L, EducationLevel.BASIC, StudyPeriod.YEAR);
        verify(curriculumPlanService, never()).findRule(anyString(), anyString(), anyString(), anyString(), any(), any());
    }



    @Test
    void addressScopeFindsSp1ClassOnPhysicalSp2BySchoolBuildingId() {
        ManualLoadEntry row = manualRow("Иванов И.И.", "СП1", "7-А", "Алгебра", 6);
        row.setClassId(701L);
        when(manualLoadEntryRepository.findAllByAcademicYearAndSchoolBuildingId("2025/2026", 36L))
                .thenReturn(List.of(row));

        List<ManualLoadEntry> result = service.findAll("2025/2026", "СП2", "Марии Ульяновой, д.5А", 36L);

        assertEquals(1, result.size());
        assertEquals("СП1", result.get(0).getNumberSchoolBuilding());
        assertEquals(701L, result.get(0).getClassId());
        verify(manualLoadEntryRepository).findAllByAcademicYearAndSchoolBuildingId("2025/2026", 36L);
    }

    @Test
    void addressScopeFindsSp1ClassOnPhysicalSp3BySchoolBuildingId() {
        ManualLoadEntry row = manualRow("Иванов И.И.", "СП1", "10-А", "Алгебра", 6);
        row.setClassId(1001L);
        when(manualLoadEntryRepository.findAllByAcademicYearAndSchoolBuildingId("2025/2026", 38L))
                .thenReturn(List.of(row));

        List<ManualLoadEntry> result = service.findAll("2025/2026", "СП3", "Кравченко, д.14, корп.1", 38L);

        assertEquals(1, result.size());
        assertEquals("СП1", result.get(0).getNumberSchoolBuilding());
        assertEquals("10-А", result.get(0).getClassName());
        verify(manualLoadEntryRepository).findAllByAcademicYearAndSchoolBuildingId("2025/2026", 38L);
    }

    @Test
    void buildingGroupScopeStillFiltersByOrganizationalSp() {
        ManualLoadEntry row = manualRow("Иванов И.И.", "СП1", "7-А", "Алгебра", 6);
        when(manualLoadEntryRepository.findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase("2025/2026", "СП1"))
                .thenReturn(List.of(row));

        List<ManualLoadEntry> result = service.findAll("2025/2026", "СП1", null, null);

        assertEquals(1, result.size());
        assertEquals("СП1", result.get(0).getNumberSchoolBuilding());
        verify(manualLoadEntryRepository).findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase("2025/2026", "СП1");
    }

    @Test
    void addressScopeBulkSaveAcceptsSp1ClassOnSelectedPhysicalSite() {
        ManualLoadEntryRequest request = manualRequest("СП1", "7-А", 701L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП2");
        bulk.setCampusAddress("Марии Ульяновой, д.5А");
        bulk.setSchoolBuildingId(36L);
        bulk.setClassIds(new java.util.LinkedHashSet<>(List.of(701L)));
        bulk.setRows(List.of(request));
        ClassroomLeadershipEntry classroom = classEntry(701L, "СП1", "7-А", "Марии Ульяновой, д.5А");
        classroom.setSchoolBuilding(schoolBuilding(36L, "СП2", "Марии Ульяновой, д.5А"));
        when(classroomLeadershipRepository.findAllById(any())).thenReturn(List.of(classroom));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.createBulk(bulk);

        assertEquals(1, saved.size());
        assertEquals("СП1", saved.get(0).getNumberSchoolBuilding());
        assertEquals(701L, saved.get(0).getClassId());
        verify(manualLoadEntryRepository).deleteByAcademicYearAndClassIds("2025/2026", java.util.Set.of(701L));
    }

    @Test
    void addressScopeBulkSaveRejectsClassFromAnotherPhysicalSite() {
        ManualLoadEntryRequest request = manualRequest("СП1", "10-А", 1001L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2025/2026");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП2");
        bulk.setSchoolBuildingId(36L);
        bulk.setClassIds(new java.util.LinkedHashSet<>(List.of(1001L)));
        bulk.setRows(List.of(request));
        ClassroomLeadershipEntry classroom = classEntry(1001L, "СП1", "10-А", "Кравченко, д.14, корп.1");
        classroom.setSchoolBuilding(schoolBuilding(38L, "СП3", "Кравченко, д.14, корп.1"));
        when(classroomLeadershipRepository.findAllById(any())).thenReturn(List.of(classroom));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createBulk(bulk));

        assertTrue(error.getMessage().contains("schoolBuildingId=36"));
    }

    @Test
    void clearAddressScopeDeletesOrdinaryRowsBySchoolBuildingId() {
        service.clearBySchoolBuilding("2025/2026", 36L);

        verify(manualLoadEntryRepository).deleteByAcademicYearAndSchoolBuildingId("2025/2026", 36L);
    }


    @Test
    void addressScopeFindsExplicitMetaGroupOnSelectedPhysicalSite() {
        ManualLoadEntry ordinary = manualRow("Иванов И.И.", "СП1", "7-А", "Алгебра", 6);
        ordinary.setClassId(701L);
        ManualLoadEntry explicitMeta = manualRow("Петров П.П.", "СП2", "МГ:4 4ЦЧ-СВЕТСКАЯ", "ОДНКНР", 1);
        explicitMeta.setClassId(null);
        explicitMeta.setMetaGroupId(4L);
        when(manualLoadEntryRepository.findAllByAcademicYearAndSchoolBuildingId("2026/2027", 36L))
                .thenReturn(List.of(ordinary, explicitMeta));

        List<ManualLoadEntry> result = service.findAll("2026/2027", "СП2", "Марии Ульяновой, д.5А", 36L);

        assertEquals(2, result.size());
        assertEquals(701L, result.get(0).getClassId());
        assertEquals(4L, result.get(1).getMetaGroupId());
        assertNull(result.get(1).getClassId());
    }

    @Test
    void addressScopeBulkSaveReplacesExplicitMetaGroupRowsByMetaGroupId() {
        ManualLoadEntryRequest request = manualRequest("СП2", "МГ:4 4ЦЧ-СВЕТСКАЯ", null);
        request.setAcademicYear("2026/2027");
        request.setSubjectName("ОДНКНР");
        request.setLoad(1);
        request.setMetaGroupId(4L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2026/2027");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП2");
        bulk.setSchoolBuildingId(36L);
        bulk.setRows(List.of(request));
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(metaGroup(4L, 36L)));
        when(metaGroupRepository.findAllById(java.util.Set.of(4L))).thenReturn(List.of(metaGroup(4L, 36L)));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.createBulk(bulk);

        assertEquals(1, saved.size());
        assertNull(saved.get(0).getClassId());
        assertEquals(4L, saved.get(0).getMetaGroupId());
        verify(manualLoadEntryRepository).deleteByAcademicYearAndMetaGroupIds("2026/2027", java.util.Set.of(4L));
        verify(manualLoadEntryRepository, never()).deleteByAcademicYearAndBuildingCodes(anyString(), any());
    }

    @Test
    void explicitMetaGroupWithoutPhysicalSiteFailsWithClearMessage() {
        ManualLoadEntryRequest request = manualRequest("СП2", "МГ:4 4ЦЧ-СВЕТСКАЯ", null);
        request.setMetaGroupId(4L);
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(metaGroup(4L, null)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createBulk(List.of(request)));

        assertTrue(error.getMessage().contains("Для метагруппы не выбрана физическая площадка проведения"));
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void addressScopeBulkSaveRejectsMetaGroupFromAnotherPhysicalSite() {
        ManualLoadEntryRequest request = manualRequest("СП2", "МГ:4 4ЦЧ-СВЕТСКАЯ", null);
        request.setAcademicYear("2026/2027");
        request.setMetaGroupId(4L);
        ManualLoadBulkRequest bulk = new ManualLoadBulkRequest();
        bulk.setAcademicYear("2026/2027");
        bulk.setScopeType("BUILDING_ADDRESS");
        bulk.setNumberSchoolBuilding("СП2");
        bulk.setSchoolBuildingId(36L);
        bulk.setRows(List.of(request));
        when(metaGroupRepository.findById(4L)).thenReturn(Optional.of(metaGroup(4L, 38L)));
        when(metaGroupRepository.findAllById(java.util.Set.of(4L))).thenReturn(List.of(metaGroup(4L, 38L)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createBulk(bulk));

        assertTrue(error.getMessage().contains("metaGroupIds do not belong to selected schoolBuildingId=36"));
        verify(manualLoadEntryRepository, never()).deleteByAcademicYearAndMetaGroupIds(anyString(), any());
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void importNewTemplateWithRequiredFkColumnsPersistsOrdinaryAndMetaGroupRows() throws Exception {
        CurriculumPlanEntry ordinaryRule = curriculumRow("СП1", "5-А", "Математика", 5);
        ordinaryRule.setClassId(101L);
        ordinaryRule.setSubject(subject(21L, "Математика"));
        CurriculumPlanEntry explicitMetaRule = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMetaRule.setMetaGroupId(501L);
        explicitMetaRule.setSubject(subject(23L, "Физика"));
        MockMultipartFile file = editableImportFile(true, List.of(
                importRow("СП1", "5-А", "Математика", 5, 101L, null, 10L, 21L),
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, null, 501L, 10L, 23L)
        ));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 101L, 21L, EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(ordinaryRule));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, 23L, EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(explicitMetaRule));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.importWorkbook("2025/2026", file);

        assertEquals(2, saved.size());
        assertEquals(101L, saved.get(0).getClassId());
        assertNull(saved.get(0).getMetaGroupId());
        assertEquals(10L, saved.get(0).getTeacherId());
        assertEquals(21L, saved.get(0).getSubjectId());
        assertNull(saved.get(1).getClassId());
        assertEquals(501L, saved.get(1).getMetaGroupId());
        assertEquals(10L, saved.get(1).getTeacherId());
        assertEquals(23L, saved.get(1).getSubjectId());
    }

    @Test
    void importOldTemplateWithoutFkColumnsIsRejectedWithClearMessage() throws Exception {
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "5-А", "Математика", 5, null, null)
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("Файл создан в старом формате"));
        assertTrue(error.getMessage().contains("Выгрузите новый шаблон нагрузки"));
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void importNewTemplateRejectsUnknownForeignKey() throws Exception {
        MockMultipartFile file = editableImportFile(true, List.of(
                importRow("СП1", "5-А", "Математика", 5, 101L, null, 999L, 21L)
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("teacher_id не найден"));
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void importNewTemplateRejectsRowsWithBothClassIdAndMetaGroupId() throws Exception {
        MockMultipartFile file = editableImportFile(true, List.of(
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, 101L, 501L, 10L, 23L)
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("нельзя одновременно указывать CLASS_ID и META_GROUP_ID"));
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void importNewTemplateRejectsOrdinaryMetaGroupMemberRow() throws Exception {
        CurriculumPlanEntry metaMember = curriculumRow("СП1", "5-Б", "Физика", 3);
        metaMember.setClassId(102L);
        metaMember.setSubject(subject(23L, "Физика"));
        metaMember.setMetaGroup(true);
        MockMultipartFile file = editableImportFile(true, List.of(
                importRow("СП1", "5-Б", "Физика", 3, 102L, null, 10L, 23L)
        ));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubject_IdAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 102L, 23L, EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(metaMember));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("ordinary member row"));
        verify(manualLoadEntryRepository, never()).saveAll(any());
    }

    @Test
    void exportFullWorkbookShowsRowBuildingAddressAndLeadershipOnly() throws Exception {
        ManualLoadEntry first = manualRow("Иванов И.И.", "СП1", "1-А", "Математика", 5);
        ManualLoadEntry second = manualRow("Иванов И.И.", "СП2", "2-А", "Математика", 4);

        ClassroomLeadershipEntry firstClass = classEntry("СП1", "1-А", "ул. Первая, 1");
        firstClass.setFioTeacher("Иванов И.И.");
        ClassroomLeadershipEntry secondClass = classEntry("СП2", "2-А", "ул. Вторая, 2");

        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(first, second));
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(firstClass, secondClass));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.empty());

        byte[] body = service.exportFullWorkbook("2025/2026");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("СП1");
            assertEquals("Корпус", sheet.getRow(0).getCell(8).getStringCellValue());
            assertEquals("Классное руководство", sheet.getRow(0).getCell(9).getStringCellValue());
            assertTrue(sheet.getRow(1).getCell(8).getStringCellValue().contains("ул. Первая, 1"));
            assertEquals("1-А", sheet.getRow(1).getCell(9).getStringCellValue());
            assertEquals("СП2", sheet.getRow(2).getCell(8).getStringCellValue().split("\n")[0]);
            assertNotNull(workbook.getSheet("Все педагоги"));
        }
    }

    @Test
    void exportFullWorkbookWithSalaryAddsSalaryColumnsAndSummarySheet() throws Exception {
        ManualLoadEntry first = manualRow("Иванов И.И.", "СП1", "1-А", "Математика", 5);
        ManualLoadEntry second = manualRow("Иванов И.И.", "СП1", "2-А", "Математика", 4);
        ClassroomLeadershipEntry firstClass = classEntry("СП1", "1-А", "ул. Первая, 1");
        firstClass.setFioTeacher("Иванов И.И.");

        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setSubjectName("Математика");
        subject.setSubjectType(SubjectType.CORE);
        subject.setSubjectCoefficient(java.math.BigDecimal.ONE);

        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(first, second));
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject));
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(firstClass));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        SalarySettings settings = new SalarySettings();
        settings.setStudentHourRate(java.math.BigDecimal.valueOf(40));
        when(contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.empty());
        when(salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)).thenReturn(Optional.of(settings));

        byte[] body = service.exportFullWorkbookWithSalary("2025/2026");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var loadSheet = workbook.getSheet("СП1");
            assertEquals("За часы", loadSheet.getRow(0).getCell(10).getStringCellValue());
            assertEquals("Классное руководство, руб.", loadSheet.getRow(0).getCell(11).getStringCellValue());
            assertEquals("Итого, руб.", loadSheet.getRow(0).getCell(12).getStringCellValue());
            assertEquals("ГОД", loadSheet.getRow(1).getCell(6).getStringCellValue());
            double expectedHours = 40 * 30 * 9 * 2.8333333;
            double expectedLeadership = 500 * 30 + 5000;
            assertEquals(expectedHours, loadSheet.getRow(1).getCell(10).getNumericCellValue(), 0.01);
            assertEquals(expectedLeadership, loadSheet.getRow(1).getCell(11).getNumericCellValue(), 0.01);
            assertEquals(expectedHours + expectedLeadership, loadSheet.getRow(1).getCell(12).getNumericCellValue(), 0.01);

            var summarySheet = workbook.getSheet("Свод ЗП");
            assertEquals("Итого по комплексу", summarySheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(expectedHours + expectedLeadership, summarySheet.getRow(2).getCell(3).getNumericCellValue(), 0.01);
        }
    }

    @Test
    void exportFullWorkbookWithSalaryShowsHalfYearTotalsAndFirstHalfMoney() throws Exception {
        ManualLoadEntry year = manualRow("Петров П.П.", "СП1", "3-А", "Математика", 10);
        ManualLoadEntry firstHalf = manualRow("Петров П.П.", "СП1", "3-А", "Математика", 1);
        firstHalf.setStudyPeriod(StudyPeriod.H1);
        ManualLoadEntry secondHalf = manualRow("Петров П.П.", "СП1", "3-А", "Математика", 2);
        secondHalf.setStudyPeriod(StudyPeriod.H2);

        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setSubjectName("Математика");
        subject.setSubjectType(SubjectType.CORE);
        subject.setSubjectCoefficient(java.math.BigDecimal.ONE);

        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(year, firstHalf, secondHalf));
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject));
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        SalarySettings settings = new SalarySettings();
        settings.setStudentHourRate(java.math.BigDecimal.valueOf(40));
        when(contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.empty());
        when(salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)).thenReturn(Optional.of(settings));

        byte[] body = service.exportFullWorkbookWithSalary("2025/2026");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var loadSheet = workbook.getSheet("СП1");
            assertEquals("11/12", loadSheet.getRow(1).getCell(7).getStringCellValue());
            double expectedFirstHalfHoursMoney = 40 * 30 * 11 * 2.8333333;
            assertEquals(expectedFirstHalfHoursMoney, loadSheet.getRow(1).getCell(10).getNumericCellValue(), 0.01);
            assertEquals(expectedFirstHalfHoursMoney, loadSheet.getRow(1).getCell(12).getNumericCellValue(), 0.01);
        }
    }


    private MockMultipartFile editableImportFile(boolean includeFkColumns, List<ImportRow> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("LOAD_EDITABLE");
            var header = sheet.createRow(0);
            String[] headers = {"Учебный год", "Корпус", "Класс", "Предмет", "Группа", "Период", "С", "По", "Часы", "Уровень", "ФИО педагога", "ROW_KEY", "CLASS_ID", "META_GROUP_ID", "TEACHER_ID", "SUBJECT_ID"};
            int headerCount = includeFkColumns ? headers.length : 12;
            for (int i = 0; i < headerCount; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                ImportRow source = rows.get(i);
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("2025/2026");
                row.createCell(1).setCellValue(source.building());
                row.createCell(2).setCellValue(source.className());
                row.createCell(3).setCellValue(source.subject());
                row.createCell(4).setCellValue("");
                row.createCell(5).setCellValue("YEAR");
                row.createCell(6).setCellValue("2025-09-01");
                row.createCell(7).setCellValue("2026-05-31");
                row.createCell(8).setCellValue(source.load());
                row.createCell(9).setCellValue("BASIC");
                row.createCell(10).setCellValue("Вакансия");
                row.createCell(11).setCellValue("row-" + i);
                if (includeFkColumns) {
                    if (source.classId() != null) row.createCell(12).setCellValue(source.classId());
                    if (source.metaGroupId() != null) row.createCell(13).setCellValue(source.metaGroupId());
                    if (source.teacherId() != null) row.createCell(14).setCellValue(source.teacherId());
                    if (source.subjectId() != null) row.createCell(15).setCellValue(source.subjectId());
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "load.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private ImportRow importRow(String building, String className, String subject, int load, Long classId, Long metaGroupId) {
        return importRow(building, className, subject, load, classId, metaGroupId, null, null);
    }

    private ImportRow importRow(String building, String className, String subject, int load, Long classId, Long metaGroupId, Long teacherId, Long subjectId) {
        return new ImportRow(building, className, subject, load, classId, metaGroupId, teacherId, subjectId);
    }

    private record ImportRow(String building, String className, String subject, int load, Long classId, Long metaGroupId, Long teacherId, Long subjectId) {
    }

    private CurriculumPlanEntry curriculumRow(String building, String className, String subject, int hours) {
        CurriculumPlanEntry row = new CurriculumPlanEntry();
        row.setAcademicYear("2025/2026");
        row.setNumberSchoolBuilding(building);
        row.setClassName(className);
        row.setSubjectName(subject);
        row.setPlannedHours(BigDecimal.valueOf(hours));
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        row.setSubgroupRequired(false);
        row.setSubgroupCount(0);
        row.setDeprecated(false);
        return row;
    }

    private ManualLoadEntryRequest manualRequest(String building, String className, Long classId) {
        ManualLoadEntryRequest request = new ManualLoadEntryRequest();
        request.setAcademicYear("2025/2026");
        request.setFioTeacher("Иванов И.И.");
        request.setNumberSchoolBuilding(building);
        request.setSubjectName("Алгебра");
        request.setSubjectId(20L);
        request.setClassName(className);
        request.setClassId(classId);
        request.setTeacherId(10L);
        request.setLoad(6);
        request.setEducationLevel(EducationLevel.BASIC);
        request.setLoadFromDate(LocalDate.of(2025, 9, 1));
        request.setLoadToDate(LocalDate.of(2026, 5, 31));
        return request;
    }

    private ManualLoadEntry manualRow(String fio, String building, String className, String subject, int load) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setAcademicYear("2025/2026");
        row.setFioTeacher(fio);
        row.setNumberSchoolBuilding(building);
        row.setClassName(className);
        row.setSubjectName(subject);
        row.setLoad(load);
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        return row;
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private SubjectCatalogEntry subject(Long id, String name) {
        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setId(id);
        subject.setSubjectName(name);
        subject.setSubjectType(SubjectType.CORE);
        subject.setSubjectCoefficient(java.math.BigDecimal.ONE);
        return subject;
    }

    private MetaGroup metaGroup(Long id, Long schoolBuildingId) {
        MetaGroup metaGroup = new MetaGroup();
        metaGroup.setId(id);
        metaGroup.setNumberSchoolBuilding("СП1");
        metaGroup.setParallel(5);
        metaGroup.setName("5 ФИЗИКА");
        metaGroup.setClassType("NORMAL");
        if (schoolBuildingId != null) {
            metaGroup.setSchoolBuilding(schoolBuilding(schoolBuildingId, "СП2", "Марии Ульяновой, д.5А"));
        }
        return metaGroup;
    }

    private SchoolBuilding schoolBuilding(Long id, String code, String address) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode(code);
        building.setName(code);
        building.setManagerFio("Ответственный");
        building.setAddress(address);
        return building;
    }

    private ClassroomLeadershipEntry classEntry(Long id, String building, String className, String address, Long schoolBuildingId) {
        ClassroomLeadershipEntry entry = classEntry(id, building, className, address);
        entry.setSchoolBuilding(schoolBuilding(schoolBuildingId, building, address));
        return entry;
    }

    private ClassroomLeadershipEntry classEntry(Long id, String building, String className, String address) {
        ClassroomLeadershipEntry entry = classEntry(building, className, address);
        entry.setId(id);
        return entry;
    }

    private ClassroomLeadershipEntry classEntry(String building, String className, String address) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setAcademicYear("2025/2026");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection("общеобразовательный");
        entry.setFioTeacher("Классный руководитель");
        entry.setCampusAddress(address);
        return entry;
    }

}
