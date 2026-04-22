package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "oge_score_scale", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oge_score_scale", columnNames = {"score", "subject_name"})
})
@Getter
@Setter
public class OgeScoreScaleEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column
    private Integer grade;
}
