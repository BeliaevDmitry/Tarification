package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.CalendarDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarEventServiceImplTest {

    @Test
    void createsMeetingAndExpandsPeopleGroupAndBuildingParticipants() {
        Dependencies dependencies = new Dependencies();
        AppUser owner = user(1L, 10L, UserRole.METHODIST);
        TeacherDirectoryEntry explicit = teacher(11L, "Иванова Анна", "СП1");
        TeacherDirectoryEntry deputy = teacher(12L, "Петров Борис", "СП3");
        TeacherDirectoryEntry buildingPerson = teacher(13L, "Сидорова Вера", "СП-2");
        SchoolBuilding building = new SchoolBuilding();
        building.setId(21L);
        building.setCode("СП2");
        building.setAddress("Улица Школьная, 2");
        CalendarAudienceMembership membership = new CalendarAudienceMembership();
        membership.setGroupCode(CalendarAudienceGroup.DEPUTIES);
        membership.setTeacher(deputy);

        when(dependencies.users.findById(1L)).thenReturn(Optional.of(owner));
        when(dependencies.teachers.findAll()).thenReturn(List.of(explicit, deputy, buildingPerson));
        when(dependencies.buildings.findAllById(any())).thenReturn(List.of(building));
        when(dependencies.memberships.findAll()).thenReturn(List.of(membership));
        when(dependencies.settings.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(dependencies.events.save(any(CalendarEvent.class))).thenAnswer(invocation -> {
            CalendarEvent saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        CalendarDtos.EventView result = dependencies.service().create(new CalendarDtos.EventRequest(
                "Совещание", LocalDate.of(2026, 8, 24), LocalTime.of(10, 0), 90,
                "Актовый зал", CalendarEventVisibility.PARTICIPANTS,
                List.of(11L), List.of("DEPUTIES"), List.of(21L), List.of()), session(owner));

        assertEquals(LocalTime.of(11, 30), result.endTime());
        assertEquals(List.of(11L, 12L, 13L), result.participants().stream().map(CalendarDtos.PersonRef::id).sorted().toList());
        assertEquals(List.of("DEPUTIES"), result.selectedGroupCodes());
        assertEquals(List.of(21L), result.selectedBuildingIds());
        assertTrue(result.audienceSummary().contains("Замы"));
        assertTrue(result.audienceSummary().contains("Улица Школьная, 2"));
        assertTrue(result.canEdit());
    }

    @Test
    void participantAndExplicitlySharedViewerCanSeeRestrictedMeetings() {
        Dependencies dependencies = new Dependencies();
        AppUser owner = user(1L, 10L, UserRole.METHODIST);
        AppUser participantUser = user(2L, 20L, UserRole.METHODIST);
        AppUser sharedUser = user(3L, 30L, UserRole.METHODIST);
        TeacherDirectoryEntry participant = teacher(20L, "Участник", "СП1");
        TeacherDirectoryEntry shared = teacher(30L, "Наблюдатель", "СП2");
        CalendarEvent participantEvent = event(101L, owner, CalendarEventVisibility.PARTICIPANTS);
        participantEvent.getParticipants().add(participant);
        CalendarEvent privateEvent = event(102L, owner, CalendarEventVisibility.PRIVATE);
        privateEvent.getParticipants().add(participant);
        CalendarUserSettings ownerSettings = new CalendarUserSettings();
        ownerSettings.setUser(owner);
        ownerSettings.getSharedWith().add(shared);

        when(dependencies.events.findDistinctByStartsAtLessThanAndEndsAtGreaterThanEqualOrderByStartsAtAsc(any(), any()))
                .thenReturn(List.of(participantEvent, privateEvent));
        when(dependencies.settings.findAllByUser_IdIn(any())).thenReturn(List.of(ownerSettings));
        when(dependencies.memberships.findAll()).thenReturn(List.of());
        when(dependencies.users.findById(2L)).thenReturn(Optional.of(participantUser));
        when(dependencies.users.findById(3L)).thenReturn(Optional.of(sharedUser));

        List<CalendarDtos.EventView> participantResult = dependencies.service().list(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), session(participantUser));
        List<CalendarDtos.EventView> sharedResult = dependencies.service().list(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), session(sharedUser));

        assertEquals(List.of(101L), participantResult.stream().map(CalendarDtos.EventView::id).toList());
        assertEquals(List.of(101L, 102L), sharedResult.stream().map(CalendarDtos.EventView::id).toList());
    }

    private CalendarEvent event(Long id, AppUser owner, CalendarEventVisibility visibility) {
        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setOwner(owner);
        event.setTitle("Встреча " + id);
        event.setStartsAt(LocalDateTime.of(2026, 8, 20, 10, 0));
        event.setEndsAt(LocalDateTime.of(2026, 8, 20, 11, 0));
        event.setDurationMinutes(60);
        event.setVisibility(visibility);
        return event;
    }

    private TeacherDirectoryEntry teacher(Long id, String name, String building) {
        TeacherDirectoryEntry result = new TeacherDirectoryEntry();
        result.setId(id);
        result.setFioTeacher(name);
        result.setPrimaryPosition("Педагог");
        result.setNumberSchoolBuilding(building);
        return result;
    }

    private AppUser user(Long id, Long teacherId, UserRole role) {
        AppUser result = new AppUser();
        result.setId(id);
        result.setTeacherId(teacherId);
        result.setUsername("user" + id);
        result.setFullName("Пользователь " + id);
        result.setRole(role);
        result.setActive(true);
        return result;
    }

    private SessionUser session(AppUser user) {
        return new SessionUser(user.getId(), user.getUsername(), user.getFullName(), null, null, user.getRole(),
                true, true, true, null, true, new LinkedHashSet<>(), List.of());
    }

    private static final class Dependencies {
        private final CalendarEventRepository events = mock(CalendarEventRepository.class);
        private final CalendarUserSettingsRepository settings = mock(CalendarUserSettingsRepository.class);
        private final CalendarCustomListRepository customLists = mock(CalendarCustomListRepository.class);
        private final CalendarAudienceMembershipRepository memberships = mock(CalendarAudienceMembershipRepository.class);
        private final TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
        private final SchoolBuildingRepository buildings = mock(SchoolBuildingRepository.class);
        private final AppUserRepository users = mock(AppUserRepository.class);

        private CalendarEventServiceImpl service() {
            return new CalendarEventServiceImpl(events, settings, customLists, memberships, teachers, buildings, users);
        }
    }
}
