package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.ProbeOrder;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProbeOrderServiceImplTest {

    @Test
    void importsCurrentRegistrationUsingSystemBuildingStaffAndStudentCards() throws Exception {
        ProbeOrderRepository orders = mock(ProbeOrderRepository.class);
        ProbeOrderGeneratedDocumentRepository documents = mock(ProbeOrderGeneratedDocumentRepository.class);
        ProbeOrderScanRepository scans = mock(ProbeOrderScanRepository.class);
        ContingentSnapshotRepository snapshots = mock(ContingentSnapshotRepository.class);
        ContingentStudentRepository contingentStudents = mock(ContingentStudentRepository.class);
        StudentProfileRepository students = mock(StudentProfileRepository.class);
        ClassroomLeadershipRepository leadership = mock(ClassroomLeadershipRepository.class);
        TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);

        SchoolBuilding building = new SchoolBuilding();
        building.setId(7L);
        building.setCode("СП-2");
        building.setName("Корпус на Учебной");
        building.setAddress("Москва, Учебная улица, дом 1");
        building.setManagerFio("Кузнецова Елена Викторовна");
        TeacherDirectoryEntry classTeacher = new TeacherDirectoryEntry();
        classTeacher.setId(31L);
        classTeacher.setFioTeacher("Петрова Мария Сергеевна");
        classTeacher.setPrimaryPosition("Учитель");
        ClassroomLeadershipEntry classLeadership = new ClassroomLeadershipEntry();
        classLeadership.setAcademicYear("2026/2027");
        classLeadership.setClassName("9А");
        classLeadership.setSchoolBuilding(building);
        classLeadership.setTeacher(classTeacher);

        List<StudentProfile> profiles = new ArrayList<>();
        for (int index = 1; index <= 11; index++) {
            StudentProfile profile = new StudentProfile();
            profile.setId((long) index);
            profile.setCurrentFullName("Ученик Тестовый " + index);
            profile.setNormalizedFullName("ученик тестовый " + index);
            profiles.add(profile);
        }
        when(snapshots.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2026/2027"))
                .thenReturn(Optional.empty());
        when(students.findAll()).thenReturn(profiles);
        when(leadership.findAllByAcademicYear("2026/2027")).thenReturn(List.of(classLeadership));
        when(orders.findByAcademicYearAndExternalEventIdAndSchoolBuilding_Id("2026/2027", "EV-101", 7L))
                .thenReturn(Optional.empty());
        when(orders.save(any(ProbeOrder.class))).thenAnswer(invocation -> {
            ProbeOrder order = invocation.getArgument(0);
            order.setId(99L);
            return order;
        });
        when(documents.findAllByOrder_IdIn(any())).thenReturn(List.of());
        when(scans.findAllByOrder_IdIn(any())).thenReturn(List.of());

        ProbeOrderServiceImpl service = new ProbeOrderServiceImpl(
                orders, documents, scans, snapshots, contingentStudents, students, leadership,
                teachers, users, new ProbeOrderDocumentService(), new ObjectMapper());
        ProbeOrderDtos.ImportResponse result = service.importRegistration(
                "2026/2027", registrationFile(11), admin());

        ArgumentCaptor<ProbeOrder> captured = ArgumentCaptor.forClass(ProbeOrder.class);
        org.mockito.Mockito.verify(orders).save(captured.capture());
        ProbeOrder order = captured.getValue();
        when(orders.findAllByAcademicYearOrderByEventDateAscStartTimeAsc("2026/2027"))
                .thenReturn(List.of(order));
        ProbeOrderDtos.OrderView view = service.list("2026/2027", admin()).get(0);

        assertEquals(1, result.eventsRead());
        assertEquals(11, result.applicationsRead());
        assertEquals(11, result.participantsLinked());
        assertEquals(0, result.unresolvedApplications());
        assertEquals("СП-2", view.buildingCode());
        assertEquals(List.of("9-А"), view.classNames());
        assertEquals(11, view.participantCount());
        assertEquals(2, view.requiredCompanions());
        assertFalse(view.companionsComplete());
        assertNotNull(view.primaryCompanion());
        assertEquals(31L, view.primaryCompanion().id());
        assertEquals("Родитель Тестовый 1", view.participants().get(0).representativeName());
        assertEquals("+7 900 000-00-01", view.participants().get(0).representativePhone());

        when(orders.findAllByStatusAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                org.school.personalLoad.model.ProbeOrderStatus.RELEASED,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(order));
        ProbeOrderDtos.CalendarEvent calendarEvent = service.calendar(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).get(0);
        assertEquals("BUILDING", calendarEvent.participants().get(0).type());
        assertEquals(7L, calendarEvent.participants().get(0).id());
        assertEquals("Москва, Учебная улица, дом 1", calendarEvent.participants().get(0).details());
        assertEquals("PERSON", calendarEvent.participants().get(1).type());
        assertEquals(31L, calendarEvent.participants().get(1).id());
    }

    private MockMultipartFile registrationFile(int children) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet events = workbook.createSheet("Мероприятия");
            Row eventHeader = events.createRow(0);
            String[] eventColumns = {"ID события", "Название мероприятия", "Дата мероприятия", "Время мероприятия",
                    "Организатор", "Партнёр", "Адрес проведения"};
            for (int index = 0; index < eventColumns.length; index++) eventHeader.createCell(index).setCellValue(eventColumns[index]);
            Row event = events.createRow(1);
            event.createCell(0).setCellValue("EV-101");
            event.createCell(1).setCellValue("Профессиональная проба по робототехнике");
            event.createCell(2).setCellValue("22.09.2026");
            event.createCell(3).setCellValue("10:30–12:00");
            event.createCell(4).setCellValue("Московский колледж технологий");
            event.createCell(5).setCellValue("Партнёр проекта");
            event.createCell(6).setCellValue("Москва, Техническая улица, дом 5");

            Sheet applications = workbook.createSheet("Заявки");
            Row appHeader = applications.createRow(0);
            String[] appColumns = {"ID события", "ФИО", "Класс", "Литтера класса",
                    "ФИО представителя", "Телефон представителя"};
            for (int index = 0; index < appColumns.length; index++) appHeader.createCell(index).setCellValue(appColumns[index]);
            for (int index = 1; index <= children; index++) {
                Row row = applications.createRow(index);
                row.createCell(0).setCellValue("EV-101");
                row.createCell(1).setCellValue("Ученик Тестовый " + index);
                row.createCell(2).setCellValue("9");
                row.createCell(3).setCellValue("А");
                row.createCell(4).setCellValue("Родитель Тестовый " + index);
                row.createCell(5).setCellValue(String.format("+7 900 000-00-%02d", index));
            }
            workbook.write(out);
            return new MockMultipartFile("file", "registration.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private SessionUser admin() {
        return new SessionUser(1L, "admin", "Администратор", null, null, UserRole.ADMIN,
                true, true, true, null, true, new LinkedHashSet<>(), List.of());
    }
}
