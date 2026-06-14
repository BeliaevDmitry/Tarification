package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;

@ExtendWith(MockitoExtension.class)
class TeacherDirectoryServiceImplFkCutoverTest {

    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;

    @Test
    void dismissalUsesTeacherIdAndCreatesVacancyRowWithVacancyTeacherId() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов И.И.");
        TeacherDirectoryEntry vacancy = teacher(99L, "Вакансия");
        ManualLoadEntry load = load(10L, 1L, "Иванов И.И.", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        TeacherDirectoryServiceImpl service = new TeacherDirectoryServiceImpl(teacherDirectoryRepository, manualLoadEntryRepository);
        when(teacherDirectoryRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(teacherDirectoryRepository.findByFioTeacherIgnoreCase("Вакансия")).thenReturn(Optional.of(vacancy));
        when(manualLoadEntryRepository.findByTeacherId(1L)).thenReturn(List.of(load));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(load));
        when(teacherDirectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markForDismissal(1L, LocalDate.of(2026, 1, 10), "admin");

        verify(manualLoadEntryRepository).findByTeacherId(1L);
        ArgumentCaptor<ManualLoadEntry> savedLoads = ArgumentCaptor.forClass(ManualLoadEntry.class);
        verify(manualLoadEntryRepository, org.mockito.Mockito.times(2)).save(savedLoads.capture());
        ManualLoadEntry vacancyRow = savedLoads.getAllValues().get(1);
        assertEquals(99L, vacancyRow.getTeacherId());
        assertEquals("Вакансия", vacancyRow.getFioTeacher());
        assertEquals(load.getClassId(), vacancyRow.getClassId());
        assertEquals(load.getMetaGroupId(), vacancyRow.getMetaGroupId());
        assertTrue(load.isDismissalAdjusted());
    }

    @Test
    void restoreAndDeleteChecksUseTeacherId() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов И.И.");
        ManualLoadEntry load = load(10L, 1L, "Старое ФИО", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 10));
        load.setDismissalAdjusted(true);
        load.setBackupLoadToDate(LocalDate.of(2026, 5, 31));
        TeacherDirectoryServiceImpl service = new TeacherDirectoryServiceImpl(teacherDirectoryRepository, manualLoadEntryRepository);
        when(teacherDirectoryRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(manualLoadEntryRepository.findByTeacherId(1L)).thenReturn(List.of(load));
        when(teacherDirectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.restore(1L);

        verify(manualLoadEntryRepository).findByTeacherId(1L);
        assertEquals(LocalDate.of(2026, 5, 31), load.getLoadToDate());

        when(manualLoadEntryRepository.existsByTeacherId(1L)).thenReturn(false);
        service.deleteById(1L);
        verify(manualLoadEntryRepository).existsByTeacherId(1L);
    }

    @Test
    void archivePreservesTeacherAndLoadRelations() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов И.И.");
        TeacherDirectoryServiceImpl service = new TeacherDirectoryServiceImpl(teacherDirectoryRepository, manualLoadEntryRepository);
        when(teacherDirectoryRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(teacherDirectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TeacherDirectoryEntry archived = service.archive(1L);

        assertTrue(archived.isArchived());
        assertTrue(archived.getArchivedAt() != null);
        verify(manualLoadEntryRepository, org.mockito.Mockito.never()).findByTeacherId(anyLong());

        TeacherDirectoryEntry restored = service.unarchive(1L);
        assertFalse(restored.isArchived());
        assertEquals(null, restored.getArchivedAt());
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private ManualLoadEntry load(Long id, Long teacherId, String fio, LocalDate from, LocalDate to) {
        ManualLoadEntry load = new ManualLoadEntry();
        load.setId(id);
        load.setTeacherId(teacherId);
        load.setFioTeacher(fio);
        load.setAcademicYear("2025/2026");
        load.setNumberSchoolBuilding("СП1");
        load.setSubjectName("Алгебра");
        load.setClassName("7-А");
        load.setClassId(701L);
        load.setLoad(6);
        load.setEducationLevel(EducationLevel.BASIC);
        load.setLoadFromDate(from);
        load.setLoadToDate(to);
        return load;
    }
}
