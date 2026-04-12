package org.school.personalLoad.model.contingent;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contingent_snapshot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contingent_snapshot_year_date", columnNames = {"academicYear", "snapshotDate"})
})
public class ContingentSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String academicYear;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private LocalDateTime importedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String sourceFileName;
}

