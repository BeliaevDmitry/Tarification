package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_class_transfer_request", indexes = {
        @Index(name = "idx_student_transfer_year_status", columnList = "academic_year,status"),
        @Index(name = "idx_student_transfer_student", columnList = "student_id")
})
public class StudentClassTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "student_name_snapshot", nullable = false, length = 500)
    private String studentNameSnapshot;

    @Column(name = "from_class_name", nullable = false, length = 100)
    private String fromClassName;

    @Column(name = "target_class_name", nullable = false, length = 100)
    private String targetClassName;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "promise_note", columnDefinition = "text")
    private String promiseNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StudentClassTransferStatus status = StudentClassTransferStatus.WAITING_FOR_PLACE;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "comment_text", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;
}
