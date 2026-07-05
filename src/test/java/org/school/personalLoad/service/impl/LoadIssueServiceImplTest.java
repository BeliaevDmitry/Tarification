package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.LoadIssueDtos;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.LoadIssueStateRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadIssueServiceImplTest {

    @Mock
    private ClassroomLeadershipRepository classroomRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadRepository;
    @Mock
    private LoadIssueStateRepository stateRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumRepository;

    private LoadIssueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoadIssueServiceImpl(classroomRepository, manualLoadRepository, stateRepository, curriculumRepository);
        when(classroomRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(classroom()));
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(stateRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void doesNotReportRequiredSubjectMissingFromCurriculum() {
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        assertTrue(response.rows().stream().noneMatch(row -> row.type().equals("Россия мои горизонты")));
    }

    @Test
    void reportsUnassignedRequiredSubjectThatExistsInCurriculum() {
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(curriculum("Россия мои горизонты")));

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        LoadIssueDtos.LoadIssueRow issue = response.rows().stream()
                .filter(row -> row.type().equals("Россия мои горизонты"))
                .findFirst()
                .orElseThrow();
        assertEquals("3-Б", issue.targetClass());
        assertTrue(issue.description().contains("в нагрузке по предмету стоит: не назначено"));
    }

    @Test
    void doesNotReportRussiaHorizonsAssignedToClassTeacher() {
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(manualLoad("Россия мои горизонты")));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(curriculum("Россия мои горизонты")));

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        assertTrue(response.rows().stream().noneMatch(row -> row.type().equals("Россия мои горизонты")));
    }

    private ClassroomLeadershipEntry classroom() {
        ClassroomLeadershipEntry row = new ClassroomLeadershipEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП1");
        row.setClassName("3-Б");
        row.setFioTeacher("Белогур Кристина Игоревна");
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(1L);
        teacher.setFioTeacher(row.getFioTeacher());
        row.setTeacher(teacher);
        return row;
    }

    private CurriculumPlanEntry curriculum(String subjectName) {
        CurriculumPlanEntry row = new CurriculumPlanEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП1");
        row.setClassName("3-Б");
        row.setSubjectName(subjectName);
        row.setPlannedHours(BigDecimal.ONE);
        row.setCurriculumPart(CurriculumPart.EXTRACURRICULAR);
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        return row;
    }

    private ManualLoadEntry manualLoad(String subjectName) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП1");
        row.setClassName("3-Б");
        row.setSubjectName(subjectName);
        row.setFioTeacher("Белогур Кристина Игоревна");
        row.setTeacherId(1L);
        row.setLoad(1);
        row.setCurriculumPart(CurriculumPart.EXTRACURRICULAR);
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        return row;
    }
}
