package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_teacher_class_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vsoko_mcko_teacher_assignment", columnNames = {
                "academic_year", "class_name", "subject_name"
        })
}, indexes = {
        @Index(name = "idx_vsoko_mcko_assignment_teacher", columnList = "teacher_id"),
        @Index(name = "idx_vsoko_mcko_assignment_year", columnList = "academic_year")
})
@Getter
@Setter
public class MckoTeacherClassAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "subject_name", nullable = false, length = 500)
    private String subjectName;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "teacher_fio_snapshot", nullable = false, length = 500)
    private String teacherFioSnapshot;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
