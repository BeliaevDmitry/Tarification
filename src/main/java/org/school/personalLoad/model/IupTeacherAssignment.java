package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "iup_teacher_assignment", indexes = {
        @Index(name = "idx_iup_teacher_assignment_line", columnList = "iup_subject_line_id"),
        @Index(name = "idx_iup_teacher_assignment_teacher", columnList = "teacher_id")
})
public class IupTeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iup_subject_line_id", nullable = false)
    private IupSubjectLine subjectLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherDirectoryEntry teacher;

    @Column(name = "teacher_fio_snapshot", nullable = false)
    private String teacherFioSnapshot;

    @Column(name = "hours_per_week", nullable = false, precision = 10, scale = 2)
    private BigDecimal hoursPerWeek = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_form", nullable = false)
    private IupDeliveryForm deliveryForm = IupDeliveryForm.FACE_TO_FACE;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getTeacherId() {
        return teacher == null ? null : teacher.getId();
    }
}
