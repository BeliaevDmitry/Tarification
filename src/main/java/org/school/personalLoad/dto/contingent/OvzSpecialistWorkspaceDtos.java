package org.school.personalLoad.dto.contingent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class OvzSpecialistWorkspaceDtos {
    private OvzSpecialistWorkspaceDtos() {}

    public enum CompletionStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }

    @Data
    public static class Overview {
        private String currentUserName;
        private Long currentTeacherId;
        private boolean responsible;
        private boolean canManageSettings;
        private Long responsibleEmployeeId;
        private String responsibleEmployeeName;
        private int childCount;
        private int completedCount;
        private int incompleteCount;
        private List<ChildSummary> children;
    }

    @Data
    public static class ChildSummary {
        private Long studentId;
        private String fullName;
        private String className;
        private CompletionStatus overallStatus;
        private CompletionStatus currentUserStatus;
        private List<SpecialistStatus> specialists;
    }

    @Data
    public static class SpecialistStatus {
        private Long specialistId;
        private String specialistName;
        private Long staffId;
        private Long employeeId;
        private String employeeName;
        private CompletionStatus status;
        private boolean editable;
    }

    @Data
    public static class ChildDetail {
        private Long studentId;
        private String fullName;
        private String className;
        private boolean responsible;
        private List<SupportEntry> entries;
    }

    @Data
    public static class SupportEntry extends SpecialistStatus {
        private String childDeficits;
        private String childResources;
        private String annualTasks;
        private String plannedResults;
        private Long updatedByUserId;
        private String updatedByName;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SupportEntryRequest {
        private Long specialistId;
        private String childDeficits;
        private String childResources;
        private String annualTasks;
        private String plannedResults;
    }

    @Data
    public static class SettingsView {
        private Long responsibleEmployeeId;
        private String responsibleEmployeeName;
        private boolean canManage;
        private List<EmployeeOption> employees;
    }

    @Data
    public static class SettingsRequest {
        private Long responsibleEmployeeId;
    }

    @Data
    public static class EmployeeOption {
        private Long id;
        private String fullName;
        private String position;
        private String personnelNumber;
    }
}
