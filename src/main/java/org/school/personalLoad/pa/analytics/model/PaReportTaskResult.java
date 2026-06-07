package org.school.personalLoad.pa.analytics.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_report_task_result", indexes = {
        @Index(name = "idx_pa_task_result_report_version", columnList = "report_version_id"),
        @Index(name = "idx_pa_task_result_student", columnList = "student_result_id"),
        @Index(name = "idx_pa_task_result_task_no", columnList = "task_no"),
        @Index(name = "idx_pa_task_result_topic", columnList = "topic"),
        @Index(name = "idx_pa_task_result_skill", columnList = "skill")
})
@Getter
@Setter
public class PaReportTaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_version_id", nullable = false)
    private Long reportVersionId;

    @Column(name = "student_result_id")
    private Long studentResultId;

    @Column(name = "task_no")
    private Integer taskNo;

    @Column(name = "topic", length = 1000)
    private String topic;

    @Column(name = "skill", length = 1000)
    private String skill;

    @Column(name = "task_kind", length = 100)
    private String taskKind;

    @Column(name = "repeat_from_task_no")
    private Integer repeatFromTaskNo;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "score")
    private Double score;

    @Column(name = "percent")
    private Double percent;

    @Column(name = "empty", nullable = false)
    private boolean empty = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
