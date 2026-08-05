package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oge_work_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oge_work_result", columnNames = {
                "academic_year", "full_name", "birth_date", "snils", "subject_name",
                "work_source", "work_type", "work_date"
        })
})
@Getter
@Setter
public class OgeWorkResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "birth_date", length = 20)
    private String birthDate;

    @Column(name = "snils", length = 20)
    private String snils;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "work_source", nullable = false, length = 30)
    private String workSource = "INTERNAL";

    @Column(name = "work_type", nullable = false, length = 50)
    private String workType = "Входная";

    @Column(name = "work_date", length = 20)
    private String workDate = "";

    @Column(name = "task_scores_json", columnDefinition = "text")
    private String taskScoresJson;

    @Column(name = "test_score")
    private Integer testScore;

    @Column(name = "grade")
    private Integer grade;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "source_file", length = 1000)
    private String sourceFile;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "needs_teacher_binding", nullable = false)
    private boolean needsTeacherBinding = true;

    @Column(name = "needs_manual_student_match", nullable = false)
    private boolean needsManualStudentMatch = false;

    @Column(name = "source_issue", length = 1000)
    private String sourceIssue;
}
