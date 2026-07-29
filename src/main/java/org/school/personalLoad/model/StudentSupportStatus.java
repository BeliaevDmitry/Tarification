package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_support_status", indexes = {
        @Index(name = "idx_student_support_status_student_year", columnList = "student_id,academic_year"),
        @Index(name = "idx_student_support_status_dates", columnList = "valid_from,valid_to")
})
public class StudentSupportStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private StudentCategory category = StudentCategory.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nosology_id")
    private NosologyCatalogEntry nosology;

    @Column(name = "nosology_code_snapshot")
    private String nosologyCodeSnapshot;

    @Column(name = "aoop_variant_snapshot")
    private String aoopVariantSnapshot;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getNosologyId() {
        return nosology == null ? null : nosology.getId();
    }
}
