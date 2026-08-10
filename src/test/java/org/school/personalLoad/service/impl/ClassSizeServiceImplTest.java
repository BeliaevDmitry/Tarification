package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentClassSizeOverrideRepository;
import org.school.personalLoad.repository.ContingentClassSizeSourceSettingRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassSizeServiceImplTest {

    @Test
    void contingentFailureDoesNotDisableLegacySalaryAndExports() {
        ContingentSnapshotRepository snapshots = mock(ContingentSnapshotRepository.class);
        when(snapshots.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenThrow(new IllegalStateException("old contingent schema"));
        ClassSizeServiceImpl service = new ClassSizeServiceImpl(
                snapshots,
                mock(ContingentStudentRepository.class),
                mock(ContingentClassSizeOverrideRepository.class),
                mock(ContingentClassSizeSourceSettingRepository.class),
                mock(ClassroomLeadershipRepository.class),
                mock(CurriculumPlanEntryRepository.class)
        );

        Map<String, Integer> sizes = service.aisClassSizes("2025/2026");

        assertEquals(Map.of(), sizes);
    }

    @Test
    void contingentSourceSettingsFailureStillReturnsImportedSizes() {
        ContingentSnapshotRepository snapshots = mock(ContingentSnapshotRepository.class);
        ContingentClassSizeSourceSettingRepository settings = mock(ContingentClassSizeSourceSettingRepository.class);
        when(snapshots.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.empty());
        when(settings.findByAcademicYear("2025/2026"))
                .thenThrow(new IllegalStateException("old class-size settings schema"));
        ClassSizeServiceImpl service = new ClassSizeServiceImpl(
                snapshots,
                mock(ContingentStudentRepository.class),
                mock(ContingentClassSizeOverrideRepository.class),
                settings,
                mock(ClassroomLeadershipRepository.class),
                mock(CurriculumPlanEntryRepository.class)
        );

        Map<String, Integer> sizes = service.effectiveClassSizes("2025/2026");

        assertEquals(Map.of(), sizes);
    }
}
