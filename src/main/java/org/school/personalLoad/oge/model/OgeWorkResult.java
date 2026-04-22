package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oge_work_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oge_work_result", columnNames = {"full_name", "subject_name"})
})
@Getter
@Setter
public class OgeWorkResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "test_score")
    private Integer testScore;

    @Column(name = "grade")
    private Integer grade;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "source_file", length = 1000)
    private String sourceFile;
}
