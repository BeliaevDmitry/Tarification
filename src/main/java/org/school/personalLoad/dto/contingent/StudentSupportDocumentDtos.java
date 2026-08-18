package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentSupportDocumentForm;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.school.personalLoad.model.SupportEducationStage;

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
        private String nosologyCode;
        private SupportEducationStage educationStage;
        private String educationProgram;
        private boolean prolongationAvailable;
        private boolean prolongationUsed;
        private Integer prolongedGrade;
        private String prolongedAcademicYear;
        private boolean ipraPresent;
        private List<CorrectionDirectionRequest> correctionDirections;
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
        private String nosologyCode;
        private StudentCategory derivedCategory;
        private SupportEducationStage educationStage;
        private String educationProgram;
        private boolean prolongationAvailable;
        private boolean prolongationUsed;
        private Integer prolongedGrade;
        private String prolongedAcademicYear;
        private boolean ipraPresent;
        private List<CorrectionDirectionView> correctionDirections;
        private String issuingOrganization;
        private LocalDate receivedAt;
        private String responsibleEmployee;
        private String comment;
        private String validityStatus;
        private List<AttachmentView> attachments;
    }

    @Data
    public static class CorrectionDirectionRequest {
        private Long specialistId;
        private String tasks;
    }

    @Data
    public static class CorrectionDirectionView {
        private Long id;
        private Long specialistId;
        private String specialistName;
        private String tasks;
    }

    @Data
    public static class NosologySaveRequest {
        private Long id;
        private String code;
        private boolean active = true;
    }

    @Data
    public static class NosologyView {
        private Long id;
        private String code;
        private boolean active;
    }

    @Data
    public static class SpecialistSaveRequest {
        private String name;
    }

    @Data
    public static class SpecialistView {
        private Long id;
        private String name;
        private boolean active;
        private boolean builtIn;
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
