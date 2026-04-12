package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "manual_load_entry")
public class ManualLoadEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(nullable = false, length = 32)
    private String academicYear = "";

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private Integer load;

    private String groupNameEducationalPlan;

    private Integer groupLoad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    @Enumerated(EnumType.STRING)
    private StudyPeriod studyPeriod;

    private LocalDate loadFromDate;

    private LocalDate loadToDate;

    private LocalDate backupLoadToDate;

    @Column(nullable = false)
    private boolean dismissalAdjusted = false;

    @Column(nullable = false)
    private boolean orphaned = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
