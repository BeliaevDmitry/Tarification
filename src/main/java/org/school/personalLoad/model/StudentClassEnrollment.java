package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_class_enrollment", indexes = {
        @Index(name = "idx_student_enrollment_student_year", columnList = "student_id,academic_year"),
        @Index(name = "idx_student_enrollment_class_year", columnList = "class_id,academic_year")
})
public class StudentClassEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private ClassroomLeadershipEntry classRef;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentEnrollmentStatus status = StudentEnrollmentStatus.ACTIVE;

    @Column(name = "source_snapshot_id")
    private Long sourceSnapshotId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getClassId() {
        return classRef == null ? null : classRef.getId();
    }
}
