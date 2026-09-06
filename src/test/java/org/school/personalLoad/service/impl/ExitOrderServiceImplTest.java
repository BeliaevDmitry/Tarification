package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.ExitOrderDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExitOrderServiceImplTest {

    @Test
    void usesVlasovaAsDefaultDeputyInExitOrderSettings() {
        TestContext context = context();
        TeacherDirectoryEntry bocharova = teacher(19L, "Бочарова Анна Сергеевна", "СП1");
        TeacherDirectoryEntry vlasova = teacher(20L, "Власова Юлия Сергеевна", "СП1");
        when(context.teachers.findAll()).thenReturn(List.of(bocharova, vlasova));

        ExitOrderDtos.SettingsView result = context.service.settings("2026/2027", admin());

        assertEquals(20L, result.deputyDirectorTeacherId());
        assertEquals("Власова Юлия Сергеевна", result.deputyDirectorName());
    }

    @Test
    void suggestsLoggedInClassTeacherClassAndItsStudents() {
        TestContext context = context();
        SchoolBuilding building = building(10L, "СП1", "Корпус 1", "ул. Первая, д. 1");
        TeacherDirectoryEntry teacher = teacher(20L, "Петрова Мария Сергеевна", "СП1");
        ClassroomLeadershipEntry classroom = classroom(30L, "6Е", building, teacher);
        StudentProfile student = student(40L, "Иванов Иван Иванович");
        StudentClassEnrollment enrollment = enrollment(classroom, student);
        AppUser account = new AppUser();
        account.setId(1L);
        account.setTeacherId(teacher.getId());

        when(context.appUsers.findById(1L)).thenReturn(Optional.of(account));
        when(context.classrooms.findAllByAcademicYear("2026/2027")).thenReturn(List.of(classroom));
        when(context.enrollments.findAllByAcademicYear("2026/2027")).thenReturn(List.of(enrollment));
        when(context.teachers.findAll()).thenReturn(List.of(teacher));

        ExitOrderDtos.ReferenceData result = context.service.references("2026/2027", admin());

        assertEquals(List.of(30L), result.suggestedClassIds());
        assertEquals(20L, result.defaultCompanionTeacherId());
        assertEquals("ул. Первая, д. 1", result.suggestedGatheringPlace());
        assertTrue(result.classes().get(0).suggested());
        assertEquals(List.of("Иванов Иван Иванович"), result.classes().get(0).students().stream()
                .map(ExitOrderDtos.StudentOption::fullName).toList());
    }

    @Test
    void createsOrderWithGatheringAtBuildingContainingMostSelectedChildren() {
        TestContext context = context();
        SchoolBuilding first = building(10L, "СП1", "Корпус 1", "ул. Первая, д. 1");
        SchoolBuilding second = building(11L, "СП2", "Корпус 2", "ул. Вторая, д. 2");
        TeacherDirectoryEntry companion = teacher(20L, "Петрова Мария Сергеевна", "СП1");
        ClassroomLeadershipEntry class6e = classroom(30L, "6Е", first, companion);
        ClassroomLeadershipEntry class7a = classroom(31L, "7А", second, companion);
        StudentProfile firstStudent = student(40L, "Иванов Иван Иванович");
        StudentProfile secondStudent = student(41L, "Петров Пётр Петрович");
        StudentProfile thirdStudent = student(42L, "Сидорова Анна Олеговна");
        List<StudentClassEnrollment> enrollments = List.of(
                enrollment(class6e, firstStudent), enrollment(class6e, secondStudent),
                enrollment(class7a, thirdStudent));

        when(context.students.findAllById(any())).thenReturn(List.of(firstStudent, secondStudent, thirdStudent));
        when(context.enrollments.findAllByAcademicYear("2026/2027")).thenReturn(enrollments);
        when(context.buildings.findById(10L)).thenReturn(Optional.of(first));
        when(context.teachers.findById(20L)).thenReturn(Optional.of(companion));
        when(context.orders.save(any(ExitOrder.class))).thenAnswer(invocation -> {
            ExitOrder order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });

        ExitOrderDtos.CreateRequest request = new ExitOrderDtos.CreateRequest(
                null, "Экскурсия в музей", LocalDate.now().plusDays(7),
                LocalTime.of(11, 0), LocalTime.of(12, 30), "Музей",
                "ул. Музейная, д. 5", LocalTime.of(10, 0), null,
                LocalTime.of(14, 0), List.of(40L, 41L, 42L), 20L, null, List.of());

        ExitOrderDtos.OrderView result = context.service.create("2026/2027", request, admin());

        assertEquals(10L, result.schoolBuildingId());
        assertEquals("СП1", result.buildingCode());
        assertEquals("ул. Первая, д. 1", result.gatheringPlace());
        assertEquals(List.of("6Е", "7А"), result.classNames());
        assertEquals(3, result.participantCount());
    }

    @Test
    void attendanceAndSummaryExcludeAbsentChildrenFromVisits() {
        TestContext context = context();
        SchoolBuilding building = building(10L, "СП1", "Корпус 1", "ул. Первая, д. 1");
        TeacherDirectoryEntry companion = teacher(20L, "Петрова Мария Сергеевна", "СП1");
        ExitOrder order = releasedOrder(100L, building, companion);
        ExitOrderParticipant attended = participant(501L, order, student(40L, "Иванов Иван Иванович"), "6Е");
        ExitOrderParticipant absent = participant(502L, order, student(41L, "Петров Пётр Петрович"), "6Е");
        order.getParticipants().addAll(List.of(attended, absent));

        when(context.orders.findOneById(100L)).thenReturn(Optional.of(order));
        when(context.orders.save(any(ExitOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.orders.findAllByAcademicYearOrderByEventDateAscStartTimeAsc("2026/2027"))
                .thenReturn(List.of(order));

        ExitOrderDtos.OrderView updated = context.service.markAttendance(100L,
                new ExitOrderDtos.AttendanceRequest(List.of(502L)), admin());
        ExitOrderDtos.SummaryView summary = context.service.summary("2026/2027", admin());

        assertEquals(1, updated.absentCount());
        assertFalse(attended.isAbsent());
        assertTrue(absent.isAbsent());
        assertEquals(1, summary.totalEvents());
        assertEquals(1, summary.totalAttended());
        assertEquals(1, summary.totalAbsent());
        assertEquals(1, summary.classes().get(0).events());
        assertEquals(1, summary.classes().get(0).attended());
        assertEquals(1, summary.teachers().get(0).events());
        assertEquals(1, summary.teachers().get(0).childrenAccompanied());
    }

    private TestContext context() {
        ExitOrderRepository orders = mock(ExitOrderRepository.class);
        ExitOrderApprovalRepository approvals = mock(ExitOrderApprovalRepository.class);
        ExitOrderSettingsRepository settings = mock(ExitOrderSettingsRepository.class);
        ExitOrderDictionaryOptionRepository dictionaries = mock(ExitOrderDictionaryOptionRepository.class);
        ExitOrderGeneratedDocumentRepository documents = mock(ExitOrderGeneratedDocumentRepository.class);
        ExitOrderScanRepository scans = mock(ExitOrderScanRepository.class);
        ClassroomLeadershipRepository classrooms = mock(ClassroomLeadershipRepository.class);
        StudentClassEnrollmentRepository enrollments = mock(StudentClassEnrollmentRepository.class);
        StudentProfileRepository students = mock(StudentProfileRepository.class);
        SchoolBuildingRepository buildings = mock(SchoolBuildingRepository.class);
        TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
        AppUserRepository appUsers = mock(AppUserRepository.class);
        when(settings.findById(ExitOrderSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(approvals.findAllByOrder_Id(any())).thenReturn(List.of());
        when(scans.findByOrder_Id(any())).thenReturn(Optional.empty());
        ExitOrderServiceImpl service = new ExitOrderServiceImpl(orders, approvals, settings, dictionaries,
                documents, scans, classrooms, enrollments, students, buildings, teachers, appUsers,
                new ProbeOrderDocumentService());
        return new TestContext(service, orders, classrooms, enrollments, students, buildings, teachers, appUsers);
    }

    private SessionUser admin() {
        return new SessionUser(1L, "admin", "Администратор", null, null, UserRole.ADMIN,
                true, true, true, null, true, new LinkedHashSet<>(), List.of());
    }

    private SchoolBuilding building(Long id, String code, String name, String address) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode(code);
        building.setName(name);
        building.setAddress(address);
        building.setManagerFio("Руководитель корпуса");
        return building;
    }

    private TeacherDirectoryEntry teacher(Long id, String fullName, String buildingCode) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fullName);
        teacher.setPrimaryPosition("Учитель");
        teacher.setNumberSchoolBuilding(buildingCode);
        return teacher;
    }

    private ClassroomLeadershipEntry classroom(Long id, String name, SchoolBuilding building,
                                                TeacherDirectoryEntry teacher) {
        ClassroomLeadershipEntry classroom = new ClassroomLeadershipEntry();
        classroom.setId(id);
        classroom.setAcademicYear("2026/2027");
        classroom.setClassName(name);
        classroom.setClassDirection("Общеобразовательный");
        classroom.setFioTeacher(teacher.getFioTeacher());
        classroom.setTeacher(teacher);
        classroom.setSchoolBuilding(building);
        classroom.setNumberSchoolBuilding(building.getCode());
        classroom.setCampusAddress(building.getAddress());
        return classroom;
    }

    private StudentProfile student(Long id, String fullName) {
        StudentProfile student = new StudentProfile();
        student.setId(id);
        student.setCurrentFullName(fullName);
        student.setNormalizedFullName(fullName.toLowerCase());
        student.setActive(true);
        return student;
    }

    private StudentClassEnrollment enrollment(ClassroomLeadershipEntry classroom, StudentProfile student) {
        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setStudent(student);
        enrollment.setClassRef(classroom);
        enrollment.setAcademicYear("2026/2027");
        enrollment.setClassName(classroom.getClassName());
        enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
        return enrollment;
    }

    private ExitOrder releasedOrder(Long id, SchoolBuilding building, TeacherDirectoryEntry companion) {
        ExitOrder order = new ExitOrder();
        order.setId(id);
        order.setAcademicYear("2026/2027");
        order.setPreamble("На основании Плана воспитательной работы");
        order.setEventName("Экскурсия");
        order.setEventDate(LocalDate.now().minusDays(1));
        order.setStartTime(LocalTime.of(10, 0));
        order.setEndTime(LocalTime.of(12, 0));
        order.setVenue("Музей");
        order.setEventAddress("ул. Музейная, д. 5");
        order.setGatheringTime(LocalTime.of(9, 0));
        order.setGatheringPlace(building.getAddress());
        order.setReturnTime(LocalTime.of(13, 0));
        order.setSchoolBuilding(building);
        order.setPrimaryCompanion(companion);
        order.setRequestedByUserId(1L);
        order.setRequestedBy("Администратор");
        order.setStatus(ProbeOrderStatus.RELEASED);
        return order;
    }

    private ExitOrderParticipant participant(Long id, ExitOrder order, StudentProfile student, String className) {
        ExitOrderParticipant participant = new ExitOrderParticipant();
        participant.setId(id);
        participant.setOrder(order);
        participant.setStudent(student);
        participant.setFullNameSnapshot(student.getCurrentFullName());
        participant.setClassNameSnapshot(className);
        participant.setOrganizationalBuildingCode(order.getSchoolBuilding().getCode());
        participant.setSchoolBuildingId(order.getSchoolBuilding().getId());
        return participant;
    }

    private record TestContext(ExitOrderServiceImpl service,
                               ExitOrderRepository orders,
                               ClassroomLeadershipRepository classrooms,
                               StudentClassEnrollmentRepository enrollments,
                               StudentProfileRepository students,
                               SchoolBuildingRepository buildings,
                               TeacherDirectoryRepository teachers,
                               AppUserRepository appUsers) {
    }
}
