package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;
import org.school.personalLoad.auth.UserRole;

import java.time.LocalDateTime;
import java.util.List;

public final class AdmissionDtos {
    private AdmissionDtos() {
    }

    @Data
    public static class SaveRequest {
        private String fullName;
        private Integer requestedParallel;
        private AdmissionDocumentStatus documentStatus;
        private String problems;
        private String comment;
        private String assignedClass;
    }

    @Data
    public static class ActionRequest {
        private String action;
        private AdmissionDocumentStatus documentStatus;
        private String assignedClass;
        private String comment;
        private String problems;
        private Boolean testing;
    }

    @Data
    public static class CandidateRow {
        private Long id;
        private String fullName;
        private Integer requestedParallel;
        private AdmissionDocumentStatus documentStatus;
        private String problems;
        private String comment;
        private String assignedClass;
        private AdmissionDecisionStatus decisionStatus;
        private boolean processed;
        private boolean testing;
        private String additionalInfo;
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime updatedAt;
        private String updatedBy;
    }

    @Data
    public static class ParallelStats {
        private Integer requestedParallel;
        private int total;
        private int active;
        private int agreed;
        private int enrolled;
        private int refused;
    }

    @Data
    public static class ClassOption {
        private String className;
        private Integer parallel;
        private Integer students;
    }

    @Data
    public static class Overview {
        private String academicYear;
        private int total;
        private int active;
        private int agreed;
        private int enrolled;
        private int refused;
        private int processed;
        private List<ParallelStats> parallels;
        private List<ClassOption> classOptions;
        private List<CandidateRow> candidates;
        private Access access;
    }

    @Data
    public static class Access {
        private boolean canView;
        private boolean canEdit;
        private boolean canDecide;
        private boolean canManageRoles;
        private boolean canEditRoles;
    }

    @Data
    public static class RoleUserRow {
        private Long userId;
        private String username;
        private String fullName;
        private UserRole systemRole;
        private String systemRoleName;
        private boolean active;
        private boolean secretary;
        private boolean secretaryFromSystemRole;
        private boolean decisionMaker;
    }

    @Data
    public static class RolesOverview {
        private List<RoleUserRow> users;
        private boolean canEdit;
    }

    @Data
    public static class RoleAssignmentRequest {
        private Long userId;
        private boolean secretary;
        private boolean decisionMaker;
    }

    @Data
    public static class RolesUpdateRequest {
        private List<RoleAssignmentRequest> assignments;
    }
}
