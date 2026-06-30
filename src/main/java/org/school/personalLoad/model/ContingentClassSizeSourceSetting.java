package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contingent_class_size_source_setting", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contingent_class_size_source_year", columnNames = {"academic_year"})
})
public class ContingentClassSizeSourceSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ClassSizeSource source = ClassSizeSource.AIS;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
