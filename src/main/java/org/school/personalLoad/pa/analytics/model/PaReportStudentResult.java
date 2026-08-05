package org.school.personalLoad.pa.analytics.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_report_student_result", indexes = {
        @Index(name = "idx_pa_student_result_report_version", columnList = "report_version_id"),
        @Index(name = "idx_pa_student_result_academic_year", columnList = "academic_year"),
        @Index(name = "idx_pa_student_result_subject", columnList = "subject_name"),
        @Index(name = "idx_pa_student_result_class", columnList = "class_name"),
        @Index(name = "idx_pa_student_result_teacher", columnList = "teacher_fio"),
        @Index(name = "idx_pa_student_result_student", columnList = "student_id"),
        @Index(name = "idx_pa_student_result_fio_norm", columnList = "student_fio_normalized"),
        @Index(name = "idx_pa_student_result_row_status", columnList = "row_status")
})
@Getter
@Setter
public class PaReportStudentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_link_status", length = 50)
    private String studentLinkStatus;

    @Column(name = "student_link_message", length = 1000)
    private String studentLinkMessage;

    @Column(name = "report_version_id", nullable = false)
    private Long reportVersionId;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "class_name", length = 30)
    private String className;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "student_fio", length = 500)
    private String studentFio;

    @Column(name = "student_fio_normalized", length = 500)
    private String studentFioNormalized;

    @Column(name = "presence_status", length = 100)
    private String presenceStatus;

    @Column(name = "variant_name", length = 100)
    private String variantName;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "percent")
    private Double percent;

    @Column(name = "mark")
    private Integer mark;

    @Column(name = "has_result", nullable = false)
    private boolean hasResult = false;

    @Column(name = "possible_other_subgroup", nullable = false)
    private boolean possibleOtherSubgroup = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", length = 50)
    private PaStudentResultStatus rowStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
