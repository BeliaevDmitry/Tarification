package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subject_level_coefficient_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_level_coefficient_name_stage", columnNames = {"subjectName", "educationStage"})
})
public class SubjectLevelCoefficientEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationStage educationStage;

    @Column(nullable = false)
    private BigDecimal coefficient = BigDecimal.ONE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
