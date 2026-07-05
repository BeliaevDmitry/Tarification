package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mcko_certificate", indexes = {
        @Index(name = "idx_mcko_certificate_teacher", columnList = "teacher_id"),
        @Index(name = "idx_mcko_certificate_subject", columnList = "mcko_subject"),
        @Index(name = "idx_mcko_certificate_date", columnList = "diagnostic_date")
})
public class MckoCertificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id")
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;

    @Column(name = "teacher_fio_snapshot", nullable = false)
    private String teacherFioSnapshot;

    @Column(name = "mcko_subject", nullable = false)
    private String mckoSubject;

    @Column(name = "exam_type", nullable = false)
    private String examType;

    @Column(name = "diagnostic_date", nullable = false)
    private LocalDate diagnosticDate;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private boolean published;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MckoCertificateSource source = MckoCertificateSource.MANUAL;

    private String comment;
    @Column(name = "scan_file_name")
    private String scanFileName;

    @Column(name = "scan_content_type")
    private String scanContentType;

    @Lob
    @Column(name = "scan_content")
    @JsonIgnore
    private byte[] scanContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MckoImportBatch importBatch;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
