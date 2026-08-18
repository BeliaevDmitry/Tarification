package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.CalendarAudienceDtos;
import org.school.personalLoad.model.CalendarAudienceGroup;
import org.school.personalLoad.model.CalendarAudienceMembership;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.CalendarAudienceMembershipRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarAudienceServiceImplTest {

    @Test
    void directorCanReplaceAllThreeGroupCompositions() {
        CalendarAudienceMembershipRepository memberships = mock(CalendarAudienceMembershipRepository.class);
        TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
        SchoolBuildingRepository buildings = mock(SchoolBuildingRepository.class);
        TeacherDirectoryEntry first = teacher(1L, "Первый Сотрудник", "СП1");
        TeacherDirectoryEntry second = teacher(2L, "Второй Сотрудник", "СП2");
        CalendarAudienceMembership old = new CalendarAudienceMembership();
        old.setId(50L);
        old.setGroupCode(CalendarAudienceGroup.DEPUTIES);
        old.setTeacher(first);
        SchoolBuilding building = new SchoolBuilding();
        building.setId(7L);
        building.setCode("СП1");
        building.setName("Основной корпус");
        building.setAddress("Учебная улица, дом 1");

        when(memberships.findAll()).thenReturn(List.of(old));
        when(teachers.findAllById(any())).thenReturn(List.of(first, second));
        when(teachers.findAll()).thenReturn(List.of(second, first));
        when(buildings.findAll()).thenReturn(List.of(building));

        CalendarAudienceServiceImpl service = new CalendarAudienceServiceImpl(memberships, teachers, buildings);
        CalendarAudienceDtos.SettingsView result = service.update(new CalendarAudienceDtos.UpdateRequest(List.of(
                new CalendarAudienceDtos.GroupSelection("DEPUTIES", List.of(2L)),
                new CalendarAudienceDtos.GroupSelection("ADMINISTRATION", List.of(1L)),
                new CalendarAudienceDtos.GroupSelection("FULL_ADMINISTRATION", List.of())
        )), user(UserRole.DIRECTOR));

        assertEquals(List.of(2L), result.groups().get(0).personIds());
        assertEquals(List.of(1L), result.groups().get(1).personIds());
        assertEquals("Учебная улица, дом 1", result.buildings().get(0).address());
        verify(memberships).deleteAll(List.of(old));
        ArgumentCaptor<List<CalendarAudienceMembership>> created = ArgumentCaptor.forClass(List.class);
        verify(memberships).saveAll(created.capture());
        assertEquals(2, created.getValue().size());
    }

    @Test
    void ordinaryUserCannotChangeSharedGroups() {
        CalendarAudienceServiceImpl service = new CalendarAudienceServiceImpl(
                mock(CalendarAudienceMembershipRepository.class),
                mock(TeacherDirectoryRepository.class),
                mock(SchoolBuildingRepository.class));

        assertThrows(AuthExceptions.ForbiddenException.class,
                () -> service.update(new CalendarAudienceDtos.UpdateRequest(List.of()), user(UserRole.METHODIST)));
    }

    private TeacherDirectoryEntry teacher(Long id, String name, String buildingCode) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(name);
        teacher.setPrimaryPosition("Педагог");
        teacher.setNumberSchoolBuilding(buildingCode);
        return teacher;
    }

    private SessionUser user(UserRole role) {
        return new SessionUser(1L, "user", "Пользователь", null, null, role,
                true, true, true, null, true, new LinkedHashSet<>(), List.of());
    }
}
