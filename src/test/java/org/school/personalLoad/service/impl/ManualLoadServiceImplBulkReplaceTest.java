package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void createBulkReplacesRowsForAffectedBuilding() {
        ManualLoadEntryRequest request = new ManualLoadEntryRequest();
        request.setAcademicYear("2025/2026");
        request.setFioTeacher("Иванов И.И.");
        request.setNumberSchoolBuilding("B1");
        request.setSubjectName("Алгебра");
        request.setClassName("8-А");
        request.setLoad(6);
        request.setEducationLevel(EducationLevel.BASIC);
        request.setLoadFromDate(LocalDate.of(2025, 9, 1));
        request.setLoadToDate(LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBulk(List.of(request));

        verify(manualLoadEntryRepository).deleteByAcademicYearAndBuildingCodes("2025/2026", java.util.Set.of("b1"));
        verify(manualLoadEntryRepository).saveAll(any());
    }

    @Test
    void exportFullWorkbookShowsTeacherAddressesInExtraInfo() throws Exception {
        ManualLoadEntry first = manualRow("Иванов И.И.", "СП1", "1-А", "Математика", 5);
        ManualLoadEntry second = manualRow("Иванов И.И.", "СП1", "2-А", "Математика", 4);

        ClassroomLeadershipEntry firstClass = classEntry("СП1", "1-А", "ул. Первая, 1");
        ClassroomLeadershipEntry secondClass = classEntry("СП1", "2-А", "ул. Вторая, 2");

        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(first, second));
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(firstClass, secondClass));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.empty());

        byte[] body = service.exportFullWorkbook("2025/2026");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            String extra = workbook.getSheet("СП1").getRow(1).getCell(8).getStringCellValue();
            assertTrue(extra.contains("Адреса: ул. Вторая, 2, ул. Первая, 1")
                    || extra.contains("Адреса: ул. Первая, 1, ул. Вторая, 2"));
            assertFalse(extra.contains("Корпуса:"));
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
            assertEquals("За часы", loadSheet.getRow(0).getCell(9).getStringCellValue());
            assertEquals("Классное руководство, руб.", loadSheet.getRow(0).getCell(10).getStringCellValue());
            assertEquals("Итого, руб.", loadSheet.getRow(0).getCell(11).getStringCellValue());
            double expectedHours = 40 * 25 * 9 * 2.8333333;
            double expectedLeadership = 500 * 25 + 5000;
            assertEquals(expectedHours, loadSheet.getRow(1).getCell(9).getNumericCellValue(), 0.01);
            assertEquals(expectedLeadership, loadSheet.getRow(1).getCell(10).getNumericCellValue(), 0.01);
            assertEquals(expectedHours + expectedLeadership, loadSheet.getRow(1).getCell(11).getNumericCellValue(), 0.01);

            var summarySheet = workbook.getSheet("Свод ЗП");
            assertEquals("Итого по комплексу", summarySheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(expectedHours + expectedLeadership, summarySheet.getRow(2).getCell(3).getNumericCellValue(), 0.01);
        }
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
