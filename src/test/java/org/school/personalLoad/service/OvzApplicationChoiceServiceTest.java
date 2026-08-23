package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.model.OvzApplicationChoice;
import org.school.personalLoad.model.OvzStageStatus;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class OvzApplicationChoiceServiceTest {

    @Test
    void receivedApplicationUpdatesExistingChoicesWithoutConflictingReinsert() {
        StudentSupportDocumentService documentService = mock(StudentSupportDocumentService.class);
        StudentSupportDocumentRepository documentRepository = mock(StudentSupportDocumentRepository.class);
        StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
        StudentClassEnrollmentRepository enrollmentRepository = mock(StudentClassEnrollmentRepository.class);
        OvzWorkflowStageRepository stageRepository = mock(OvzWorkflowStageRepository.class);
        OvzApplicationChoiceRepository choiceRepository = mock(OvzApplicationChoiceRepository.class);
        StudentProfile student = new StudentProfile(); student.setId(17L); student.setCurrentFullName("Иванов Иван Иванович");
        OvzApplicationChoice existing = new OvzApplicationChoice(); existing.setId(5L); existing.setStudent(student);
        existing.setAcademicYear("2026/2027"); existing.setSpecialistName("Педагог-психолог"); existing.setAgreed(true);
        when(studentRepository.findById(17L)).thenReturn(Optional.of(student));
        when(choiceRepository.findAllByStudent_IdAndAcademicYearOrderBySpecialistNameAsc(17L, "2026/2027"))
                .thenReturn(List.of(existing));
        when(stageRepository.findByStudent_IdAndAcademicYearAndStage(anyLong(), anyString(), any())).thenReturn(Optional.empty());

        OvzDossierService service = new OvzDossierService(documentService, documentRepository, studentRepository,
                enrollmentRepository, stageRepository, choiceRepository, mock(PpkProtocolRepository.class),
                mock(StudentSupportDocumentCorrectionRepository.class), mock(StudentSupportStatusRepository.class),
                mock(StudentSupportDocumentAttachmentRepository.class), mock(CorrectionDistributionService.class));
        OvzDtos.ApplicationChoiceRequest request = new OvzDtos.ApplicationChoiceRequest();
        request.setSpecialistName("Педагог-психолог"); request.setTasks("Обновлённая задача"); request.setAgreed(false);

        service.saveApplicationChoices("2026/2027", 17L, List.of(request));

        assertThat(existing.getTasks()).isEqualTo("Обновлённая задача");
        assertThat(existing.isAgreed()).isFalse();
        verify(choiceRepository).save(existing);
        verify(choiceRepository, never()).delete(existing);
        verify(stageRepository).save(argThat(stage -> stage.getStatus() == OvzStageStatus.COMPLETED));
    }
}
