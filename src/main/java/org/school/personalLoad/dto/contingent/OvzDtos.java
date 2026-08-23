package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.*;

import java.time.LocalDate;
import java.util.List;

public final class OvzDtos {
    private OvzDtos() {}

    @Data
    public static class DossierSummary {
        private Long studentId;
        private String fullName;
        private LocalDate birthDate;
        private String className;
        private boolean mse;
        private LocalDate mseValidFrom;
        private LocalDate mseValidTo;
        private boolean conclusion;
        private LocalDate conclusionValidFrom;
        private LocalDate conclusionValidTo;
        private boolean recommendation;
        private String nosologyCode;
        private LocalDate validTo;
        private List<StudentSupportDocumentDtos.View> documents;
        private List<StudentSupportDocumentDtos.CorrectionDirectionView> correctionDirections;
        private List<StageView> stages;
    }

    @Data
    public static class DossierDetail extends DossierSummary {
        private List<ApplicationChoiceView> applicationChoices;
        private List<PpkProtocolView> ppkProtocols;
    }

    @Data
    public static class StageView {
        private OvzRoadmapStage stage;
        private String label;
        private OvzStageStatus status;
    }

    @Data
    public static class StageUpdateRequest {
        private OvzRoadmapStage stage;
        private OvzStageStatus status;
    }

    @Data
    public static class ApplicationChoiceRequest {
        private String specialistName;
        private String tasks;
        private boolean agreed;
    }

    @Data
    public static class ApplicationChoiceView extends ApplicationChoiceRequest {
        private Long id;
    }

    @Data
    public static class PpkProtocolSaveRequest {
        private Long id;
        private LocalDate meetingDate;
        private PpkProtocolType protocolType;
        private Long studentId;
        private String chairName;
        private String secretaryName;
        private String attendees;
        private String invitedRepresentative;
        private String representativeName;
        private String representativeSignatureName;
        private String agenda;
        private String meetingNotes;
        private String decisionText;
        private OvzStageStatus status;
    }

    @Data
    public static class PpkProtocolView {
        private Long id;
        private String protocolNumber;
        private LocalDate meetingDate;
        private PpkProtocolType protocolType;
        private Long studentId;
        private String studentFullName;
        private String className;
        private String chairName;
        private String secretaryName;
        private String attendees;
        private String invitedRepresentative;
        private String representativeName;
        private String representativeSignatureName;
        private String agenda;
        private String meetingNotes;
        private String decisionText;
        private OvzStageStatus status;
    }

    @Data
    public static class PpkProtocolSettingsRequest {
        private Long chairEmployeeId;
        private Long secretaryEmployeeId;
        private List<Long> attendeeEmployeeIds;
    }

    @Data
    public static class PpkProtocolSettingsView extends PpkProtocolSettingsRequest {
        private String chairName;
        private String chairPosition;
        private String secretaryName;
        private String secretaryPosition;
        private String attendees;
        private List<PpkCommissionMemberView> attendeeMembers;
    }

    @Data
    public static class PpkCommissionMemberView {
        private Long employeeId;
        private String fullName;
        private String position;
    }

    @Data
    public static class PpkEmployeeOption {
        private Long employeeId;
        private String fullName;
        private String position;
    }

    @Data
    public static class PpkProtocolDefaults extends PpkProtocolSettingsView {
        private Long studentId;
        private PpkProtocolType protocolType;
        private String invitedRepresentative;
        private String representativeName;
        private String representativeSignatureName;
        private String agenda;
        private String meetingNotes;
        private String decisionText;
        private String aoopVariant;
        private String conclusionNumber;
        private String message;
    }
}
