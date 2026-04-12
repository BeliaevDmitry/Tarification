package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "academic_year", uniqueConstraints = {
        @UniqueConstraint(name = "uk_academic_year_name", columnNames = "name")
})
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer startYear;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

