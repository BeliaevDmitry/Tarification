package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.StudentClassTransferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentClassTransferDtos {
    private StudentClassTransferDtos() {
    }

    @Data
    public static class SaveRequest {
        private Long studentId;
        private String targetClassName;
        private LocalDate requestDate;
        private String reason;
        private LocalDate promisedDate;
        private String promiseNote;
        private StudentClassTransferStatus status;
        private LocalDate completedDate;
        private String comment;
    }

    @Data
    public static class HistoryRow {
        private String action;
        private String details;
        private LocalDateTime changedAt;
        private String changedBy;
    }

    @Data
    public static class TransferRow {
        private Long id;
        private Long studentId;
        private String studentName;
        private String fromClassName;
        private String targetClassName;
        private LocalDate requestDate;
        private String reason;
        private LocalDate promisedDate;
        private String promiseNote;
        private StudentClassTransferStatus status;
        private LocalDate completedDate;
        private String comment;
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime updatedAt;
        private String updatedBy;
        private List<HistoryRow> history;
    }

    @Data
    public static class StudentOption {
        private Long studentId;
        private String fullName;
        private String className;
        private Integer parallel;
    }

    @Data
    public static class ClassOption {
        private String className;
        private Integer parallel;
        private int students;
    }

    @Data
    public static class Access {
        private boolean canView;
        private boolean canEdit;
    }

    @Data
    public static class Overview {
        private String academicYear;
        private int total;
        private int waiting;
        private int transferred;
        private int closed;
        private List<TransferRow> requests;
        private List<StudentOption> studentOptions;
        private List<ClassOption> classOptions;
        private Access access;
    }
}
