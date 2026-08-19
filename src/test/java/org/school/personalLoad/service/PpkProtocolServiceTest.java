package org.school.personalLoad.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.model.OvzStageStatus;
import org.school.personalLoad.model.PpkProtocol;
import org.school.personalLoad.model.PpkProtocolType;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.school.personalLoad.repository.OvzWorkflowStageRepository;
import org.school.personalLoad.repository.PpkProtocolRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportDocumentRepository;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PpkProtocolServiceTest {
    private final PpkProtocolRepository protocolRepository = mock(PpkProtocolRepository.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final StudentClassEnrollmentRepository enrollmentRepository = mock(StudentClassEnrollmentRepository.class);
    private final OvzWorkflowStageRepository workflowStageRepository = mock(OvzWorkflowStageRepository.class);
    private final StudentSupportDocumentRepository documentRepository = mock(StudentSupportDocumentRepository.class);
    private final PpkProtocolSettingsService settingsService = mock(PpkProtocolSettingsService.class);
    private final PpkProtocolService service = new PpkProtocolService(
            protocolRepository, studentRepository, enrollmentRepository, workflowStageRepository,
            documentRepository, settingsService);

    @Test
    void fillsParentFromStudentCardAndUsesCorrectMaleCases() {
        StudentProfile student = student(10L, "Левинов Николай Михайлович", "Иванова Мария Петровна");
        prepareDefaults(student, "4-А", "И4.2");

        OvzDtos.PpkProtocolDefaults defaults = service.defaults("2026/2027", student.getId());

        assertEquals("Иванова Мария Петровна", defaults.getRepresentativeName());
        assertEquals("Иванова Мария Петровна", defaults.getRepresentativeSignatureName());
        assertTrue(defaults.getAgenda().contains(
                "обучающемуся с ОВЗ Левинову Николаю Михайловичу"));
        assertTrue(defaults.getAgenda().contains(
                "для обучающегося с ОВЗ Левинова Николая Михайловича"));
        assertTrue(defaults.getDecisionText().contains(
                "для обучающегося 4 «А» класса Левинова Николая Михайловича"));
        assertTrue(defaults.getDecisionText().contains(
                "обучающемуся с ОВЗ Левинову Николаю Михайловичу"));
    }

    @Test
    void usesCorrectFemaleCasesThroughoutProtocolText() {
        StudentProfile student = student(11L, "Иванова Анна Сергеевна", "Петров Сергей Иванович");
        prepareDefaults(student, "2-Е", "И5.1");

        OvzDtos.PpkProtocolDefaults defaults = service.defaults("2026/2027", student.getId());

        assertTrue(defaults.getAgenda().contains(
                "обучающейся с ОВЗ Ивановой Анне Сергеевне"));
        assertTrue(defaults.getAgenda().contains(
                "для обучающейся с ОВЗ Ивановой Анны Сергеевны"));
        assertTrue(defaults.getDecisionText().contains(
                "для обучающейся 2 «Е» класса Ивановой Анны Сергеевны"));
        assertTrue(defaults.getDecisionText().contains(
                "обучающейся с ОВЗ Ивановой Анне Сергеевне"));
    }

    @Test
    void usesCorrectLoveNameCasesThroughoutProtocolText() {
        StudentProfile student = student(13L, "Сапрыкина Любовь Романовна", null);
        prepareDefaults(student, "4-Е", "И5.1");

        OvzDtos.PpkProtocolDefaults defaults = service.defaults("2026/2027", student.getId());

        assertTrue(defaults.getAgenda().contains(
                "обучающейся с ОВЗ Сапрыкиной Любови Романовне"));
        assertTrue(defaults.getAgenda().contains(
                "для обучающейся с ОВЗ Сапрыкиной Любови Романовны"));
        assertTrue(defaults.getDecisionText().contains(
                "для обучающейся 4 «Е» класса Сапрыкиной Любови Романовны"));
        assertTrue(defaults.getDecisionText().contains(
                "обучающейся с ОВЗ Сапрыкиной Любови Романовне"));
    }

    @Test
    void printsEditableRepresentativeAtTopAndNameOrBlankAtSignature() throws Exception {
        StudentProfile student = student(12L, "Левинов Николай Михайлович", "Иванова Мария Петровна");
        PpkProtocol protocol = protocol(student);
        protocol.setRepresentativeName("Петров Сергей Иванович");
        protocol.setRepresentativeSignatureName("Петров Сергей Иванович");
        when(protocolRepository.findById(7L)).thenReturn(Optional.of(protocol));

        PpkProtocolService.GeneratedDocument namedDocument = service.generate("2026/2027", 7L);
        String named = documentText(namedDocument);
        assertEquals("№7.docx", namedDocument.fileName());
        assertTrue(named.contains("№7 от 19.08.2026"));
        assertTrue(named.contains("Приглашены: законный представитель ребёнка — Петров Сергей Иванович"));
        assertTrue(named.contains("Представитель ребёнка __________________ / Петров Сергей Иванович /"));

        protocol.setRepresentativeSignatureName(null);
        String blank = documentText(service.generate("2026/2027", 7L));
        assertTrue(blank.contains("Представитель ребёнка __________________ / ____________________________ /"));
    }

    @Test
    void assignsOneContinuousSimpleProtocolNumber() {
        OvzDtos.PpkProtocolSettingsView settings = new OvzDtos.PpkProtocolSettingsView();
        settings.setAttendeeEmployeeIds(List.of());
        settings.setAttendeeMembers(List.of());
        when(settingsService.get()).thenReturn(settings);
        when(protocolRepository.maxSequenceNumber()).thenReturn(4);
        when(protocolRepository.save(any(PpkProtocol.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OvzDtos.PpkProtocolSaveRequest request = new OvzDtos.PpkProtocolSaveRequest();
        request.setMeetingDate(LocalDate.of(2027, 9, 1));
        OvzDtos.PpkProtocolView saved = service.save("2027/2028", request);

        assertEquals("№5", saved.getProtocolNumber());
    }

    private void prepareDefaults(StudentProfile student, String className, String nosologyCode) {
        OvzDtos.PpkProtocolSettingsView settings = new OvzDtos.PpkProtocolSettingsView();
        settings.setChairName("Власова Юлия Сергеевна");
        settings.setSecretaryName("Рыбкина Лариса Павловна");
        settings.setAttendeeEmployeeIds(List.of());
        settings.setAttendeeMembers(List.of());
        when(settingsService.get()).thenReturn(settings);
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setClassName(className);
        when(enrollmentRepository.findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(
                student.getId(), "2026/2027")).thenReturn(Optional.of(enrollment));

        StudentSupportDocument conclusion = new StudentSupportDocument();
        conclusion.setDocumentType(StudentSupportDocumentType.CPMPC_CONCLUSION);
        conclusion.setDocumentNumber("12345");
        conclusion.setNosologyCode(nosologyCode);
        when(documentRepository.findFirstByStudent_IdAndAcademicYearAndDocumentType(
                student.getId(), "2026/2027", StudentSupportDocumentType.CPMPC_CONCLUSION))
                .thenReturn(Optional.of(conclusion));
    }

    private StudentProfile student(Long id, String fullName, String representativeName) {
        StudentProfile student = new StudentProfile();
        student.setId(id);
        student.setCurrentFullName(fullName);
        student.setRepresentativeName(representativeName);
        return student;
    }

    private PpkProtocol protocol(StudentProfile student) {
        PpkProtocol protocol = new PpkProtocol();
        protocol.setId(7L);
        protocol.setAcademicYear("2026/2027");
        protocol.setProtocolNumber("№7");
        protocol.setMeetingDate(LocalDate.of(2026, 8, 19));
        protocol.setProtocolType(PpkProtocolType.APPOINTMENT);
        protocol.setStudent(student);
        protocol.setChairName("Власова Ю.С.");
        protocol.setSecretaryName("Рыбкина Л.П.");
        protocol.setAttendees("");
        protocol.setAgenda("Повестка");
        protocol.setMeetingNotes("Ход заседания");
        protocol.setDecisionText("Решение");
        protocol.setStatus(OvzStageStatus.PRINTED);
        return protocol;
    }

    private String documentText(PpkProtocolService.GeneratedDocument generated) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.content()))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
