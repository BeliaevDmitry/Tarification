package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_class_transfer_history", indexes = {
        @Index(name = "idx_student_transfer_history_request", columnList = "transfer_request_id,changed_at")
})
public class StudentClassTransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_request_id", nullable = false)
    private StudentClassTransferRequest transferRequest;

    @Column(name = "action_name", nullable = false, length = 100)
    private String action;

    @Column(name = "details", nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;
}
