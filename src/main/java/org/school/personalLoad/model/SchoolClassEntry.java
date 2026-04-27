package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "school_class_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_school_class_year_name", columnNames = {"academicYear", "className"})
})
public class SchoolClassEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false, length = 30)
    private String className;

    @Column(nullable = false)
    private Integer parallel = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
