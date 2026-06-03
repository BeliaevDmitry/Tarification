package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.school.personalLoad.dto.ManualLoadBulkRequest;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadHealthResponse;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
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

    private ManualLoadServiceImpl service;

    @BeforeEach
    void setUp() {
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
                salarySettingsRepository
        );
    }

    @Test
    void legacyCreateBulkForSingleAddressBuildingIsAllowed() {
        ManualLoadEntryRequest request = manualRequest("B1", "8-А", null);
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026"))
                .thenReturn(List.of(classEntry(1L, "B1", "8-А", "ул. Одна, 1")));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBulk(List.of(request));

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.Set.of("b1"));
        verify(manualLoadEntryRepository).saveAll(any());
    }

    @Test
    void legacyCreateBulkForMultiAddressBuildingIsRejectedWithoutDeleteOrSave() {
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", null);
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
        ManualLoadEntryRequest request = manualRequest("СП3", "4-Д", null);
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
        bulk.setClassIds(new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L, 9139L)));
        bulk.setRows(List.of(request));

        when(classroomLeadershipRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        classEntry(9119L, "СП3", "4-Д", "Марии Ульяновой, д.7"),
                        classEntry(9166L, "СП3", "4-И", "Марии Ульяновой, д.7"),
                        classEntry(9139L, "СП3", "4-К", "Марии Ульяновой, д.7")
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
        bulk.setClassIds(new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L)));
        bulk.setRows(List.of(request));

        when(classroomLeadershipRepository.findAllById(new java.util.LinkedHashSet<>(java.util.List.of(9119L, 9166L))))
                .thenReturn(List.of(
                        classEntry(9119L, "СП3", "4-Д", "Марии Ульяновой, д.7"),
                        classEntry(9166L, "СП3", "4-И", "Кравченко, д.14, корп.1")
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
        CurriculumPlanEntry explicitMeta = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMeta.setMetaGroupId(501L);
        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(assignedMeta));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, "Физика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(explicitMeta));
        when(tarifficationProcessingService.addingGroup(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processCurrentManualLoad("2025/2026");

        verify(curriculumPlanEntryRepository).findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, "Физика", EducationLevel.BASIC, StudyPeriod.YEAR);
        verify(curriculumPlanService, never()).findRule(anyString(), anyString(), anyString(), anyString(), any(), any());
    }


    @Test
    void importNewTemplateWithClassAndMetaGroupIdsPersistsFkRelations() throws Exception {
        CurriculumPlanEntry ordinaryRule = curriculumRow("СП1", "5-А", "Математика", 5);
        ordinaryRule.setClassId(101L);
        CurriculumPlanEntry explicitMetaRule = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMetaRule.setMetaGroupId(501L);
        MockMultipartFile file = editableImportFile(true, List.of(
                importRow("СП1", "5-А", "Математика", 5, 101L, null),
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, null, 501L)
        ));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 101L, "Математика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(ordinaryRule));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndMetaGroupIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 501L, "Физика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(explicitMetaRule));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.importWorkbook("2025/2026", file);

        assertEquals(2, saved.size());
        assertEquals(101L, saved.get(0).getClassId());
        assertNull(saved.get(0).getMetaGroupId());
        assertNull(saved.get(1).getClassId());
        assertEquals(501L, saved.get(1).getMetaGroupId());
    }

    @Test
    void importLegacyTemplateResolvesOrdinaryRowToClassId() throws Exception {
        CurriculumPlanEntry ordinaryRule = curriculumRow("СП1", "5-А", "Математика", 5);
        ordinaryRule.setClassId(101L);
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "5-А", "Математика", 5, null, null)
        ));
        when(classroomLeadershipRepository.findAllByAcademicYearAndNumberSchoolBuildingAndClassName("2025/2026", "СП1", "5-А"))
                .thenReturn(List.of(classEntry(101L, "СП1", "5-А", "ул. Первая, 1")));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 101L, "Математика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(ordinaryRule));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.importWorkbook("2025/2026", file);

        assertEquals(1, saved.size());
        assertEquals(101L, saved.get(0).getClassId());
        assertNull(saved.get(0).getMetaGroupId());
    }

    @Test
    void importLegacyTemplateResolvesExplicitMetaGroupRowToMetaGroupId() throws Exception {
        CurriculumPlanEntry explicitMetaRule = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMetaRule.setMetaGroupId(501L);
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, null, null)
        ));
        when(curriculumPlanEntryRepository.findAllByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", "СП1", "МГ:5 ФИЗИКА", "Физика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(List.of(explicitMetaRule));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.importWorkbook("2025/2026", file);

        assertEquals(1, saved.size());
        assertNull(saved.get(0).getClassId());
        assertEquals(501L, saved.get(0).getMetaGroupId());
    }

    @Test
    void importLegacyTemplateSkipsOrdinaryMetaGroupMemberAndSavesExplicitMetaGroupOnly() throws Exception {
        CurriculumPlanEntry metaMember = curriculumRow("СП1", "5-Б", "Физика", 3);
        metaMember.setClassId(102L);
        metaMember.setMetaGroup(true);
        CurriculumPlanEntry explicitMetaRule = curriculumRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3);
        explicitMetaRule.setMetaGroupId(501L);
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "5-Б", "Физика", 3, null, null),
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, null, null)
        ));
        when(classroomLeadershipRepository.findAllByAcademicYearAndNumberSchoolBuildingAndClassName("2025/2026", "СП1", "5-Б"))
                .thenReturn(List.of(classEntry(102L, "СП1", "5-Б", "ул. Вторая, 2")));
        when(curriculumPlanEntryRepository.findFirstByAcademicYearAndClassIdAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", 102L, "Физика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(Optional.of(metaMember));
        when(curriculumPlanEntryRepository.findAllByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameIgnoreCaseAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                "2025/2026", "СП1", "МГ:5 ФИЗИКА", "Физика", EducationLevel.BASIC, StudyPeriod.YEAR))
                .thenReturn(List.of(explicitMetaRule));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ManualLoadEntry> saved = service.importWorkbook("2025/2026", file);

        assertEquals(1, saved.size());
        assertNull(saved.get(0).getClassId());
        assertEquals(501L, saved.get(0).getMetaGroupId());
        assertEquals("МГ:5 ФИЗИКА", saved.get(0).getClassName());
    }

    @Test
    void importLegacyTemplateRejectsAmbiguousClassFallbackWithClearError() throws Exception {
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "5-А", "Математика", 5, null, null)
        ));
        when(classroomLeadershipRepository.findAllByAcademicYearAndNumberSchoolBuildingAndClassName("2025/2026", "СП1", "5-А"))
                .thenReturn(List.of(
                        classEntry(101L, "СП1", "5-А", "ул. Первая, 1"),
                        classEntry(202L, "СП1", "5-А", "ул. Вторая, 2")
                ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("неоднозначный class_id"));
    }


    @Test
    void importLegacyTemplateRejectsMissingMetaGroupFallbackWithClearError() throws Exception {
        MockMultipartFile file = editableImportFile(false, List.of(
                importRow("СП1", "МГ:5 ФИЗИКА", "Физика", 3, null, null)
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importWorkbook("2025/2026", file));

        assertTrue(error.getMessage().contains("не найдено FK-соответствие curriculum"));
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
            String[] headers = {"Учебный год", "Корпус", "Класс", "Предмет", "Группа", "Период", "С", "По", "Часы", "Уровень", "ФИО педагога", "ROW_KEY", "CLASS_ID", "META_GROUP_ID"};
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
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "load.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private ImportRow importRow(String building, String className, String subject, int load, Long classId, Long metaGroupId) {
        return new ImportRow(building, className, subject, load, classId, metaGroupId);
    }

    private record ImportRow(String building, String className, String subject, int load, Long classId, Long metaGroupId) {
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
        request.setClassName(className);
        request.setClassId(classId);
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
