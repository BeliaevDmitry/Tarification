package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oge_file_import_log")
@Getter
@Setter
public class OgeFileImportLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "work_source", nullable = false, length = 30)
    private String workSource;

    @Column(name = "file_name", nullable = false, length = 1000)
    private String fileName;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "message", length = 4000)
    private String message;

    @Column(name = "records_count", nullable = false)
    private int recordsCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

