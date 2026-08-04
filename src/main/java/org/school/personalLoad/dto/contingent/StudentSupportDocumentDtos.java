package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.StudentSupportDocumentForm;
import org.school.personalLoad.model.StudentSupportDocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentSupportDocumentDtos {

    private StudentSupportDocumentDtos() {
    }

    @Data
    public static class SaveRequest {
        private Long id;
        private Long studentId;
        private StudentSupportDocumentType documentType;
        private StudentSupportDocumentForm acceptedForm;
        private String documentNumber;
        private LocalDate issueDate;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String issuingOrganization;
        private LocalDate receivedAt;
        private String responsibleEmployee;
        private String comment;
    }

    @Data
    public static class View {
        private Long id;
        private Long studentId;
        private String studentFullName;
        private String className;
        private StudentSupportDocumentType documentType;
        private StudentSupportDocumentForm acceptedForm;
        private String documentNumber;
        private LocalDate issueDate;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String issuingOrganization;
        private LocalDate receivedAt;
        private String responsibleEmployee;
        private String comment;
        private String validityStatus;
        private List<AttachmentView> attachments;
    }

    @Data
    public static class AttachmentView {
        private Long id;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private LocalDateTime uploadedAt;
        private String uploadedBy;
    }

    public record AttachmentDownload(String fileName, String contentType, byte[] content) {
    }
}
