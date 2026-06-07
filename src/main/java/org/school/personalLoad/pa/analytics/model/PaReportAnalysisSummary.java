package org.school.personalLoad.pa.analytics.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_report_analysis_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_pa_analysis_summary_report_version", columnNames = "report_version_id"),
        indexes = {
                @Index(name = "idx_pa_analysis_summary_academic_year", columnList = "academic_year"),
                @Index(name = "idx_pa_analysis_summary_subject", columnList = "subject_name"),
                @Index(name = "idx_pa_analysis_summary_teacher", columnList = "teacher_fio"),
                @Index(name = "idx_pa_analysis_summary_class", columnList = "class_name"),
                @Index(name = "idx_pa_analysis_summary_status", columnList = "analysis_status"),
                @Index(name = "idx_pa_analysis_summary_needs_review", columnList = "needs_review"),
                @Index(name = "idx_pa_analysis_summary_report_version", columnList = "report_version_id")
        })
@Getter
@Setter
public class PaReportAnalysisSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_version_id", nullable = false, unique = true)
    private Long reportVersionId;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "class_name", length = 30)
    private String className;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "work_type", length = 50)
    private String workType;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "level_code", length = 50)
    private String level;

    @Column(name = "students_total")
    private Integer studentsTotal;

    @Column(name = "students_with_result")
    private Integer studentsWithResult;

    @Column(name = "students_absent")
    private Integer studentsAbsent;

    @Column(name = "students_empty")
    private Integer studentsEmpty;

    @Column(name = "possible_other_subgroup_count")
    private Integer possibleOtherSubgroupCount;

    @Column(name = "avg_percent")
    private Double avgPercent;

    @Column(name = "avg_mark")
    private Double avgMark;

    @Column(name = "success_percent")
    private Double successPercent;

    @Column(name = "quality_percent")
    private Double qualityPercent;

    @Column(name = "problem_tasks_count")
    private Integer problemTasksCount;

    @Column(name = "problem_topics_count")
    private Integer problemTopicsCount;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = false;

    @Column(name = "specification_found", nullable = false)
    private boolean specificationFound = false;

    @Column(name = "specification_id")
    private Long specificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "specification_source", length = 50)
    private PaSpecificationMatchSource specificationSource = PaSpecificationMatchSource.NOT_FOUND;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private PaAnalysisStatus analysisStatus = PaAnalysisStatus.NOT_ANALYZED;

    @Column(name = "analysis_message", length = 4000)
    private String analysisMessage;

    @Column(name = "analysis_error_log_path", length = 2000)
    private String analysisErrorLogPath;

    @Column(name = "analysis_error_log_file_name", length = 1000)
    private String analysisErrorLogFileName;

    @Column(name = "analysis_started_at")
    private LocalDateTime analysisStartedAt;

    @Column(name = "analysis_finished_at")
    private LocalDateTime analysisFinishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
