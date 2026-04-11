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

/*
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
    void doesNotCreateMemoForAcademicStartDate() {
        String fio = "Иванова И.И.";
        ManualLoadEntry row = row(fio, "Математика", "5-А", 10,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Математика", "5-А", 9, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 9, 1, 8, 0)),
                change(fio, "Математика", "5-А", 10, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 9, 1, 8, 5))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.isEmpty());
    }

    @Test
    void unchangedRowsWithNewPeriodBordersAreNotDuplicatedAsAddedAndRemoved() {
        String fio = "Архангельская Т.М.";
        LocalDate changeDate = LocalDate.of(2025, 9, 16);

        ManualLoadEntry beforeRemoved = row(fio, "Русский язык", "1-А", 5,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry beforeUnchanged = row(fio, "Русский язык", "1-Е", 5,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry afterUnchanged = row(fio, "Русский язык", "1-Е", 5,
                LocalDate.of(2025, 9, 16), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(beforeRemoved, beforeUnchanged, afterUnchanged));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher memo = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        long removedCount = memo.getRows().stream()
                .filter(r -> "Снять".equals(r.getStatus()))
                .count();
        assertEquals(1, removedCount);
        assertTrue(memo.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        assertFalse(memo.getRows().stream()
                .anyMatch(r -> "1-Е".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
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



    @Test
    void donorMemoContainsRemovedAndRemainingRowsOnTransferDate() {
        String donorFio = "Иванов И.И.";

        ManualLoadEntry donorLeaving = row(donorFio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry donorRemaining = row(donorFio, "Геометрия", "8-А", 4,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donorLeaving, donorRemaining, recipient));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        ServiceMemoDtos.PendingTeacher donorMemo = pending.stream()
                .filter(it -> donorFio.equals(it.getFioTeacher()))
                .filter(it -> LocalDate.of(2025, 10, 11).equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "Алгебра".equals(r.getSubjectName()) && "Снять".equals(r.getStatus())));
        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "Геометрия".equals(r.getSubjectName()) && (r.getStatus() == null || r.getStatus().isBlank())));
    }

    @Test
    void recipientWithoutPreviousLoadIsMarkedAsNewEmploymentOnlyForFirstAppearance() {
        String fio = "Новиков Н.Н.";
        ManualLoadEntry firstLoad = row(fio, "Информатика", "9-А", 8,
                LocalDate.of(2025, 11, 1), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(firstLoad));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        assertEquals("NEW", pending.getMemoType());
    }

    @Test
    void pureAdditionForExistingTeacherIsNotMarkedAsNewEmployment() {
        String fio = "Петров П.П.";
        ManualLoadEntry oldLoad = row(fio, "История", "6-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry newSubject = row(fio, "Обществознание", "6-Б", 3,
                LocalDate.of(2025, 10, 15), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(oldLoad, newSubject));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> LocalDate.of(2025, 10, 15).equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertEquals("CHANGED", pending.getMemoType());
    }

    @Test
    void firstLoadForTeacherExistingInDirectoryIsNotMarkedAsNewEmployment() {
        String fio = "Аулова Мария Владимировна";
        ManualLoadEntry firstLoad = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 10, 15), LocalDate.of(2026, 5, 31));

        TeacherDirectoryEntry directoryEntry = new TeacherDirectoryEntry();
        directoryEntry.setFioTeacher(fio);
        directoryEntry.setCreatedAt(LocalDateTime.of(2024, 1, 10, 9, 0));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(firstLoad));
        when(changesDAO.findAll()).thenReturn(List.of());
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of(directoryEntry));

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        assertEquals("CHANGED", pending.getMemoType());
    }

    @Test
    void donorRowsDifferingOnlyByEducationalPlanGroupAreNotDuplicatedAsRemoval() {
        String fio = "Иванов И.И.";
        LocalDate changeDate = LocalDate.of(2025, 10, 11);

        ManualLoadEntry removed = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        removed.setGroupNameEducationalPlan("Группа 1");
        ManualLoadEntry staying = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        staying.setGroupNameEducationalPlan("Группа 2");

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(removed, staying));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertEquals(0, pending.getRows().stream().filter(r -> "Снять".equals(r.getStatus())).count());
    }

    @Test
    void donorRowsDifferingOnlyBySchoolBuildingAreNotDuplicatedAsRemoval() {
        String fio = "Иванов И.И.";
        LocalDate changeDate = LocalDate.of(2025, 10, 11);

        ManualLoadEntry removed = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        removed.setNumberSchoolBuilding("1");
        ManualLoadEntry staying = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        staying.setNumberSchoolBuilding("2");

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(removed, staying));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertEquals(0, pending.getRows().stream().filter(r -> "Снять".equals(r.getStatus())).count());
    }

    @Test
    void donorRemovalDetectedWhenOneOfIdenticalRowsIsRemoved() {
        String fio = "Иванов И.И.";
        LocalDate changeDate = LocalDate.of(2025, 10, 11);

        ManualLoadEntry removed = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        removed.setNumberSchoolBuilding("1");
        removed.setGroupNameEducationalPlan("Группа 1");

        ManualLoadEntry staying = row(fio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        staying.setNumberSchoolBuilding("1");
        staying.setGroupNameEducationalPlan("Группа 1");

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(removed, staying));
        when(changesDAO.findAll()).thenReturn(List.of());

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, pending.getRows().stream().filter(r -> "Снять".equals(r.getStatus())).count());
    }

    @Test
    void donorRemovesOnlyClassesPresentInTarifficationChangesForDate() {
        String fio = "Алфёров Александр Викторович";
        LocalDate changeDate = LocalDate.of(2025, 9, 16);

        ManualLoadEntry oneAEnds = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry oneEActive = row(fio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry oneIActive = row(fio, "Изобразительное искусство", "1-И", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(oneAEnds, oneEActive, oneIActive));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Изобразительное искусство", "1-А", 1, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 9, 16, 9, 0))
        ));

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        long removed = pending.getRows().stream().filter(r -> "Снять".equals(r.getStatus())).count();
        assertEquals(1, removed);
        assertTrue(pending.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        assertFalse(pending.getRows().stream()
                .anyMatch(r -> "1-Е".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        assertFalse(pending.getRows().stream()
                .anyMatch(r -> "1-И".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
    }

    @Test
    void donorRemovalUsesLatestChangeBatchWithinDate() {
        String fio = "Бардина Наталья Николаевна";
        LocalDate changeDate = LocalDate.of(2025, 9, 16);

        ManualLoadEntry oneAEnds = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry oneEEnds = row(fio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));

        ManualLoadEntry oneAActive = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 16), LocalDate.of(2026, 5, 31));
        ManualLoadEntry oneEActive = row(fio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 16), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(oneAEnds, oneEEnds, oneAActive, oneEActive));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Изобразительное искусство", "1-Е", 1, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 9, 16, 9, 0)),
                change(fio, "Изобразительное искусство", "1-А", 1, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 9, 16, 10, 0))
        ));

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(pending.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        assertFalse(pending.getRows().stream()
                .anyMatch(r -> "1-Е".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
    }

    @Test
    void donorRemovalFallsBackToAnyLatestChangeTypesWhenRemovedTypeMissing() {
        String fio = "Бардина Наталья Николаевна";
        LocalDate changeDate = LocalDate.of(2025, 9, 16);

        ManualLoadEntry oneAEnds = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry oneEEnds = row(fio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 15));
        ManualLoadEntry oneAActive = row(fio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 16), LocalDate.of(2026, 5, 31));
        ManualLoadEntry oneEActive = row(fio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 16), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(oneAEnds, oneEEnds, oneAActive, oneEActive));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Изобразительное искусство", "1-А", 1, TarifficationChanges.ChangeType.MODIFIED,
                        LocalDateTime.of(2025, 9, 16, 10, 0))
        ));

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(it -> fio.equals(it.getFioTeacher()))
                .filter(it -> changeDate.equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(pending.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        assertFalse(pending.getRows().stream()
                .anyMatch(r -> "1-Е".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
    }


    @Test
    void laterTransferDoesNotCarryRemovedRowsFromEarlierMemo() {
        String donor = "Иванов И.И.";

        ManualLoadEntry algebra = row(donor, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry geometry = row(donor, "Геометрия", "8-А", 4,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 12, 10));
        ManualLoadEntry physics = row(donor, "Физика", "8-А", 3,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));

        ManualLoadEntry recipient1 = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));
        ManualLoadEntry recipient2 = row("Сидоров С.С.", "Геометрия", "8-А", 4,
                LocalDate.of(2025, 12, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(algebra, geometry, physics, recipient1, recipient2));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        ServiceMemoDtos.PendingTeacher donorFirst = pending.stream()
                .filter(it -> donor.equals(it.getFioTeacher()))
                .filter(it -> LocalDate.of(2025, 10, 11).equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();
        ServiceMemoDtos.PendingTeacher donorSecond = pending.stream()
                .filter(it -> donor.equals(it.getFioTeacher()))
                .filter(it -> LocalDate.of(2025, 12, 11).equals(it.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(donorFirst.getRows().stream().anyMatch(r -> "Алгебра".equals(r.getSubjectName()) && "Снять".equals(r.getStatus())));
        assertFalse(donorFirst.getRows().stream().anyMatch(r -> "Геометрия".equals(r.getSubjectName()) && "Снять".equals(r.getStatus())));

        assertTrue(donorSecond.getRows().stream().anyMatch(r -> "Геометрия".equals(r.getSubjectName()) && "Снять".equals(r.getStatus())));
        assertFalse(donorSecond.getRows().stream().anyMatch(r -> "Алгебра".equals(r.getSubjectName()) && "Снять".equals(r.getStatus())));
        assertTrue(donorSecond.getRows().stream().anyMatch(r -> "Физика".equals(r.getSubjectName()) && (r.getStatus() == null || r.getStatus().isBlank())));
    }

    @Test
    void secondGenerationWithoutChangesDoesNotCreateDuplicateMemo() {
        String fio = "Иванова И.И.";
        ManualLoadEntry row = row(fio, "Математика", "5-А", 12,
                LocalDate.of(2025, 10, 5), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, "Математика", "5-А", 11, TarifficationChanges.ChangeType.REMOVED,
                        LocalDateTime.of(2025, 10, 5, 10, 0)),
                change(fio, "Математика", "5-А", 12, TarifficationChanges.ChangeType.ADDED,
                        LocalDateTime.of(2025, 10, 5, 10, 1))
        ));

        List<ServiceMemo> stored = new ArrayList<>();
        when(serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(any())).thenAnswer(invocation -> stored.stream()
                .sorted(Comparator.comparing(ServiceMemo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());
        when(serviceMemoRepository.save(any(ServiceMemo.class))).thenAnswer(invocation -> {
            ServiceMemo memo = invocation.getArgument(0);
            memo.setId((long) (stored.size() + 1));
            if (memo.getCreatedAt() == null) {
                memo.setCreatedAt(LocalDateTime.now());
            }
            stored.add(memo);
            return memo;
        });

        String teacherToken = service.findPendingTeachers().get(0).getTeacherKey();
        List<ServiceMemoDtos.ProcessedMemo> created = service.generateForTeachers(List.of(teacherToken), "Зам директора");
        assertEquals(1, created.size());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateForTeachers(List.of(teacherToken), "Зам директора"));
        assertTrue(ex.getMessage().contains("нет новых изменений"));
        assertEquals(1, stored.size());
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
*/