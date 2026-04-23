package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "oge_task_scale_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oge_task_scale_entry", columnNames = {"academic_year", "subject_name", "task_number"})
})
@Getter
@Setter
public class OgeTaskScaleEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "task_number", nullable = false)
    private Integer taskNumber;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;
}

