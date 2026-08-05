package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.IupOrderDocumentDtos;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.IupDeliveryForm;
import org.school.personalLoad.model.IupOrderTemplateType;
import org.school.personalLoad.model.IupParticipationMode;
import org.school.personalLoad.model.IupPlan;
import org.school.personalLoad.model.IupSubjectLine;
import org.school.personalLoad.model.IupTeacherAssignment;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentGender;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.IupPlanRepository;
import org.school.personalLoad.repository.IupSubjectLineRepository;
import org.school.personalLoad.repository.IupTeacherAssignmentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;
import org.school.personalLoad.service.IupOrderDocumentService;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IupOrderDocumentServiceImplTest {

    private static final String YEAR = "2025/2026";

    @Mock
    private IupPlanRepository iupPlanRepository;
    @Mock
    private IupSubjectLineRepository iupSubjectLineRepository;
    @Mock
    private IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private StudentSupportStatusRepository supportStatusRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    private IupOrderDocumentServiceImpl service;
    private IupPlan plan;
    private IupSubjectLine line;
    private IupTeacherAssignment assignment;
    private StudentClassEnrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new IupOrderDocumentServiceImpl(
                iupPlanRepository,
                iupSubjectLineRepository,
                iupTeacherAssignmentRepository,
                enrollmentRepository,
                supportStatusRepository,
                curriculumPlanEntryRepository
        );
        plan = plan(1L, "Иванов Иван Иванович", LocalDate.of(2013, 3, 19), "5-А");
        line = new IupSubjectLine();
        line.setId(11L);
        line.setIupPlan(plan);
        line.setSubjectName("Математика");
        line.setParticipationMode(IupParticipationMode.INDIVIDUAL);
        line.setClassHours(BigDecimal.ZERO);
        line.setIndividualHours(new BigDecimal("2"));

        assignment = new IupTeacherAssignment();
        assignment.setId(21L);
        assignment.setSubjectLine(line);
        assignment.setTeacherFioSnapshot("Петрова Анна Сергеевна");
        assignment.setHoursPerWeek(new BigDecimal("2"));
        assignment.setDeliveryForm(IupDeliveryForm.FACE_TO_FACE);

        enrollment = enrollment(plan, "5-А", "Смирнова Ольга Ивановна");
        stubPlan(plan, line, assignment, enrollment);
        lenient().when(supportStatusRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of());
    }

    @Test
    void generatesIndividualOrderWithThreeRealTemplateAppendices() throws Exception {
        IupOrderDocumentDtos.GenerateRequest request = baseRequest(IupOrderTemplateType.INDIVIDUAL_IUP);

        IupOrderDocumentService.GeneratedDocument generated = service.generate(YEAR, request);
        writeQaSample("01-individual-iup.docx", generated);

        assertTrue(generated.fileName().contains("3-100"));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.content()))) {
            String text = allText(document);
            assertTrue(text.contains("Об организации обучения по индивидуальному учебному плану"));
            assertTrue(text.contains("Иванова Ивана Ивановича"));
            assertTrue(text.contains("ИНДИВИДУАЛЬНЫЙ УЧЕБНЫЙ ПЛАН"));
            assertTrue(text.contains("Индивидуальное расписание обучающегося"));
            assertTrue(text.contains("График проведения промежуточной аттестации"));
            assertTrue(text.contains("Математика"));
            assertTrue(text.contains("Петрова Анна Сергеевна"));
            assertFalse(text.contains("{{"));
            assertTrue(document.getTables().size() >= 4);
        }
    }

    @Test
    void generatesHomeEducationOrderAndTeacherLoadAppendix() throws Exception {
        IupOrderDocumentDtos.GenerateRequest request = baseRequest(IupOrderTemplateType.HOME_EDUCATION);
        request.setMedicalConclusionNumber("б/н");
        request.setMedicalConclusionDate(LocalDate.of(2025, 9, 1));
        request.setMedicalOrganization("ДГП №10 ДЗМ");

        IupOrderDocumentService.GeneratedDocument generated = service.generate(YEAR, request);
        writeQaSample("02-home-education.docx", generated);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.content()))) {
            String text = allText(document);
            assertTrue(text.contains("Об изменении формы обучения и организации обучения"));
            assertTrue(text.contains("индивидуальных учебных занятий на дому"));
            assertTrue(text.contains("РАСПРЕДЕЛЕНИЕ ДОПОЛНИТЕЛЬНОЙ УЧЕБНОЙ НАГРУЗКИ"));
            assertTrue(text.contains("Петрова Анна Сергеевна"));
            assertTrue(text.contains("2"));
        }
    }

    @Test
    void generatesExtensionUsingPreviousOrderDetails() throws Exception {
        IupOrderDocumentDtos.GenerateRequest request = baseRequest(IupOrderTemplateType.HOME_EDUCATION_EXTENSION);
        request.setMedicalConclusionNumber("958");
        request.setMedicalConclusionDate(LocalDate.of(2025, 11, 13));
        request.setMedicalOrganization("ДГП №131 ДЗМ");
        request.setPreviousOrderNumber("3-186");
        request.setPreviousOrderDate(LocalDate.of(2025, 10, 23));

        IupOrderDocumentService.GeneratedDocument generated = service.generate(YEAR, request);
        writeQaSample("03-home-education-extension.docx", generated);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.content()))) {
            String text = allText(document);
            assertTrue(text.contains("Внести изменения в приказ от 23.10.2025 № 3-186"));
            assertTrue(text.contains("Продлить индивидуальные учебные занятия на дому"));
        }
    }

    @Test
    void generatesOvzGroupOrderOnlyFromK2AndK3Plans() throws Exception {
        IupPlan second = plan(2L, "Сидорова Анна Петровна", LocalDate.of(2014, 4, 2), "6-Б");
        IupSubjectLine secondLine = new IupSubjectLine();
        secondLine.setId(12L);
        secondLine.setIupPlan(second);
        secondLine.setSubjectName("Русский язык");
        secondLine.setParticipationMode(IupParticipationMode.WITH_CLASS);
        secondLine.setClassHours(new BigDecimal("3"));
        secondLine.setIndividualHours(BigDecimal.ZERO);
        StudentClassEnrollment secondEnrollment = enrollment(second, "6-Б", "Орлова Мария Ивановна");

        when(iupPlanRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(plan, second));
        when(iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(2L)).thenReturn(List.of(secondLine));
        when(iupTeacherAssignmentRepository.findAllBySubjectLine_IupPlan_Id(2L)).thenReturn(List.of());
        when(enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(200L, YEAR))
                .thenReturn(List.of(secondEnrollment));
        StudentSupportStatus k2 = status(plan, StudentCategory.K2);
        StudentSupportStatus k3 = status(second, StudentCategory.K3);
        when(supportStatusRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(k2, k3));

        IupOrderDocumentDtos.GenerateRequest request = baseRequest(IupOrderTemplateType.OVZ_GROUP);
        request.setIupPlanIds(List.of(1L, 2L));
        request.setStudentGender(null);
        request.setStudentNameForOrder(null);
        request.setPpkProtocolNumber("1");
        request.setPpkProtocolDate(LocalDate.of(2025, 8, 29));

        IupOrderDocumentService.GeneratedDocument generated = service.generate(YEAR, request);
        writeQaSample("04-ovz-group.docx", generated);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.content()))) {
            String text = allText(document);
            assertTrue(text.contains("обучающихся с ограниченными возможностями здоровья"));
            assertTrue(text.contains("Иванов Иван Иванович"));
            assertTrue(text.contains("Сидорова Анна Петровна"));
            assertTrue(text.contains("Приложение 1.1"));
            assertTrue(text.contains("Приложение 1.2"));
            assertTrue(text.contains("Приложение 2"));
        }
    }

    @Test
    void rejectsNormalStudentInOvzGroupOrder() {
        IupOrderDocumentDtos.GenerateRequest request = baseRequest(IupOrderTemplateType.OVZ_GROUP);
        request.setStudentGender(null);
        request.setStudentNameForOrder(null);
        request.setPpkProtocolNumber("1");
        request.setPpkProtocolDate(LocalDate.of(2025, 8, 29));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(YEAR, request)
        );

        assertEquals("В сводный приказ ОВЗ включаются только дети со статусом К2 или К3", exception.getMessage());
    }

    private IupOrderDocumentDtos.GenerateRequest baseRequest(IupOrderTemplateType type) {
        IupOrderDocumentDtos.GenerateRequest request = new IupOrderDocumentDtos.GenerateRequest();
        request.setTemplateType(type);
        request.setIupPlanIds(List.of(1L));
        request.setOrderNumber("3-100");
        request.setOrderDate(LocalDate.of(2025, 9, 17));
        request.setStudentGender(StudentGender.MALE);
        request.setStudentNameForOrder("Иванова Ивана Ивановича");
        request.setPedagogicalCouncilProtocolNumber("2");
        request.setPedagogicalCouncilProtocolDate(LocalDate.of(2025, 9, 19));
        request.setElectronicJournalAdministrator("Сотрудник А. А.");
        request.setControlOfficer("Сотрудник Б. Б.");
        request.setDirectorName("Директор В. В.");
        request.setEducationLevelAndForm("основное общее образование / очная форма обучения");
        return request;
    }

    private IupPlan plan(Long id, String name, LocalDate birthDate, String className) {
        StudentProfile student = new StudentProfile();
        student.setId(id * 100);
        student.setCurrentFullName(name);
        student.setBirthDate(birthDate);
        IupPlan result = new IupPlan();
        result.setId(id);
        result.setStudent(student);
        result.setAcademicYear(YEAR);
        result.setOrderNumber("3-100");
        result.setOrderDate(LocalDate.of(2025, 9, 17));
        result.setValidFrom(LocalDate.of(2025, 10, 1));
        result.setValidTo(LocalDate.of(2026, 5, 29));
        return result;
    }

    private StudentClassEnrollment enrollment(IupPlan source, String className, String teacher) {
        ClassroomLeadershipEntry classRef = new ClassroomLeadershipEntry();
        classRef.setFioTeacher(teacher);
        classRef.setClassName(className);
        StudentClassEnrollment result = new StudentClassEnrollment();
        result.setStudent(source.getStudent());
        result.setAcademicYear(YEAR);
        result.setClassName(className);
        result.setClassRef(classRef);
        result.setValidFrom(LocalDate.of(2025, 9, 1));
        return result;
    }

    private StudentSupportStatus status(IupPlan source, StudentCategory category) {
        StudentSupportStatus result = new StudentSupportStatus();
        result.setStudent(source.getStudent());
        result.setAcademicYear(YEAR);
        result.setCategory(category);
        result.setValidFrom(LocalDate.of(2025, 9, 1));
        result.setValidTo(LocalDate.of(2026, 8, 31));
        return result;
    }

    private void stubPlan(IupPlan source,
                          IupSubjectLine subject,
                          IupTeacherAssignment teacher,
                          StudentClassEnrollment classEnrollment) {
        lenient().when(iupPlanRepository.findAllById(List.of(source.getId()))).thenReturn(List.of(source));
        lenient().when(iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(source.getId()))
                .thenReturn(List.of(subject));
        lenient().when(iupTeacherAssignmentRepository.findAllBySubjectLine_IupPlan_Id(source.getId()))
                .thenReturn(List.of(teacher));
        lenient().when(enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                source.getStudent().getId(), YEAR)).thenReturn(List.of(classEnrollment));
        lenient().when(curriculumPlanEntryRepository.findAllById(any())).thenReturn(List.of());
    }

    private String allText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
        for (XWPFTable table : document.getTables()) {
            table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                    text.append(cell.getText()).append('\n')));
        }
        return text.toString();
    }

    private void writeQaSample(String fileName,
                               IupOrderDocumentService.GeneratedDocument generated) throws Exception {
        String qaDirectory = System.getProperty("iup.order.qa.dir");
        if (qaDirectory == null || qaDirectory.isBlank()) {
            return;
        }
        Path directory = Path.of(qaDirectory);
        Files.createDirectories(directory);
        Files.write(directory.resolve(fileName), generated.content());
    }
}
