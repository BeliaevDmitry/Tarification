package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.dto.contingent.OvzSpecialistWorkspaceDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OvzSpecialistWorkspaceServiceTest {

    private CorrectionStudentAssignmentRepository assignments;
    private OvzSpecialistSupportEntryRepository entries;
    private OvzSpecialistWorkspaceSettingsRepository settings;
    private StudentProfileRepository students;
    private StudentClassEnrollmentRepository enrollments;
    private TeacherDirectoryRepository teachers;
    private AppUserRepository users;
    private OvzSpecialistWorkspaceService service;

    @BeforeEach
    void setUp() {
        assignments = mock(CorrectionStudentAssignmentRepository.class);
        entries = mock(OvzSpecialistSupportEntryRepository.class);
        settings = mock(OvzSpecialistWorkspaceSettingsRepository.class);
        students = mock(StudentProfileRepository.class);
        enrollments = mock(StudentClassEnrollmentRepository.class);
        teachers = mock(TeacherDirectoryRepository.class);
        users = mock(AppUserRepository.class);
        service = new OvzSpecialistWorkspaceService(assignments, entries, settings, students, enrollments, teachers, users);
        when(settings.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
    }

    @Test
    void ordinarySpecialistSeesOnlyChildrenAssignedByPersonnelCard() {
        CorrectionStudentAssignment own = assignment(1L, "Иванов Иван", 101L, "Учитель-логопед", 10L, "Логопед");
        CorrectionStudentAssignment other = assignment(2L, "Петров Пётр", 102L, "Педагог-психолог", 20L, "Психолог");
        when(assignments.findAllByAcademicYear("2026/2027")).thenReturn(List.of(own, other));
        when(entries.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(enrollments.findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(enrollments.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(anyLong(), anyString()))
                .thenReturn(List.of());
        when(users.findById(7L)).thenReturn(Optional.of(appUser(10L)));

        OvzSpecialistWorkspaceDtos.Overview result = service.overview("2026/2027", session(7L, false));

        assertFalse(result.isResponsible());
        assertEquals(1, result.getChildCount());
        assertEquals("Иванов Иван", result.getChildren().get(0).getFullName());
        assertTrue(result.getChildren().get(0).getSpecialists().get(0).isEditable());
    }

    @Test
    void ordinarySpecialistCannotChangeAnotherEmployeesPart() {
        CorrectionStudentAssignment other = assignment(2L, "Петров Пётр", 102L, "Педагог-психолог", 20L, "Психолог");
        when(assignments.findByAcademicYearAndStudent_IdAndSpecialist_Id("2026/2027", 2L, 102L))
                .thenReturn(Optional.of(other));
        when(users.findById(7L)).thenReturn(Optional.of(appUser(10L)));
        OvzSpecialistWorkspaceDtos.SupportEntryRequest request = new OvzSpecialistWorkspaceDtos.SupportEntryRequest();
        request.setSpecialistId(102L);

        assertThrows(ForbiddenException.class,
                () -> service.saveEntry("2026/2027", 2L, request, session(7L, false)));
        verify(entries, never()).save(any());
    }

    @Test
    void appointedResponsibleCanCompleteAnySpecialistsPart() {
        TeacherDirectoryEntry responsible = teacher(10L, "Ответственный");
        OvzSpecialistWorkspaceSettings configured = new OvzSpecialistWorkspaceSettings();
        configured.setId(1L);
        configured.setResponsibleTeacher(responsible);
        when(settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(configured));
        CorrectionStudentAssignment other = assignment(2L, "Петров Пётр", 102L, "Педагог-психолог", 20L, "Психолог");
        when(assignments.findByAcademicYearAndStudent_IdAndSpecialist_Id("2026/2027", 2L, 102L))
                .thenReturn(Optional.of(other));
        when(users.findById(7L)).thenReturn(Optional.of(appUser(10L)));
        when(entries.findByAcademicYearAndStudent_IdAndSpecialist_Id("2026/2027", 2L, 102L))
                .thenReturn(Optional.empty());
        when(entries.save(any())).thenAnswer(invocation -> {
            OvzSpecialistSupportEntry entry = invocation.getArgument(0);
            entry.setId(50L);
            return entry;
        });
        OvzSpecialistWorkspaceDtos.SupportEntryRequest request = new OvzSpecialistWorkspaceDtos.SupportEntryRequest();
        request.setSpecialistId(102L);
        request.setChildDeficits("Дефициты");
        request.setChildResources("Ресурсы");
        request.setAnnualTasks("Задачи");
        request.setPlannedResults("Результаты");

        OvzSpecialistWorkspaceDtos.SupportEntry result = service.saveEntry(
                "2026/2027", 2L, request, session(7L, false));

        assertTrue(result.isEditable());
        assertEquals(OvzSpecialistWorkspaceDtos.CompletionStatus.COMPLETED, result.getStatus());
        verify(entries).save(any(OvzSpecialistSupportEntry.class));
    }

    private SessionUser session(Long id, boolean edit) {
        SessionUser session = new SessionUser();
        session.setId(id);
        session.setFullName("Текущий пользователь");
        session.setRole(UserRole.HR);
        session.setActive(true);
        session.setCanView(true);
        session.setCanEdit(edit);
        session.setTabPermissions(List.of(new TabPermissionSnapshot(AppTab.OVZ, true, edit, false, false)));
        return session;
    }

    private AppUser appUser(Long teacherId) {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setTeacherId(teacherId);
        return user;
    }

    private CorrectionStudentAssignment assignment(Long studentId, String studentName, Long specialistId,
                                                    String specialistName, Long teacherId, String teacherName) {
        StudentProfile student = new StudentProfile();
        student.setId(studentId);
        student.setCurrentFullName(studentName);
        CorrectionSpecialistCatalogEntry specialist = new CorrectionSpecialistCatalogEntry();
        specialist.setId(specialistId);
        specialist.setName(specialistName);
        CorrectionSpecialistStaff staff = new CorrectionSpecialistStaff();
        staff.setId(specialistId + 1000);
        staff.setSpecialist(specialist);
        staff.setTeacher(teacher(teacherId, teacherName));
        CorrectionStudentAssignment assignment = new CorrectionStudentAssignment();
        assignment.setStudent(student);
        assignment.setSpecialist(specialist);
        assignment.setStaff(staff);
        assignment.setAcademicYear("2026/2027");
        return assignment;
    }

    private TeacherDirectoryEntry teacher(Long id, String name) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(name);
        return teacher;
    }
}
