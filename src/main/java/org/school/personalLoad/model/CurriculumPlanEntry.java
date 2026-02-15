package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "curriculum_plan_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_curriculum_class_subject_level", columnNames = {"className", "subjectName", "educationLevel"})
})
public class CurriculumPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private Integer plannedHours;

    @Column(nullable = false)
    private boolean subgroupRequired;

    @Column(nullable = false)
    private Integer subgroupCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
