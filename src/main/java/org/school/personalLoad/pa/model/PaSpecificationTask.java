package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "pa_specification_task", indexes = {
        @Index(name = "idx_pa_spec_task_spec", columnList = "specification_id"),
        @Index(name = "idx_pa_spec_task_no", columnList = "task_no")
})
@Getter
@Setter
public class PaSpecificationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specification_id", nullable = false)
    private PaSpecification specification;

    @Column(name = "task_no", nullable = false)
    private Integer taskNo;

    @Column(name = "topic", length = 2000)
    private String topic;

    @Column(name = "skill", length = 2000)
    private String skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_kind", length = 20)
    private PaTaskKind taskKind;

    @Column(name = "repeat_from_task_no")
    private Integer repeatFromTaskNo;

    @Column(name = "max_score")
    private Integer maxScore;
}
