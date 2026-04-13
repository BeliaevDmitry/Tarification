package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "academic_year", uniqueConstraints = {
        @UniqueConstraint(name = "uk_academic_year_code", columnNames = {"code"})
})
public class AcademicYearConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private boolean continuityApplied = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
