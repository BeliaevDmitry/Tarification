package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "iup_plan", indexes = {
        @Index(name = "idx_iup_plan_student_year", columnList = "student_id,academic_year"),
        @Index(name = "idx_iup_plan_dates", columnList = "valid_from,valid_to")
})
public class IupPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IupStatus status = IupStatus.DRAFT;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber = 1;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
