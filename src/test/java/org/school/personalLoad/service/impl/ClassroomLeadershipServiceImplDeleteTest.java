package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomLeadershipServiceImplDeleteTest {

    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;

    private ClassroomLeadershipServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomLeadershipServiceImpl(
                classroomLeadershipRepository,
                teacherDirectoryRepository,
                schoolBuildingRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository
        );
    }

    @Test
    void deleteOneRemovesClassAndAllLoadAndCurriculumTails() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП3", "7-А");
        when(classroomLeadershipRepository.findByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А"))
                .thenReturn(Optional.of(entry));

        service.deleteOne("2026/2027", "СП3", "7-А");

        verify(curriculumPlanEntryRepository).deleteByAcademicYearAndClassId("2026/2027", 42L);
        verify(manualLoadEntryRepository).deleteByAcademicYearAndClassIds("2026/2027", List.of(42L));
        verify(curriculumPlanEntryRepository).deleteByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А");
        verify(manualLoadEntryRepository).deleteByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А");
        verify(classroomLeadershipRepository).deleteByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А");
    }

    @Test
    void dependencySummaryCountsLoadAndCurriculumBeforeDelete() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП3", "7-А");
        when(classroomLeadershipRepository.findByAcademicYearAndNumberSchoolBuildingAndClassName("2026/2027", "СП3", "7-А"))
                .thenReturn(Optional.of(entry));
        when(curriculumPlanEntryRepository.countClassTails("2026/2027", 42L, "СП3", "7-А")).thenReturn(5L);
        when(manualLoadEntryRepository.countClassTails("2026/2027", 42L, "СП3", "7-А")).thenReturn(3L);

        Map<String, Object> summary = service.dependencySummary("2026/2027", "СП3", "7-А");

        assertEquals(5L, summary.get("curriculumRows"));
        assertEquals(3L, summary.get("manualLoadRows"));
        assertEquals(8L, summary.get("totalRows"));
    }

    private ClassroomLeadershipEntry classEntry(Long id, String building, String className) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setId(id);
        entry.setAcademicYear("2026/2027");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection("Универсальный");
        entry.setFioTeacher("Иванов И.И.");
        return entry;
    }
}
