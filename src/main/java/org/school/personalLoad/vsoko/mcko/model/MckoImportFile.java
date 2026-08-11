package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_import_file", indexes = {
        @Index(name = "idx_vsoko_mcko_import_file_batch", columnList = "batch_id"),
        @Index(name = "idx_vsoko_mcko_import_file_status", columnList = "status")
})
@Getter
@Setter
public class MckoImportFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "file_name", nullable = false, length = 1000)
    private String fileName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_kind", length = 80)
    private String fileKind;

    @Column(name = "detected_academic_year", length = 500)
    private String detectedAcademicYear;

    @Column(name = "detected_work_date", length = 1000)
    private String detectedWorkDate;

    @Column(name = "detected_subject", length = 2000)
    private String detectedSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MckoFileStatus status = MckoFileStatus.PROCESSING;

    @Column(name = "reason", length = 4000)
    private String reason;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
