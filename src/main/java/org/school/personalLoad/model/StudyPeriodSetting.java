package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "study_period_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_study_period_code_year", columnNames = {"academicYear", "code"})
})
public class StudyPeriodSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyPeriod studyPeriod;

    @Column(nullable = false)
    private Integer parallelFrom;

    @Column(nullable = false)
    private Integer parallelTo;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean defaultRule = false;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
