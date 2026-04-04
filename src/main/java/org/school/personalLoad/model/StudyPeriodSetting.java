package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "study_period_rule")
public class StudyPeriodSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String settingKey;

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
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
