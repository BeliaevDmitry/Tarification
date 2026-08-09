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
import org.school.personalLoad.model.EmploymentContract;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.LoadInRateRule;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.LoadIssueStateRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.EmploymentContractRepository;
import org.school.personalLoad.repository.LoadInRateRuleRepository;
import org.school.personalLoad.service.LoadInRateSubjectService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;

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
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private EmploymentContractRepository employmentContractRepository;
    @Mock
    private LoadInRateRuleRepository loadInRateRuleRepository;
    @Mock
    private LoadInRateSubjectService loadInRateSubjectService;

    private LoadIssueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoadIssueServiceImpl(classroomRepository, manualLoadRepository, stateRepository,
                curriculumRepository, teacherRepository, employmentContractRepository, loadInRateRuleRepository,
                loadInRateSubjectService);
        when(classroomRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(classroom()));
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(stateRepository.findAll()).thenReturn(List.of());
        when(teacherRepository.findAll()).thenReturn(List.of(teacher(1L, "Белогур Кристина Игоревна")));
        when(employmentContractRepository.findAllByActiveTrueOrderByTeacherIdAsc()).thenReturn(List.of());
        when(loadInRateRuleRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(loadInRateSubjectService.allowedByRuleIds(anyCollection())).thenReturn(Map.of());
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
    void reportsUnconfirmedInRateAllocationAsBlockingIssue() {
        EmploymentContract contract = new EmploymentContract();
        contract.setId(20L);
        contract.setTeacherId(1L);
        contract.setActive(true);
        contract.setPrimaryContract(true);
        contract.setPositionName("Преподаватель ОБЗР");
        contract.setLoadHoursMayBeIncludedInRate(false);
        LoadInRateRule rule = new LoadInRateRule();
        rule.setId(21L);
        rule.setName("Преподаватель ОБЗР");
        rule.setDocumentLabel("Преподаватель ОБЗР");
        rule.setActive(true);
        ManualLoadEntry load = new ManualLoadEntry();
        load.setId(30L);
        load.setAcademicYear("2026/2027");
        load.setTeacherId(1L);
        load.setFioTeacher("Белогур Кристина Игоревна");
        load.setNumberSchoolBuilding("СП1");
        load.setSubjectName("ОБЗР");
        load.setClassName("7-А");
        load.setLoad(4);
        when(employmentContractRepository.findAllByActiveTrueOrderByTeacherIdAsc()).thenReturn(List.of(contract));
        when(loadInRateRuleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(rule));
        Map<Long, List<LoadInRateSubjectService.AllowedSubject>> allowed =
                Map.of(21L, List.of(new LoadInRateSubjectService.AllowedSubject(101L, "ОБЗР")));
        when(loadInRateSubjectService.allowedByRuleIds(anyCollection())).thenReturn(allowed);
        when(loadInRateSubjectService.allows(eq(21L), nullable(Long.class), eq("ОБЗР"), same(allowed)))
                .thenReturn(true);
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        LoadIssueDtos.LoadIssueRow issue = response.rows().stream()
                .filter(row -> row.type().equals("Не распределены часы внутри ставки"))
                .findFirst().orElseThrow();
        assertEquals("inRate", issue.targetPage());
        assertTrue(issue.description().contains("распределите 4 ч."));
    }

    @Test
    void doesNotReportSubjectThatIsNotAllowedForThePositionRate() {
        EmploymentContract contract = new EmploymentContract();
        contract.setId(20L);
        contract.setTeacherId(1L);
        contract.setActive(true);
        contract.setPositionName("Преподаватель ОБЗР");
        LoadInRateRule rule = new LoadInRateRule();
        rule.setId(21L);
        rule.setName("Преподаватель ОБЗР");
        rule.setActive(true);
        ManualLoadEntry load = manualLoad("Математика");
        load.setId(30L);
        load.setLoad(4);

        when(employmentContractRepository.findAllByActiveTrueOrderByTeacherIdAsc()).thenReturn(List.of(contract));
        when(loadInRateRuleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(rule));
        Map<Long, List<LoadInRateSubjectService.AllowedSubject>> allowed =
                Map.of(21L, List.of(new LoadInRateSubjectService.AllowedSubject(101L, "ОБЗР")));
        when(loadInRateSubjectService.allowedByRuleIds(anyCollection())).thenReturn(allowed);
        when(loadInRateSubjectService.allows(eq(21L), nullable(Long.class), eq("Математика"), same(allowed)))
                .thenReturn(false);
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        assertTrue(response.rows().stream()
                .noneMatch(row -> row.type().equals("Не распределены часы внутри ставки")));
    }

    @Test
    void doesNotReportRussiaHorizonsAssignedToClassTeacher() {
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(manualLoad("Россия мои горизонты")));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(curriculum("Россия мои горизонты")));

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        assertTrue(response.rows().stream().noneMatch(row -> row.type().equals("Россия мои горизонты")));
    }

    @Test
    void reportsDismissedTeacherLoadWithoutHandoff() {
        TeacherDirectoryEntry dismissed = teacher(2L, "Иванов Иван Иванович");
        dismissed.setDismissalDate(LocalDate.of(2026, 1, 10));
        ManualLoadEntry source = manualLoad("Алгебра", dismissed.getFioTeacher(), 2L,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        when(teacherRepository.findAll()).thenReturn(List.of(teacher(1L, "Белогур Кристина Игоревна"), dismissed));
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(source));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        LoadIssueDtos.LoadIssueRow issue = response.rows().stream()
                .filter(row -> row.type().equals("Не закрыта нагрузка после увольнения"))
                .findFirst()
                .orElseThrow();
        assertEquals("3-Б", issue.targetClass());
        assertEquals("Алгебра", issue.targetSubject());
        assertTrue(issue.description().contains("с 2026-01-11 по 2026-05-31"));
    }

    @Test
    void doesNotReportDismissedTeacherLoadFullyHandedOff() {
        TeacherDirectoryEntry dismissed = teacher(2L, "Иванов Иван Иванович");
        dismissed.setDismissalDate(LocalDate.of(2026, 1, 10));
        ManualLoadEntry source = manualLoad("Алгебра", dismissed.getFioTeacher(), 2L,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry handoff = manualLoad("Алгебра", "Петров Петр Петрович", 3L,
                LocalDate.of(2026, 1, 11), LocalDate.of(2026, 5, 31));
        when(teacherRepository.findAll()).thenReturn(List.of(teacher(1L, "Белогур Кристина Игоревна"), dismissed));
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(source, handoff));
        when(curriculumRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        LoadIssueDtos.LoadIssueResponse response = service.findIssues("2026/2027", "");

        assertTrue(response.rows().stream().noneMatch(row -> row.type().equals("Не закрыта нагрузка после увольнения")));
    }

    private ClassroomLeadershipEntry classroom() {
        ClassroomLeadershipEntry row = new ClassroomLeadershipEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП1");
        row.setClassName("3-Б");
        row.setFioTeacher("Белогур Кристина Игоревна");
        row.setTeacher(teacher(1L, row.getFioTeacher()));
        return row;
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
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
        return manualLoad(subjectName, "Белогур Кристина Игоревна", 1L, null, null);
    }

    private ManualLoadEntry manualLoad(String subjectName, String fioTeacher, Long teacherId, LocalDate loadFromDate, LocalDate loadToDate) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП1");
        row.setClassName("3-Б");
        row.setSubjectName(subjectName);
        row.setFioTeacher(fioTeacher);
        row.setTeacherId(teacherId);
        row.setLoad(1);
        row.setCurriculumPart(CurriculumPart.EXTRACURRICULAR);
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(StudyPeriod.YEAR);
        row.setLoadFromDate(loadFromDate);
        row.setLoadToDate(loadToDate);
        return row;
    }
}
