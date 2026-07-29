package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_name_history", indexes = {
        @Index(name = "idx_student_name_history_student", columnList = "student_id")
})
public class StudentNameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "normalized_full_name", nullable = false)
    private String normalizedFullName;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
