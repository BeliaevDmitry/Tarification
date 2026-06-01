package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurriculumImportServiceImplParallelExportTest {

    @Mock
    private CurriculumExcelParser parser;
    @Mock
    private CurriculumPlanEntryRepository curriculumRepository;
    @Mock
    private ClassroomLeadershipRepository classroomRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadRepository;
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;

    private CurriculumImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CurriculumImportServiceImpl(
                parser,
                curriculumRepository,
                classroomRepository,
                manualLoadRepository,
                teacherRepository,
                subjectCatalogRepository,
                studyPeriodSettingService
        );
    }

    @Test
    void exportParallelWorkbookBuildsReadableParallelSheet() throws Exception {
        CurriculumPlanEntry h1 = entry("СП1", "7-А", "Алгебра", StudyPeriod.H1, 3);
        CurriculumPlanEntry h2 = entry("СП1", "7-А", "Алгебра", StudyPeriod.H2, 4);
        CurriculumPlanEntry year = entry("СП1", "7-Б", "Алгебра", StudyPeriod.YEAR, 5);
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(h1, h2, year));
        when(classroomRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(
                classroom("СП1", "7-А", "Математический", "Иванов И.И."),
                classroom("СП1", "7-Б", "Общеобразовательный", "Петров П.П.")
        ));
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject("Алгебра", "Математика и информатика")));

        byte[] body = service.exportParallelWorkbook("2026/2027");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("7 параллель");
            assertNotNull(sheet);
            assertEquals("Учебный план по 7 параллели, 2026/2027", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Часть учебного плана", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Предмет", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Период обучения", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("1П/2П", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals("", sheet.getRow(2).getCell(3).getStringCellValue());
            assertEquals("Математический", sheet.getRow(3).getCell(2).getStringCellValue());
            assertEquals("Иванов И.И.", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("7-А", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("Обязательная часть", sheet.getRow(6).getCell(0).getStringCellValue());
            assertEquals("Математика и информатика", sheet.getRow(7).getCell(0).getStringCellValue());
            assertEquals("Алгебра", sheet.getRow(7).getCell(1).getStringCellValue());
            assertEquals("3/4", sheet.getRow(7).getCell(2).getStringCellValue());
            assertEquals("5", sheet.getRow(7).getCell(3).getStringCellValue());
        }
    }

    private CurriculumPlanEntry entry(String building, String className, String subject, StudyPeriod period, int hours) {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setSubjectName(subject);
        entry.setStudyPeriod(period);
        entry.setCurriculumPart(CurriculumPart.CORE);
        entry.setEducationLevel(EducationLevel.BASIC);
        entry.setStage(CurriculumStage.OOO);
        entry.setPlannedHours(BigDecimal.valueOf(hours));
        entry.setSubgroupRequired(false);
        entry.setSubgroupCount(0);
        return entry;
    }

    private ClassroomLeadershipEntry classroom(String building, String className, String direction, String teacher) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection(direction);
        entry.setFioTeacher(teacher);
        entry.setCampusAddress("Адрес");
        return entry;
    }

    private SubjectCatalogEntry subject(String subjectName, String area) {
        SubjectCatalogEntry entry = new SubjectCatalogEntry();
        entry.setSubjectName(subjectName);
        entry.setSubjectType(SubjectType.CORE);
        entry.setSubjectAreaName(area);
        return entry;
    }
}
