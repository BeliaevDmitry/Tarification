package org.school.personalLoad.controller.api;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherNotificationRecordRepository;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherNotificationsControllerTest {

    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private TeacherNotificationRecordRepository recordRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;

    @Test
    void formatNotificationTotalLoadUsesHalfYearTotals() {
        TeacherNotificationsController controller = controller();

        assertEquals(
                "в 1 полугодие: 1 час и во 2 полугодие: 0 часов",
                controller.formatNotificationTotalLoad(List.of(row(StudyPeriod.H1, 1)))
        );
        assertEquals(
                "в 1 полугодие: 15 часов и во 2 полугодие: 22 часа",
                controller.formatNotificationTotalLoad(List.of(row(StudyPeriod.H1, 15), row(StudyPeriod.H2, 22)))
        );
        assertEquals(
                "18 часов",
                controller.formatNotificationTotalLoad(List.of(row(StudyPeriod.YEAR, 18)))
        );
    }

    @Test
    void formatNotificationTotalLoadUsesGroupLoadForSubgroups() {
        TeacherNotificationsController controller = controller();
        ManualLoadEntry subgroup = row(StudyPeriod.YEAR, 5);
        subgroup.setGroupNameEducationalPlan("Группа 1");
        subgroup.setGroupLoad(3);

        assertEquals(
                "3 часа",
                controller.formatNotificationTotalLoad(List.of(subgroup))
        );
    }

    @Test
    void activeRowsIgnoreFutureRowsOnNotificationDate() {
        ManualLoadEntry current = row(StudyPeriod.YEAR, 5);
        current.setLoadFromDate(LocalDate.of(2026, 9, 1));
        current.setLoadToDate(LocalDate.of(2026, 12, 31));
        ManualLoadEntry future = row(StudyPeriod.YEAR, 5);
        future.setLoadFromDate(LocalDate.of(2027, 1, 1));
        future.setLoadToDate(LocalDate.of(2027, 5, 31));
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(current, future));

        List<ManualLoadEntry> rows = controller().activeRows("2026/2027", LocalDate.of(2026, 9, 1));

        assertEquals(List.of(current), rows);
    }

    @Test
    void activeRowsKeepSecondHalfRowsForAnnualNotification() {
        ManualLoadEntry firstHalf = row(StudyPeriod.H1, 15);
        fillDuplicateKey(firstHalf);
        firstHalf.setLoadFromDate(LocalDate.of(2026, 9, 1));
        firstHalf.setLoadToDate(LocalDate.of(2026, 12, 31));
        ManualLoadEntry secondHalf = row(StudyPeriod.H2, 22);
        fillDuplicateKey(secondHalf);
        secondHalf.setLoadFromDate(LocalDate.of(2027, 1, 1));
        secondHalf.setLoadToDate(LocalDate.of(2027, 5, 31));
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(firstHalf, secondHalf));

        List<ManualLoadEntry> rows = controller().activeRows("2026/2027", LocalDate.of(2026, 9, 1));

        assertEquals(List.of(firstHalf, secondHalf), rows);
        assertEquals(
                "\u0432 1 \u043f\u043e\u043b\u0443\u0433\u043e\u0434\u0438\u0435: 15 \u0447\u0430\u0441\u043e\u0432 \u0438 \u0432\u043e 2 \u043f\u043e\u043b\u0443\u0433\u043e\u0434\u0438\u0435: 22 \u0447\u0430\u0441\u0430",
                controller().formatNotificationTotalLoad(rows)
        );
    }

    @Test
    void activeRowsKeepNullPeriodRowsThatStartAtAcademicYearBeginning() {
        ManualLoadEntry art = row(null, 1);
        fillDuplicateKey(art);
        art.setSubjectName("РР·РѕР±СЂР°Р·РёС‚РµР»СЊРЅРѕРµ РёСЃРєСѓСЃСЃС‚РІРѕ");
        art.setLoadFromDate(LocalDate.of(2025, 9, 1));
        art.setLoadToDate(LocalDate.of(2025, 11, 10));
        when(manualLoadEntryRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(art));

        List<ManualLoadEntry> rows = controller().activeRows("2025/2026", LocalDate.of(2026, 1, 1));

        assertEquals(List.of(art), rows);
        assertEquals(
                "\u0432 1 \u043f\u043e\u043b\u0443\u0433\u043e\u0434\u0438\u0435: 1 \u0447\u0430\u0441 \u0438 \u0432\u043e 2 \u043f\u043e\u043b\u0443\u0433\u043e\u0434\u0438\u0435: 0 \u0447\u0430\u0441\u043e\u0432",
                controller().formatNotificationTotalLoad(rows)
        );
    }

    @Test
    void activeRowsCollapseDuplicateRows() {
        ManualLoadEntry first = row(StudyPeriod.YEAR, 2);
        fillDuplicateKey(first);
        ManualLoadEntry duplicate = row(StudyPeriod.YEAR, 2);
        fillDuplicateKey(duplicate);
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(first, duplicate));

        List<ManualLoadEntry> rows = controller().activeRows("2026/2027", LocalDate.of(2026, 9, 1));

        assertEquals(List.of(first), rows);
        assertEquals("2 часа", controller().formatNotificationTotalLoad(rows));
    }

    @Test
    void activeRowsKeepDifferentModulesAndSumThemUnderBaseSubject() {
        ManualLoadEntry firstModule = row(StudyPeriod.YEAR, 1);
        fillDuplicateKey(firstModule);
        firstModule.setSubjectName("Труд");
        firstModule.setCurriculumModuleId(101L);
        ManualLoadEntry secondModule = row(StudyPeriod.YEAR, 1);
        fillDuplicateKey(secondModule);
        secondModule.setSubjectName("Труд");
        secondModule.setCurriculumModuleId(102L);
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(firstModule, secondModule));

        List<ManualLoadEntry> rows = controller().activeRows("2026/2027", LocalDate.of(2026, 9, 1));

        assertEquals(2, rows.size());
        assertEquals("2 часа", controller().formatNotificationTotalLoad(rows));
        assertEquals("Труд", rows.get(0).getSubjectName());
        assertEquals("Труд", rows.get(1).getSubjectName());
    }

    @Test
    void teacherNameForNotificationUsesDativeNameFromDirectory() {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setFioTeacher("Иванов Иван Иванович");
        teacher.setFioTeacherDative("Иванову Ивану Ивановичу");
        when(teacherDirectoryRepository.findByFioTeacherIgnoreCase("Иванов Иван Иванович"))
                .thenReturn(Optional.of(teacher));
        TeacherNotificationsController controller = controller();

        assertEquals("Иванову Ивану Ивановичу", controller.teacherNameForNotification("Иванов Иван Иванович"));
    }

    @Test
    void stripTemplateHourWordAfterTotalLoadAvoidsDuplicateHourWords() {
        TeacherNotificationsController controller = controller();

        assertEquals(
                "предварительную педагогическую нагрузку - ${TOTAL_LOAD}.",
                controller.stripTemplateHourWordAfterTotalLoad("предварительную педагогическую нагрузку - ${TOTAL_LOAD} часов.")
        );
    }

    @Test
    void notificationOmitsInsideRateColumnForOrdinaryLoad() throws Exception {
        ManualLoadEntry load = documentRow(6);
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load));

        byte[] body = controller().generateDoc(
                load.getFioTeacher(), "2026/2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 20));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(body))) {
            XWPFTable table = loadTable(document);
            assertFalse(table.getText().contains("Внутри ставки"));
            assertEquals(4, table.getRow(0).getTableCells().size());
        }
    }

    @Test
    void notificationShowsInsideRateColumnWhenEmployeeHasIncludedHours() throws Exception {
        ManualLoadEntry load = documentRow(6);
        load.setIncludedInRateHours(new BigDecimal("4"));
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load));

        byte[] body = controller().generateDoc(
                load.getFioTeacher(), "2026/2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 20));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(body))) {
            XWPFTable table = loadTable(document);
            assertTrue(table.getText().contains("Внутри ставки"));
            assertEquals(5, table.getRow(0).getTableCells().size());
            assertEquals("4", table.getRow(1).getCell(3).getText());
        }
    }

    private XWPFTable loadTable(XWPFDocument document) {
        return document.getTables().stream()
                .filter(table -> table.getText().contains("Часы всего"))
                .findFirst()
                .orElseThrow();
    }

    private ManualLoadEntry documentRow(int load) {
        ManualLoadEntry row = row(StudyPeriod.YEAR, load);
        row.setFioTeacher("Иванов Иван Иванович");
        row.setSubjectName("ОБЗР");
        row.setClassName("7-А");
        row.setLoadFromDate(LocalDate.of(2026, 9, 1));
        row.setLoadToDate(LocalDate.of(2027, 5, 31));
        return row;
    }

    private TeacherNotificationsController controller() {
        return new TeacherNotificationsController(manualLoadEntryRepository, recordRepository, teacherDirectoryRepository);
    }

    private ManualLoadEntry row(StudyPeriod period, int load) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setStudyPeriod(period);
        row.setLoad(load);
        return row;
    }

    private void fillDuplicateKey(ManualLoadEntry row) {
        row.setFioTeacher("РџРµРґР°РіРѕРі");
        row.setNumberSchoolBuilding("1");
        row.setClassName("8-А");
        row.setSubjectName("Биология");
        row.setEducationLevel(org.school.personalLoad.model.EducationLevel.BASIC);
        row.setLoadFromDate(LocalDate.of(2026, 9, 1));
        row.setLoadToDate(LocalDate.of(2027, 5, 31));
    }
}
