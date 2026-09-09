package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;

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
    public static class Overview {
        private String academicYear;
        private int total;
        private int active;
        private int agreed;
        private int enrolled;
        private int refused;
        private int processed;
        private List<ParallelStats> parallels;
        private List<CandidateRow> candidates;
    }
}
