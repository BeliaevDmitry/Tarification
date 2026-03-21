package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "curriculum_plan_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_curriculum_import_key", columnNames = {"academicYear", "stage", "className", "subjectName", "studyPeriod"})
})
public class CurriculumPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private String academicYear = "";

    @Column(nullable = false)
    private String stage = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyPeriod studyPeriod = StudyPeriod.YEAR;

    @Column(nullable = false)
    private Integer plannedHours;

    @Column(nullable = false)
    private boolean subgroupRequired;

    @Column(nullable = false)
    private Integer subgroupCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    private Integer subgroup1Hours;

    @Enumerated(EnumType.STRING)
    private EducationLevel subgroup1EducationLevel;

    private Integer subgroup2Hours;

    @Enumerated(EnumType.STRING)
    private EducationLevel subgroup2EducationLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurriculumPart curriculumPart = CurriculumPart.CORE;

    @Column(nullable = false)
    private boolean deprecated = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
