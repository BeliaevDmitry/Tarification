package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.TeacherTimeOffDtos;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.TeacherTimeOffEntry;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherTimeOffEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherTimeOffServiceTest {
    private TeacherTimeOffEntryRepository entries;
    private TeacherDirectoryRepository teachers;
    private TeacherTimeOffService service;

    @BeforeEach
    void setUp() {
        entries = mock(TeacherTimeOffEntryRepository.class);
        teachers = mock(TeacherDirectoryRepository.class);
        service = new TeacherTimeOffService(entries, teachers);
    }

    @Test
    void accrualStoresFiveMinuteDurationEventDateAndAccountAuthor() {
        TeacherDirectoryEntry teacher = teacher(10L, "Иванова Анна Петровна");
        when(teachers.findById(10L)).thenReturn(Optional.of(teacher));
        when(entries.save(any())).thenAnswer(invocation -> {
            TeacherTimeOffEntry entry = invocation.getArgument(0);
            entry.setId(100L);
            return entry;
        });

        TeacherTimeOffDtos.EntryView saved = service.addAccrual(
                new TeacherTimeOffDtos.AccrualRequest(
                        10L, 125, "Работа на олимпиаде", false, LocalDate.of(2026, 9, 12)),
                actor());

        assertEquals(TeacherTimeOffEntry.OperationType.EARNED, saved.operationType());
        assertEquals(125, saved.amountMinutes());
        assertEquals("Работа на олимпиаде", saved.reason());
        assertFalse(saved.lessonRemoval());
        assertEquals(LocalDate.of(2026, 9, 12), saved.eventDate());
        assertEquals("Кадровик", saved.createdByFio());
        assertNotNull(saved.createdAt());
        verify(entries).save(argThat(entry ->
                entry.getCreatedByUserId().equals(5L)
                        && entry.getCreatedByUsername().equals("hr")
                        && entry.getTeacherFioSnapshot().equals("Иванова Анна Петровна")));
    }

    @Test
    void accrualRejectsDurationThatIsNotMultipleOfFiveMinutes() {
        when(teachers.findById(10L)).thenReturn(Optional.of(teacher(10L, "Иванова А.П.")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.addAccrual(new TeacherTimeOffDtos.AccrualRequest(
                        10L, 62, "Работа", true, LocalDate.now()), actor()));

        assertEquals("Трудозатраты должны быть кратны 5 минутам", error.getMessage());
        verify(entries, never()).save(any());
    }

    @Test
    void useTimeOffConvertsOneDayToEightHoursAndChecksBalance() {
        TeacherDirectoryEntry teacher = teacher(10L, "Иванова А.П.");
        when(teachers.findById(10L)).thenReturn(Optional.of(teacher));
        when(entries.findAllByTeacherId(10L)).thenReturn(List.of(
                entry(teacher, TeacherTimeOffEntry.OperationType.EARNED, 960),
                entry(teacher, TeacherTimeOffEntry.OperationType.USED, 240)
        ));
        when(entries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TeacherTimeOffDtos.EntryView saved = service.useTimeOff(
                new TeacherTimeOffDtos.UsageRequest(10L, BigDecimal.ONE, LocalDate.of(2026, 9, 15)), actor());

        assertEquals(TeacherTimeOffEntry.OperationType.USED, saved.operationType());
        assertEquals(480, saved.amountMinutes());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.useTimeOff(new TeacherTimeOffDtos.UsageRequest(
                        10L, BigDecimal.valueOf(2), LocalDate.of(2026, 9, 16)), actor()));
        assertTrue(error.getMessage().startsWith("Недостаточно часов отгула"));
    }

    @Test
    void workspaceShowsEarnedUsedAndRemainingForEveryEmployee() {
        TeacherDirectoryEntry teacher = teacher(10L, "Иванова Анна Петровна");
        TeacherTimeOffEntry earned = entry(teacher, TeacherTimeOffEntry.OperationType.EARNED, 720);
        TeacherTimeOffEntry used = entry(teacher, TeacherTimeOffEntry.OperationType.USED, 480);
        when(entries.findAllByOrderByEventDateDescCreatedAtDesc()).thenReturn(List.of(used, earned));
        when(teachers.findAllById(List.of(10L))).thenReturn(List.of(teacher));

        TeacherTimeOffDtos.WorkspaceView result = service.workspace();

        assertEquals(1, result.summary().size());
        assertEquals(720, result.summary().get(0).earnedMinutes());
        assertEquals(480, result.summary().get(0).usedMinutes());
        assertEquals(240, result.summary().get(0).remainingMinutes());
        assertEquals(2, result.entries().size());
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private TeacherTimeOffEntry entry(TeacherDirectoryEntry teacher,
                                      TeacherTimeOffEntry.OperationType type,
                                      int minutes) {
        TeacherTimeOffEntry entry = new TeacherTimeOffEntry();
        entry.setTeacherId(teacher.getId());
        entry.setTeacherFioSnapshot(teacher.getFioTeacher());
        entry.setOperationType(type);
        entry.setAmountMinutes(minutes);
        entry.setReason(type == TeacherTimeOffEntry.OperationType.EARNED ? "Работа" : "Использование отгула");
        entry.setEventDate(LocalDate.of(2026, 9, 1));
        entry.setCreatedByUsername("hr");
        entry.setCreatedByFio("Кадровик");
        return entry;
    }

    private SessionUser actor() {
        return new SessionUser(
                5L, "hr", "Кадровик", null, null, UserRole.HR,
                true, true, true, null, false, new LinkedHashSet<>(), List.of());
    }
}
