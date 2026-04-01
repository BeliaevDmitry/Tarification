package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceMemoServiceImplTest {

    @Mock
    private TarifficationChangesDAO changesDAO;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private ServiceMemoRepository serviceMemoRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;

    private ServiceMemoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceMemoServiceImpl(
                changesDAO,
                manualLoadEntryRepository,
                teacherDirectoryRepository,
                serviceMemoRepository,
                studyPeriodSettingService
        );

        lenient().when(studyPeriodSettingService.rangesByKey()).thenReturn(Map.of(
                StudyPeriodSettingKey.YEAR_1_9,
                new StudyPeriodSettingService.DateRange(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31))
        ));
        lenient().when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        lenient().when(serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(any())).thenReturn(List.of());

        AtomicLong seq = new AtomicLong(1);
        lenient().when(serviceMemoRepository.save(any(ServiceMemo.class))).thenAnswer(invocation -> {
            ServiceMemo memo = invocation.getArgument(0);
            if (memo.getId() == null) {
                memo.setId(seq.getAndIncrement());
            }
            return memo;
        });
    }

    @Test
    void pendingIsSeparatedByChangeDateForSameTeacher() {
        String fio = "Иванова И.И.";

        ManualLoadEntry row1 = row(fio, "Математика", "5-А", 10,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 4));
        ManualLoadEntry row2 = row(fio, "Математика", "5-А", 12,
                LocalDate.of(2025, 10, 5), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row1, row2));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Математика", "5-А", 10, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 10, 1, 9, 0)),
                change(fio, "Математика", "5-А", 11, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 10, 1, 9, 1)),
                change(fio, "Математика", "5-А", 11, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 10, 5, 10, 0)),
                change(fio, "Математика", "5-А", 12, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 10, 5, 10, 1))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertEquals(2, pending.size());
        assertEquals(LocalDate.of(2025, 10, 1), pending.get(0).getStartDate());
        assertEquals(LocalDate.of(2025, 10, 5), pending.get(1).getStartDate());
        assertNotEquals(pending.get(0).getTeacherKey(), pending.get(1).getTeacherKey());
        assertTrue(pending.get(0).getRows().stream().anyMatch(row -> Objects.equals(row.getLoad(), 10)));
        assertTrue(pending.get(1).getRows().stream().anyMatch(row -> Objects.equals(row.getLoad(), 12)));
        assertFalse(pending.get(0).getRows().stream().anyMatch(row -> Objects.equals(row.getLoad(), 12)));

        assertFalse(pending.get(0).getRows().isEmpty());
        assertFalse(pending.get(1).getRows().isEmpty());
    }

    @Test
    void generateCreatesOnlySelectedTeacherDateMemo() {
        String fio = "Иванова И.И.";
        ManualLoadEntry row = row(fio, "Математика", "5-А", 12,
                LocalDate.of(2025, 10, 5), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Математика", "5-А", 11, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 10, 5, 10, 0)),
                change(fio, "Математика", "5-А", 12, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 10, 5, 10, 1)),
                change(fio, "Математика", "5-А", 10, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 10, 1, 9, 0)),
                change(fio, "Математика", "5-А", 11, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 10, 1, 9, 1))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        String selectedToken = pending.stream()
                .filter(it -> LocalDate.of(2025, 10, 5).equals(it.getStartDate()))
                .findFirst()
                .orElseThrow()
                .getTeacherKey();

        List<ServiceMemoDtos.ProcessedMemo> created = service.generateForTeachers(List.of(selectedToken), "Зам директора");

        assertEquals(1, created.size());
        assertEquals(LocalDate.of(2025, 10, 5), created.get(0).getStartDate());

        ArgumentCaptor<ServiceMemo> captor = ArgumentCaptor.forClass(ServiceMemo.class);
        verify(serviceMemoRepository, atLeastOnce()).save(captor.capture());
        assertEquals(LocalDate.of(2025, 10, 5), captor.getValue().getChangeStartDate());
        assertTrue(captor.getValue().getGeneratedFilename().contains("2025-10-05"));
    }

    @Test
    void doesNotCreatePendingOnlyBecauseInitialAcademicDayLoadExists() {
        String fio = "Петров П.П.";
        ManualLoadEntry initial = row(fio, "История", "7-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(initial));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.isEmpty());
    }

    @Test
    void transferFromOneTeacherToDifferentTeachersOnDifferentDatesCreatesSeparateLogicalMemos() {
        ManualLoadEntry donor = row("Иванов И.И.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry recipient1 = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2025, 12, 15));
        ManualLoadEntry recipient2 = row("Сидоров С.С.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 12, 16), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donor, recipient1, recipient2));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertEquals(4, pending.size());

        assertMemo(pending, "Иванов И.И.", LocalDate.of(2025, 10, 11), "Снять");
        assertMemo(pending, "Петров П.П.", LocalDate.of(2025, 10, 11), "Добавить");
        assertMemo(pending, "Петров П.П.", LocalDate.of(2025, 12, 16), "Снять");
        assertMemo(pending, "Сидоров С.С.", LocalDate.of(2025, 12, 16), "Добавить");
    }

    private ManualLoadEntry row(String fio, String subject, String className, int load, LocalDate from, LocalDate to) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setFioTeacher(fio);
        row.setSubjectName(subject);
        row.setClassName(className);
        row.setLoad(load);
        row.setNumberSchoolBuilding("1");
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        row.setLoadFromDate(from);
        row.setLoadToDate(to);
        row.setCreatedAt(from.atStartOfDay());
        return row;
    }

    private TarifficationChanges change(String fio,
                                        String subject,
                                        String className,
                                        int load,
                                        TarifficationChanges.ChangeType type,
                                        LocalDateTime changeAt) {
        TarifficationChanges change = new TarifficationChanges();
        change.setFioTeacher(fio);
        change.setSubjectName(subject);
        change.setClassName(className);
        change.setLoad(load);
        change.setChangeType(type);
        change.setChangeDate(changeAt);
        return change;
    }

    private void assertMemo(List<ServiceMemoDtos.PendingTeacher> pending, String fio, LocalDate date, String expectedStatus) {
        ServiceMemoDtos.PendingTeacher memo = pending.stream()
                .filter(row -> fio.equals(row.getFioTeacher()))
                .filter(row -> date.equals(row.getStartDate()))
                .findFirst()
                .orElseThrow();
        assertTrue(memo.getRows().stream().anyMatch(row -> expectedStatus.equals(row.getStatus())));
    }
}
