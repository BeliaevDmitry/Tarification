package org.school.personalLoad.pa.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pa_class_level_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pa_class_level_assignment", columnNames = {"academicYear", "subjectName", "className", "workType"})
})
public class PaClassLevelAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String academicYear;
    @Column(nullable = false)
    private String subjectName;
    @Column(nullable = false)
    private String className;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaWorkType workType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaLevel level;
    @Column(nullable = false)
    private boolean manual = true;
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
