package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.model.PpkProtocolSettings;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.PpkProtocolSettingsRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PpkProtocolSettingsServiceTest {
    private final PpkProtocolSettingsRepository settingsRepository = mock(PpkProtocolSettingsRepository.class);
    private final TeacherDirectoryRepository teacherRepository = mock(TeacherDirectoryRepository.class);
    private final PpkProtocolSettingsService service = new PpkProtocolSettingsService(settingsRepository, teacherRepository);

    @Test
    void savesCommissionFromEmployeeCardsWithCurrentPositions() {
        PpkProtocolSettings settings = new PpkProtocolSettings();
        TeacherDirectoryEntry chair = employee(1L, "Власова Юлия Сергеевна", "Заместитель директора");
        TeacherDirectoryEntry secretary = employee(2L, "Рыбкина Лариса Павловна", "Педагог-психолог");
        TeacherDirectoryEntry attendee = employee(3L, "Дмитриева Ирина Николаевна", "Учитель-логопед");
        when(settingsRepository.findById(PpkProtocolSettings.DEFAULT_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teacherRepository.findAll()).thenReturn(List.of(chair, secretary, attendee));

        OvzDtos.PpkProtocolSettingsRequest request = new OvzDtos.PpkProtocolSettingsRequest();
        request.setChairEmployeeId(1L);
        request.setSecretaryEmployeeId(2L);
        request.setAttendeeEmployeeIds(List.of(3L));
        OvzDtos.PpkProtocolSettingsView view = service.update(request);

        assertEquals("Власова Юлия Сергеевна", view.getChairName());
        assertEquals("Заместитель директора", view.getChairPosition());
        assertEquals("Дмитриева Ирина Николаевна — Учитель-логопед", view.getAttendees());
        assertEquals("3", settings.getAttendeeEmployeeIds());
    }

    @Test
    void rejectsOneEmployeeInTwoCommissionRoles() {
        OvzDtos.PpkProtocolSettingsRequest request = new OvzDtos.PpkProtocolSettingsRequest();
        request.setChairEmployeeId(1L);
        request.setSecretaryEmployeeId(1L);
        request.setAttendeeEmployeeIds(List.of(3L));

        assertThrows(IllegalArgumentException.class, () -> service.update(request));
    }

    private TeacherDirectoryEntry employee(Long id, String fullName, String position) {
        TeacherDirectoryEntry employee = new TeacherDirectoryEntry();
        employee.setId(id);
        employee.setFioTeacher(fullName);
        employee.setPrimaryPosition(position);
        return employee;
    }
}
