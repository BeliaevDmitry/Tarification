package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_import_batch")
@Getter
@Setter
public class VsokoMckoImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "files_total", nullable = false)
    private int filesTotal;

    @Column(name = "files_processed", nullable = false)
    private int filesProcessed;

    @Column(name = "files_failed", nullable = false)
    private int filesFailed;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;
}
