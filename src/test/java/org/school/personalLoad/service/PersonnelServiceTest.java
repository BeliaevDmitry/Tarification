package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.PersonnelDtos.AcceptEmployeeRequest;
import org.school.personalLoad.dto.PersonnelDtos.NameCases;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.MckoCertificate;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.EmploymentContract;
import org.school.personalLoad.model.LoadInRateRule;
import org.school.personalLoad.pa.model.PaSpecification;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonnelServiceTest {
    private final TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
    private final ManualLoadEntryRepository loads = mock(ManualLoadEntryRepository.class);
    private final ClassroomLeadershipRepository classes = mock(ClassroomLeadershipRepository.class);
    private final HrServiceMemoRepository hrMemos = mock(HrServiceMemoRepository.class);
    private final ServiceMemoRepository loadMemos = mock(ServiceMemoRepository.class);
    private final MckoCertificateRepository mcko = mock(MckoCertificateRepository.class);
    private final HrPersonalDataRepository personal = mock(HrPersonalDataRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final LoadInRateRuleRepository rules = mock(LoadInRateRuleRepository.class);
    private final LoadSalaryCalculationService salary = mock(LoadSalaryCalculationService.class);
    private final PaSpecificationRepository paSpecifications = mock(PaSpecificationRepository.class);
    private final PaReportVersionRepository paVersions = mock(PaReportVersionRepository.class);
    private final PaReportAnalysisSummaryRepository paSummaries = mock(PaReportAnalysisSummaryRepository.class);
    private final PaReportStudentResultRepository paStudents = mock(PaReportStudentResultRepository.class);
    private final PersonnelService service = new PersonnelService(
            teachers, loads, classes, hrMemos, loadMemos, mcko, personal, contracts, rules, salary,
            paSpecifications, paVersions, paSummaries, paStudents);

    @Test
    void personnelUsesStableApiRowsWithoutHibernateInternals() throws Exception {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(13L);
        teacher.setFioTeacher("Абдрахманова Анастасия Робертовна");
        teacher.setPrimaryPosition("Учитель");
        when(classes.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(hrMemos.findAllByAcademicYearOrderByCreatedAtDesc("2026/2027")).thenReturn(List.of());
        when(teachers.findAll()).thenReturn(List.of(teacher));

        var rows = service.personnel("2026/2027");
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(rows);

        assertEquals(13L, rows.get(0).id());
        assertEquals("", rows.get(0).additionalDutiesSummary());
        assertFalse(json.contains("hibernateLazyInitializer"));
        assertFalse(json.contains("\"handler\""));
    }

    @Test
    void vacancyIsAcceptedUnderSameTeacherIdAndLoadGetsNewName() {
        TeacherDirectoryEntry vacancy = new TeacherDirectoryEntry();
        vacancy.setId(42L);
        vacancy.setFioTeacher("Вакансия Рысь");
        ManualLoadEntry load = new ManualLoadEntry();
        load.setTeacherId(42L);
        load.setFioTeacher("Вакансия Рысь");
        MckoCertificate certificate = new MckoCertificate();
        certificate.setTeacherId(42L);
        certificate.setTeacherFioSnapshot("Вакансия Рысь");
        PaSpecification specification = new PaSpecification();
        specification.setTeacherFio("Вакансия Рысь");
        when(teachers.findById(42L)).thenReturn(Optional.of(vacancy));
        when(teachers.findByFioTeacherIgnoreCase("Рысь Виктория Игоревна")).thenReturn(Optional.empty());
        when(teachers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loads.findByTeacherId(42L)).thenReturn(List.of(load));
        when(classes.findAll()).thenReturn(List.of());
        when(loadMemos.findAll()).thenReturn(List.of());
        when(mcko.findAllByTeacherId(42L)).thenReturn(List.of(certificate));
        when(paSpecifications.findAll()).thenReturn(List.of(specification));
        when(paVersions.findAll()).thenReturn(List.of());
        when(paSummaries.findAll()).thenReturn(List.of());
        when(paStudents.findAll()).thenReturn(List.of());
        when(personal.findByTeacherId(42L)).thenReturn(Optional.empty());

        var result = service.acceptEmployee(new AcceptEmployeeRequest(
                42L, "Рысь Виктория Игоревна", "+7 900 000-00-00", "rys@example.test",
                "СП1", "Учитель", "Основное место работы", LocalDate.of(2026, 9, 1),
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, false, null, null
        ), "admin");

        assertEquals(42L, result.teacherId());
        assertTrue(result.linkedToVacancy());
        assertEquals("Рысь Виктория Игоревна", vacancy.getFioTeacher());
        assertEquals("Рысь Виктории Игоревне", vacancy.getFioTeacherDative());
        assertEquals("Рысь Виктория Игоревна", load.getFioTeacher());
        assertEquals("Рысь Виктория Игоревна", certificate.getTeacherFioSnapshot());
        assertEquals("Рысь Виктория Игоревна", specification.getTeacherFio());
        assertEquals("РЫСЬ ВИКТОРИЯ ИГОРЕВНА", specification.getTeacherFioNormalized());
        verify(loads).save(load);
    }

    @Test
    void automaticCasesIncludeDocumentForms() {
        var cases = RussianNameCases.derive("Носкова Светлана Николаевна");
        assertEquals("Носкова С.Н.", cases.initials());
        assertEquals("Носковой С.Н.", cases.initialsGenitive());
        assertEquals("Носковой С.Н.", cases.initialsDative());
        assertEquals("Носковой Светлане Николаевне", cases.dative());
        assertEquals("Носковой Светланы Николаевны", cases.genitive());
    }

    @Test
    void acceptingEmployeeAppliesHoursInRateRuleByPrimaryPosition() {
        LoadInRateRule rule = new LoadInRateRule();
        rule.setId(18L);
        rule.setName("Педагог-психолог");
        rule.setDocumentLabel("Педагог-психолог");
        rule.setActive(true);
        when(rules.findAllByOrderByNameAsc()).thenReturn(List.of(rule));
        when(teachers.findByFioTeacherIgnoreCase("Петрова Анна Ивановна")).thenReturn(Optional.empty());
        when(teachers.findAll()).thenReturn(List.of());
        TeacherDirectoryEntry[] accepted = new TeacherDirectoryEntry[1];
        when(teachers.save(any())).thenAnswer(invocation -> {
            TeacherDirectoryEntry teacher = invocation.getArgument(0);
            teacher.setId(77L);
            accepted[0] = teacher;
            return teacher;
        });
        when(personal.findByTeacherId(77L)).thenReturn(Optional.empty());
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(77L))
                .thenReturn(List.of());
        when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NameCases correctedCases = new NameCases(
                "Петрова Анна Ивановна",
                "Петровой Анны Ивановны",
                "Петровой Анне Ивановне",
                "Петрову Анну Ивановну",
                "Петровой Анной Ивановной",
                "Петровой Анне Ивановне",
                "Петрова А.И.",
                "Петровой А.И.",
                "Петровой А.И.",
                "Петрову А.И.",
                "Петровой А.И.",
                "Петровой А.И."
        );
        service.acceptEmployee(new AcceptEmployeeRequest(
                null, "Петрова Анна Ивановна", null, null,
                "СП1", "Педагог-психолог", "Основное место работы", LocalDate.of(2026, 9, 1),
                null, null, null, null, null, null, null, null, null, null,
                "25", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 1), null,
                false, null, correctedCases
        ), "admin");

        assertEquals("Петрову Анну Ивановну", accepted[0].getFioTeacherAccusative());
        assertEquals("Петровой А.И.", accepted[0].getInitialsInstrumental());
        verify(contracts).save(argThat(contract ->
                contract.isLoadHoursMayBeIncludedInRate()
                        && Long.valueOf(18L).equals(contract.getLoadInRateRuleId())
                        && contract.getLoadInRateDocumentLabel() == null
                        && "Педагог-психолог".equals(contract.getPositionName())));
    }

    @Test
    void employeeVerificationSheetContainsPersonalDataAndEqualTwoColumnTable() throws Exception {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(7L);
        teacher.setFioTeacher("Рысь Виктория Игоревна");
        teacher.setPhone("+7 900 000-00-00");
        teacher.setEmail("rys@example.test");
        teacher.setNumberSchoolBuilding("СП1");
        teacher.setPrimaryPosition("Учитель");
        teacher.setEmploymentType("Основное место работы");
        teacher.setEmploymentDate(LocalDate.of(2026, 9, 1));
        teacher.setFioTeacherGenitive("Рыси Виктории Игоревны");
        teacher.setInitialsGenitive("Рыси В.И.");
        when(teachers.findById(7L)).thenReturn(Optional.of(teacher));
        when(personal.findByTeacherId(7L)).thenReturn(Optional.empty());
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(7L)).thenReturn(List.of());

        byte[] content = service.employeeDataSheet(7L);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            assertTrue(document.getParagraphs().stream()
                    .anyMatch(paragraph -> paragraph.getText().contains("Лист проверки данных сотрудника")));
            assertEquals(2, document.getTables().get(0).getRow(0).getTableCells().size());
            var cells = document.getTables().get(0).getRow(0).getTableCells();
            assertEquals(cells.get(0).getCTTc().getTcPr().getTcW().getW(),
                    cells.get(1).getCTTc().getTcPr().getTcW().getW());
            String text = document.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .map(cell -> cell.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("Рыси Виктории Игоревны"));
            assertTrue(text.contains("Рыси В.И."));
            assertFalse(text.contains("Трудовой договор"));
        }
        Path qa = Path.of("target", "docx-qa");
        Files.createDirectories(qa);
        Files.write(qa.resolve("employee-data-sheet.docx"), content);
    }
}
