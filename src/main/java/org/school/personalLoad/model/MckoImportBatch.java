package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mcko_import_batch")
public class MckoImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "imported_rows")
    private int importedRows;

    @Column(name = "skipped_rows")
    private int skippedRows;
}
