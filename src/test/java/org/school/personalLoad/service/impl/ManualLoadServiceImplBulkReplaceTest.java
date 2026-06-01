package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.ManualLoadBulkRequest;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
