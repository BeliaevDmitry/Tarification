package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
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
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
            assertEquals("Итого основная часть", sheet.getRow(8).getCell(0).getStringCellValue());
            assertEquals("3/4", sheet.getRow(8).getCell(2).getStringCellValue());
            assertEquals("5", sheet.getRow(8).getCell(3).getStringCellValue());
            assertEquals("Итого формируемая часть", sheet.getRow(9).getCell(0).getStringCellValue());
            assertEquals("", sheet.getRow(9).getCell(2).getStringCellValue());
            assertEquals("Итого основная+формируемая часть", sheet.getRow(10).getCell(0).getStringCellValue());
            assertEquals("3/4", sheet.getRow(10).getCell(2).getStringCellValue());
            assertEquals("Максимальная нагрузка", sheet.getRow(11).getCell(0).getStringCellValue());
            assertEquals("32", sheet.getRow(11).getCell(2).getStringCellValue());
            assertEquals("Итого внеурочная часть", sheet.getRow(12).getCell(0).getStringCellValue());
        }
    }

    @Test
    void exportParallelWorkbookHighlightsExceededMaximumLoad() throws Exception {
        CurriculumPlanEntry overloaded = entry("СП1", "7-А", "Алгебра", StudyPeriod.YEAR, 33);
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(overloaded));
        when(classroomRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject("Алгебра", "Математика и информатика")));

        byte[] body = service.exportParallelWorkbook("2026/2027");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("7 параллель");
            var maximumRow = java.util.stream.IntStream.rangeClosed(0, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(java.util.Objects::nonNull)
                    .filter(row -> row.getCell(0) != null && "Максимальная нагрузка".equals(row.getCell(0).getStringCellValue()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("32", maximumRow.getCell(2).getStringCellValue());
            assertEquals(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex(),
                    maximumRow.getCell(2).getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void exportEditableWorkbookIncludesManualLoadExclusionColumnValues() throws Exception {
        CurriculumPlanEntry ordinaryExcluded = entry("СП1", "4-Е", "ОДНКНР", StudyPeriod.YEAR, 1);
        ordinaryExcluded.setExcludedFromManualLoad(true);
        ordinaryExcluded.setMetaGroup(true);
        CurriculumPlanEntry ordinaryIncluded = entry("СП1", "4-Ж", "ОДНКНР", StudyPeriod.YEAR, 2);
        CurriculumPlanEntry explicitMetaGroup = entry("СП1", "МГ:4 4ЦЧ-СВЕТСКАЯ", "ОДНКНР", StudyPeriod.YEAR, 1);
        explicitMetaGroup.setMetaGroup(true);
        explicitMetaGroup.setMetaGroupId(4L);
        when(curriculumRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(ordinaryExcluded, ordinaryIncluded, explicitMetaGroup));
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject("ОДНКНР", "Основы духовно-нравственной культуры народов России")));

        byte[] body = service.exportEditableWorkbook("2026/2027");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("CURRICULUM_EDITABLE");
            assertNotNull(sheet);
            assertEquals("META_GROUP", sheet.getRow(0).getCell(13).getStringCellValue());
            assertEquals("EXCLUDED_FROM_MANUAL_LOAD", sheet.getRow(0).getCell(14).getStringCellValue());
            assertTrue(sheet.getRow(1).getCell(13).getBooleanCellValue());
            assertTrue(sheet.getRow(1).getCell(14).getBooleanCellValue());
            assertFalse(sheet.getRow(2).getCell(13).getBooleanCellValue());
            assertFalse(sheet.getRow(2).getCell(14).getBooleanCellValue());
            assertTrue(sheet.getRow(3).getCell(13).getBooleanCellValue());
            assertFalse(sheet.getRow(3).getCell(14).getBooleanCellValue());
        }
    }

    @Test
    void editableExportImportRoundTripPreservesManualLoadExclusionLegacyMirror() throws Exception {
        CurriculumPlanEntry ordinaryExcluded = entry("СП1", "4-Е", "ОДНКНР", StudyPeriod.YEAR, 1);
        ordinaryExcluded.setExcludedFromManualLoad(true);
        ordinaryExcluded.setMetaGroup(true);
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(ordinaryExcluded));
        when(subjectCatalogRepository.findAll()).thenReturn(List.of(subject("ОДНКНР", "Основы духовно-нравственной культуры народов России")));

        byte[] body = service.exportEditableWorkbook("2026/2027");

        StudyPeriodSetting rule = studyPeriodRule();
        when(teacherRepository.findAll()).thenReturn(List.of());
        when(classroomRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(classroom("СП1", "4-Е", "Общеобразовательный", "")));
        when(studyPeriodSettingService.resolveRuleForClassAndPeriod(anyString(), anyString(), any())).thenReturn(rule);
        when(curriculumRepository.findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(curriculumRepository.save(any(CurriculumPlanEntry.class))).thenAnswer(invocation -> {
            CurriculumPlanEntry saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(curriculumRepository.findAll()).thenReturn(List.of());
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        service.importFile(new MockMultipartFile("file", "curriculum.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", body), "2026/2027");

        org.mockito.ArgumentCaptor<CurriculumPlanEntry> captor = org.mockito.ArgumentCaptor.forClass(CurriculumPlanEntry.class);
        org.mockito.Mockito.verify(curriculumRepository).save(captor.capture());
        CurriculumPlanEntry saved = captor.getValue();
        assertTrue(saved.isExcludedFromManualLoad());
        assertTrue(saved.isMetaGroup());
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

    private StudyPeriodSetting studyPeriodRule() {
        StudyPeriodSetting rule = new StudyPeriodSetting();
        rule.setId(1L);
        rule.setAcademicYear("2026/2027");
        rule.setCode("YEAR");
        rule.setStudyPeriod(StudyPeriod.YEAR);
        rule.setParallelFrom(1);
        rule.setParallelTo(11);
        rule.setDisplayName("Учебный год");
        rule.setStartDate(LocalDate.of(2026, 9, 1));
        rule.setEndDate(LocalDate.of(2027, 5, 31));
        return rule;
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
