package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_directory_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_directory_fio", columnNames = "fioTeacher")
})
public class TeacherDirectoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
