package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pa_spec_import_log")
public class PaSpecImportLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String academicYear;

    @Column(nullable = false, length = 1000)
    private String fileName;

    @Column(nullable = false, length = 2000)
    private String subjects;

    @Column(nullable = false, length = 255)
    private String parallels;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(nullable = false)
    private Integer recordsCount;

    @Column(nullable = false, length = 255)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
