package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentClassSizeOverrideRepository;
import org.school.personalLoad.repository.ContingentClassSizeSourceSettingRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;

import java.util.Map;
import java.util.List;
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

    @Test
    void kindergartenGroupsStayInformationalAndDoNotBecomeSalaryClasses() {
        ContingentSnapshotRepository snapshots = mock(ContingentSnapshotRepository.class);
        ContingentStudentRepository students = mock(ContingentStudentRepository.class);
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(5L);
        when(snapshots.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2025/2026"))
                .thenReturn(Optional.of(snapshot));
        when(students.findAllBySnapshotId(5L)).thenReturn(List.of(
                student("2-А"), student("2-А"), student("Старшая группа 16А"), student("ГКП 88А")
        ));
        ClassSizeServiceImpl service = new ClassSizeServiceImpl(
                snapshots,
                students,
                mock(ContingentClassSizeOverrideRepository.class),
                mock(ContingentClassSizeSourceSettingRepository.class),
                mock(ClassroomLeadershipRepository.class),
                mock(CurriculumPlanEntryRepository.class)
        );

        assertEquals(Map.of("2-а", 2), service.aisClassSizes("2025/2026"));
    }

    private ContingentStudent student(String className) {
        ContingentStudent student = new ContingentStudent();
        student.setClassName(className);
        return student;
    }
}
