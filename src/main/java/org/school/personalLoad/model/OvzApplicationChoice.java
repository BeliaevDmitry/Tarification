package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ovz_application_choice", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ovz_choice_student_year_specialist", columnNames = {"student_id", "academic_year", "specialist_name"})
})
public class OvzApplicationChoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "specialist_name", nullable = false)
    private String specialistName;

    @Column(name = "tasks", length = 4000)
    private String tasks;

    @Column(name = "agreed", nullable = false)
    private boolean agreed = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
