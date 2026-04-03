package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceMemoTransferGenerationTest {

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
        lenient().when(changesDAO.findAll()).thenReturn(List.of());
    }

    @Test
    void transferCreatesPendingForDonorAndRecipient() {
        ManualLoadEntry donor = row("Иванов И.И.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donor, recipient));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.stream().anyMatch(p -> "Иванов И.И.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
        assertTrue(pending.stream().anyMatch(p -> "Петров П.П.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
    }

    @Test
    void donorWithoutCurrentRowsIsRecoveredFromRemovedHistoryChanges() {
        LocalDateTime changeTs = LocalDateTime.of(2025, 10, 11, 9, 0);
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(recipient));
        when(changesDAO.findAll()).thenReturn(List.of(
                change("Иванов И.И.", "Алгебра", "8-А", 6,
                        TarifficationChanges.ChangeType.REMOVED, changeTs),
                change("Петров П.П.", "Алгебра", "8-А", 6,
                        TarifficationChanges.ChangeType.ADDED, changeTs)
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.stream().anyMatch(p -> "Иванов И.И.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
        assertTrue(pending.stream().anyMatch(p -> "Петров П.П.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
    }

    @Test
    void transferOneClassOutOfFiveKeepsDonorMemoWithRemovedAndRemainingRows() {
        String donorFio = "Иванов И.И.";
        LocalDate transferDate = LocalDate.of(2025, 10, 11);

        ManualLoadEntry donorLeaving = row(donorFio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry donorRemaining1 = row(donorFio, "Алгебра", "8-Б", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining2 = row(donorFio, "Алгебра", "8-В", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining3 = row(donorFio, "Алгебра", "8-Г", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining4 = row(donorFio, "Алгебра", "8-Д", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                transferDate, LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(
                donorLeaving, donorRemaining1, donorRemaining2, donorRemaining3, donorRemaining4, recipient
        ));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        ServiceMemoDtos.PendingTeacher donorMemo = pending.stream()
                .filter(p -> donorFio.equals(p.getFioTeacher()) && transferDate.equals(p.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "8-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        long unchangedCount = donorMemo.getRows().stream()
                .filter(r -> r.getStatus() == null || r.getStatus().isBlank())
                .map(ServiceMemoDtos.LoadRow::getClassName)
                .filter(Objects::nonNull)
                .filter(className -> className.startsWith("8-"))
                .count();
        assertTrue(unchangedCount >= 4);
    }

    @Test
    void donorFallbackUsesLatestHistoryBatchOnly() {
        ManualLoadEntry recipient = row("Петров П.П.", "ИЗО", "1-А", 1,
                LocalDate.of(2025, 9, 14), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(recipient));
        when(changesDAO.findAll()).thenReturn(List.of(
                change("Архангельская Т.М.", "ИЗО", "1-А", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 9, 0)),
                change("Архангельская Т.М.", "ИЗО", "1-Е", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 9, 0)),
                change("Архангельская Т.М.", "ИЗО", "1-А", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 10, 0))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        ServiceMemoDtos.PendingTeacher donorMemo = pending.stream()
                .filter(p -> "Архангельская Т.М.".equals(p.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        long removedRows = donorMemo.getRows().stream()
                .filter(r -> "Снять".equals(r.getStatus()))
                .count();
        assertTrue(removedRows == 1);
        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
    }

    private ManualLoadEntry row(String fio, String subject, String className, int load,
                                LocalDate from, LocalDate to) {
        ManualLoadEntry entry = new ManualLoadEntry();
        entry.setFioTeacher(fio);
        entry.setSubjectName(subject);
        entry.setClassName(className);
        entry.setLoad(load);
        entry.setLoadFromDate(from);
        entry.setLoadToDate(to);
        return entry;
    }

    private TarifficationChanges change(String fio, String subject, String className, int load,
                                        TarifficationChanges.ChangeType type, LocalDateTime when) {
        TarifficationChanges change = new TarifficationChanges();
        change.setFioTeacher(fio);
        change.setSubjectName(subject);
        change.setClassName(className);
        change.setLoad(load);
        change.setChangeType(type);
        change.setChangeDate(when);
        return change;
    }
}
