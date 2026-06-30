package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contingent_class_size_override", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contingent_class_size_override", columnNames = {"academic_year", "class_name"})
})
public class ContingentClassSizeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "manual_students")
    private Integer manualStudents;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
