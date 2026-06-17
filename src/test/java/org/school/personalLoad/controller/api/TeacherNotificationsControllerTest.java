package org.school.personalLoad.controller.api;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private TeacherNotificationsController controller() {
        return new TeacherNotificationsController(manualLoadEntryRepository, recordRepository, teacherDirectoryRepository);
    }

    private ManualLoadEntry row(StudyPeriod period, int load) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setStudyPeriod(period);
        row.setLoad(load);
        return row;
    }
}
