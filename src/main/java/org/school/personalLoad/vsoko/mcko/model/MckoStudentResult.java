package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vsoko_mcko_result_fingerprint", columnNames = "fingerprint")
}, indexes = {
        @Index(name = "idx_vsoko_mcko_result_student", columnList = "student_id"),
        @Index(name = "idx_vsoko_mcko_result_year_class", columnList = "academic_year,class_name"),
        @Index(name = "idx_vsoko_mcko_result_subject", columnList = "subject_name"),
        @Index(name = "idx_vsoko_mcko_result_teacher", columnList = "teacher_id"),
        @Index(name = "idx_vsoko_mcko_result_link", columnList = "student_link_status")
})
@Getter
@Setter
public class MckoStudentResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_fio_snapshot", length = 500)
    private String studentFioSnapshot;

    @Column(name = "student_code", length = 100)
    private String studentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_link_status", nullable = false, length = 40)
    private MckoStudentLinkStatus studentLinkStatus = MckoStudentLinkStatus.NOT_FOUND;

    @Column(name = "student_link_message", length = 1000)
    private String studentLinkMessage;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(name = "subject_name", nullable = false, length = 500)
    private String subjectName;

    @Column(name = "diagnostic_date")
    private LocalDate diagnosticDate;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "school_name", length = 500)
    private String schoolName;

    @Column(name = "class_level", length = 100)
    private String classLevel;

    @Column(name = "city_level", length = 100)
    private String cityLevel;

    @Column(name = "parallel_no")
    private Integer parallel;

    @Column(name = "class_letter", length = 20)
    private String classLetter;

    @Column(name = "variant_name", length = 100)
    private String variantName;

    @Column(name = "score")
    private Double score;

    @Column(name = "percent_value")
    private Double percent;

    @Column(name = "mark")
    private Integer mark;

    @Column(name = "student_number")
    private Integer studentNumber;

    @Column(name = "task_scores_json", columnDefinition = "text")
    private String taskScoresJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 40)
    private MckoResultType resultType = MckoResultType.STANDARD;

    @Column(name = "mastery_level", length = 100)
    private String masteryLevel;

    @Column(name = "section_1_percent")
    private Double section1Percent;

    @Column(name = "section_2_percent")
    private Double section2Percent;

    @Column(name = "section_3_percent")
    private Double section3Percent;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "teacher_fio_snapshot", length = 500)
    private String teacherFioSnapshot;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "source_row")
    private Integer sourceRow;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
