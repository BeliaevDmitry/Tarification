package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contingent_snapshot")
public class ContingentSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private LocalDateTime importedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String sourceFileName;

    @Column(name = "import_format")
    private String importFormat = "";

    @Column(name = "skipped_rows")
    private Integer skippedRows = 0;

    @Column(nullable = false)
    private Integer totalStudents = 0;
}
