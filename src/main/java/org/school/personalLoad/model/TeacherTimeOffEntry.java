package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_time_off_entry", indexes = {
        @Index(name = "idx_teacher_time_off_teacher", columnList = "teacher_id"),
        @Index(name = "idx_teacher_time_off_event_date", columnList = "event_date"),
        @Index(name = "idx_teacher_time_off_operation", columnList = "operation_type")
})
public class TeacherTimeOffEntry {

    public enum OperationType {
        EARNED,
        USED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "teacher_fio_snapshot", nullable = false)
    private String teacherFioSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 16)
    private OperationType operationType;

    @Column(name = "amount_minutes", nullable = false)
    private Integer amountMinutes;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "lesson_removal")
    private Boolean lessonRemoval;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_username", nullable = false, length = 100)
    private String createdByUsername;

    @Column(name = "created_by_fio", nullable = false)
    private String createdByFio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
