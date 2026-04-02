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
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
}
