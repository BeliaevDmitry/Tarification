package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportDocumentAttachment;
import org.school.personalLoad.model.StudentSupportDocumentForm;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportDocumentAttachmentRepository;
import org.school.personalLoad.repository.StudentSupportDocumentRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSupportDocumentServiceTest {

    @Mock
    private StudentSupportDocumentRepository documentRepository;
    @Mock
    private StudentSupportDocumentAttachmentRepository attachmentRepository;
    @Mock
    private StudentProfileRepository studentRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;

    private StudentSupportDocumentService service;
    private StudentProfile student;

    @BeforeEach
    void setUp() {
        service = new StudentSupportDocumentService(
                documentRepository,
                attachmentRepository,
                studentRepository,
                enrollmentRepository
        );
        student = new StudentProfile();
        student.setId(1L);
        student.setCurrentFullName("Иванов Иван Иванович");
        lenient().when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        lenient().when(documentRepository.save(any(StudentSupportDocument.class))).thenAnswer(invocation -> {
            StudentSupportDocument document = invocation.getArgument(0);
            document.setId(10L);
            return document;
        });
        lenient().when(attachmentRepository.findAllByDocument_IdOrderByUploadedAtAsc(10L))
                .thenReturn(List.of());
        lenient().when(enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(1L, "2026/2027"))
                .thenReturn(List.of());
    }

    @Test
    void acceptsDocumentMetadataWithoutChangingStudentCategory() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);
        request.setAcceptedForm(StudentSupportDocumentForm.COPY);
        request.setDocumentNumber("МСЭ-1");
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));
        request.setReceivedAt(LocalDate.of(2026, 9, 2));

        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);

        assertEquals(10L, saved.getId());
        assertEquals(StudentSupportDocumentType.MSE_CERTIFICATE, saved.getDocumentType());
        assertEquals(StudentSupportDocumentForm.COPY, saved.getAcceptedForm());
        assertEquals("МСЭ-1", saved.getDocumentNumber());
    }

    @Test
    void acceptsAllowedCopyAndRejectsUnsupportedFile() {
        StudentSupportDocument document = new StudentSupportDocument();
        document.setId(10L);
        document.setAcademicYear("2026/2027");
        document.setStudent(student);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(attachmentRepository.save(any(StudentSupportDocumentAttachment.class)))
                .thenAnswer(invocation -> {
                    StudentSupportDocumentAttachment attachment = invocation.getArgument(0);
                    attachment.setId(20L);
                    return attachment;
                });

        StudentSupportDocumentDtos.AttachmentView saved = service.addAttachment(
                "2026/2027",
                10L,
                new MockMultipartFile("file", "справка.pdf", "application/pdf", new byte[]{1, 2, 3}),
                "hr"
        );

        assertEquals(20L, saved.getId());
        assertEquals("справка.pdf", saved.getFileName());
        assertThrows(IllegalArgumentException.class, () -> service.addAttachment(
                "2026/2027",
                10L,
                new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[]{1}),
                "hr"
        ));
    }

    @Test
    void rejectsDocumentWithoutStudentAsUserInputError() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );

        assertEquals("Выберите ребёнка", error.getMessage());
    }
}
