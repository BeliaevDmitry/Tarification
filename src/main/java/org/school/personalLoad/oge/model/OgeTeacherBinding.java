package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oge_teacher_binding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oge_teacher_binding", columnNames = {
                "academic_year", "class_name", "full_name", "subject_name"
        })
})
@Getter
@Setter
public class OgeTeacherBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

