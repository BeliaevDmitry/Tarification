package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_primary_subject_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_primary_subject_year", columnNames = {"academicYear", "teacherId"})
})
public class TeacherPrimarySubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private Long teacherId;

    @Column(nullable = false)
    private String primarySubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrimarySubjectAssignmentMode mode = PrimarySubjectAssignmentMode.AUTO;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
