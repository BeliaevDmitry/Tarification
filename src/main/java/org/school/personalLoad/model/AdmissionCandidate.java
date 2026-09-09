package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admission_candidate", indexes = {
        @Index(name = "idx_admission_candidate_year", columnList = "academic_year"),
        @Index(name = "idx_admission_candidate_parallel", columnList = "academic_year,requested_parallel"),
        @Index(name = "idx_admission_candidate_work", columnList = "academic_year,processed")
})
public class AdmissionCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "requested_parallel", nullable = false)
    private Integer requestedParallel;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 40)
    private AdmissionDocumentStatus documentStatus = AdmissionDocumentStatus.MOS_RU_SUBMITTED;

    @Column(name = "problems", columnDefinition = "text")
    private String problems;

    @Column(name = "comment_text", columnDefinition = "text")
    private String comment;

    @Column(name = "assigned_class", length = 100)
    private String assignedClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 30)
    private AdmissionDecisionStatus decisionStatus = AdmissionDecisionStatus.PENDING;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;
}
