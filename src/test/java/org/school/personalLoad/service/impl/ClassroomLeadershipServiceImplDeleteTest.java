package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
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
    void updateOneChangesBuildingByIdAndPropagatesLoadAndCurriculumTails() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        ClassroomLeadershipEntryRequest request = new ClassroomLeadershipEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding("СП2|ЛЕНИНА,Д.1");
        request.setClassName("7-А");
        request.setClassDirection("Инженерный");
        request.setFioTeacher("Петров П.П.");
        request.setCampusAddress("Ленина, д.1");
        request.setClassType("REGULAR");

        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setFioTeacher("Петров П.П.");
        SchoolBuilding building = new SchoolBuilding();
        building.setId(36L);
        building.setCode("СП-2");
        building.setName("СП2");
        building.setAddress("Ленина, д.1");
        building.setManagerFio("Директор");

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findByFioTeacher("Петров П.П.")).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findByCode("СП2")).thenReturn(Optional.empty());
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(building));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of());
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП2", saved.getNumberSchoolBuilding());
        assertEquals("Инженерный", saved.getClassDirection());
        assertEquals("Петров П.П.", saved.getFioTeacher());
        assertEquals("Ленина, д.1", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП2");
        verify(curriculumPlanEntryRepository).renameClassEverywhere("2026/2027", "7-А", "7-А", "СП2");
        verify(manualLoadEntryRepository).renameClassEverywhere("2026/2027", "7-А", "7-А", "СП2");
        verify(schoolBuildingRepository, never()).save(any(SchoolBuilding.class));
    }

    @Test
    void updateOneKeepsSpWhenPhysicalSiteBelongsToSameSp() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 10L);

        stubSuccessfulUpdate(entry, request, sp1, sp1);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(10L, saved.getSchoolBuildingId());
        assertEquals("Обручева, д.1", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП1");
    }

    @Test
    void updateOneKeepsSp1WhenPhysicalSiteBelongsToSp2() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp2Site = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 36L);

        stubSuccessfulUpdate(entry, request, sp1, sp2Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
        assertEquals("Марии Ульяновой, д.5А", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП1");
    }

    @Test
    void updateOneKeepsSp1WhenPhysicalSiteBelongsToSp3() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp3Site = schoolBuilding(77L, "СП3", "СП3", "Кравченко, д.14, корп.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 77L);

        stubSuccessfulUpdate(entry, request, sp1, sp3Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(77L, saved.getSchoolBuildingId());
        assertEquals("Кравченко, д.14, корп.1", saved.getCampusAddress());
    }

    @Test
    void changingPhysicalSiteDoesNotChangeClassSp() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        entry.setSchoolBuilding(schoolBuilding(10L, "СП1", "СП1", "Обручева, д.1"));
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        SchoolBuilding sp2Site = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 36L);

        stubSuccessfulUpdate(entry, request, sp1, sp2Site);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП1", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
    }

    @Test
    void changingClassSpDoesNotChangePhysicalSiteAutomatically() {
        SchoolBuilding originalSite = schoolBuilding(36L, "СП2", "СП2", "Марии Ульяновой, д.5А");
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        entry.setSchoolBuilding(originalSite);
        SchoolBuilding sp3 = schoolBuilding(3L, "СП3", "СП3", "Кравченко, д.14, корп.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП3", 36L);

        stubSuccessfulUpdate(entry, request, sp3, originalSite);

        ClassroomLeadershipEntry saved = service.updateOne(42L, request);

        assertEquals("СП3", saved.getNumberSchoolBuilding());
        assertEquals(36L, saved.getSchoolBuildingId());
        assertEquals("Марии Ульяновой, д.5А", saved.getCampusAddress());
        verify(classroomLeadershipRepository).updateBuildingGroupById(42L, "СП3");
    }

    @Test
    void updateOneRejectsUnknownSchoolBuildingId() {
        ClassroomLeadershipEntry entry = classEntry(42L, "СП1", "7-А");
        SchoolBuilding sp1 = schoolBuilding(1L, "СП1", "СП1", "Обручева, д.1");
        ClassroomLeadershipEntryRequest request = updateRequest("СП1", 999L);

        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findByFioTeacher("Петров П.П.")).thenReturn(Optional.of(new TeacherDirectoryEntry()));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.of(sp1));
        when(schoolBuildingRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateOne(42L, request));

        assertEquals("Площадка не найдена: 999", error.getMessage());
        verify(classroomLeadershipRepository, never()).save(any(ClassroomLeadershipEntry.class));
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

    private void stubSuccessfulUpdate(ClassroomLeadershipEntry entry, ClassroomLeadershipEntryRequest request, SchoolBuilding spBuilding, SchoolBuilding physicalSite) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setFioTeacher("Петров П.П.");
        when(classroomLeadershipRepository.findById(42L)).thenReturn(Optional.of(entry));
        when(teacherDirectoryRepository.findByFioTeacher("Петров П.П.")).thenReturn(Optional.of(teacher));
        when(schoolBuildingRepository.findByCode(request.getNumberSchoolBuilding())).thenReturn(Optional.of(spBuilding));
        when(schoolBuildingRepository.findById(request.getSchoolBuildingId())).thenReturn(Optional.of(physicalSite));
        when(classroomLeadershipRepository.save(entry)).thenReturn(entry);
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of());
        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
    }

    private ClassroomLeadershipEntryRequest updateRequest(String numberSchoolBuilding, Long schoolBuildingId) {
        ClassroomLeadershipEntryRequest request = new ClassroomLeadershipEntryRequest();
        request.setAcademicYear("2026/2027");
        request.setNumberSchoolBuilding(numberSchoolBuilding);
        request.setSchoolBuildingId(schoolBuildingId);
        request.setClassName("7-А");
        request.setClassDirection("Инженерный");
        request.setFioTeacher("Петров П.П.");
        request.setCampusAddress("legacy ignored");
        request.setClassType("NORMAL");
        return request;
    }

    private SchoolBuilding schoolBuilding(Long id, String code, String name, String address) {
        SchoolBuilding building = new SchoolBuilding();
        building.setId(id);
        building.setCode(code);
        building.setName(name);
        building.setAddress(address);
        building.setManagerFio("Директор");
        return building;
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
