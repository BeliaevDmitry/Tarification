package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_report_version", indexes = {
        @Index(name = "idx_pa_report_key", columnList = "academic_year, subject_name, scope_type, scope_value, level_code, work_type, work_date"),
        @Index(name = "idx_pa_report_created", columnList = "created_at")
})
@Getter
@Setter
public class PaReportVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PaScopeType scopeType = PaScopeType.PARALLEL;

    @Column(name = "scope_value", nullable = false, length = 30)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_code", nullable = false, length = 20)
    private PaLevel level = PaLevel.BASIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 20)
    private PaWorkType workType = PaWorkType.EXIT;

    @Column(name = "work_date")
    private java.time.LocalDate workDate;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "active_version", nullable = false)
    private boolean activeVersion = true;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACCEPTED";

    @Column(name = "validation_message", length = 4000)
    private String validationMessage;

    @Column(name = "source_file_name", length = 1000)
    private String sourceFileName;

    @Column(name = "source_file_path", length = 2000)
    private String sourceFilePath;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "teacher_fio_normalized", length = 500)
    private String teacherFioNormalized;

    @Column(name = "uploaded_by_username", length = 255)
    private String uploadedByUsername;

    @Column(name = "uploaded_by_fio", length = 500)
    private String uploadedByFio;

    @Column(name = "reported_students_count")
    private Integer reportedStudentsCount;

    @Column(name = "class_size_count")
    private Integer classSizeCount;

    @Column(name = "accepted_results_count")
    private Integer acceptedResultsCount;

    @Column(name = "downloaded_at_least_once", nullable = false)
    private boolean downloadedAtLeastOnce = false;

    @Column(name = "uploaded_back_success", nullable = false)
    private boolean uploadedBackSuccess = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
