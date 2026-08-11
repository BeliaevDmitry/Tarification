package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_class_summary", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vsoko_mcko_class_summary_fingerprint", columnNames = "fingerprint")
}, indexes = {
        @Index(name = "idx_vsoko_mcko_class_summary_work", columnList = "academic_year,class_name,subject_name,diagnostic_date"),
        @Index(name = "idx_vsoko_mcko_class_summary_source", columnList = "source_file_id")
})
@Getter
@Setter
public class MckoClassDiagnosticSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "subject_name", nullable = false, length = 500)
    private String subjectName;

    @Column(name = "diagnostic_date")
    private LocalDate diagnosticDate;

    @Column(name = "school_name", length = 500)
    private String schoolName;

    @Column(name = "result_kind", nullable = false, length = 40)
    private String resultKind;

    @Column(name = "participant_count")
    private Integer participantCount;

    @Column(name = "average_score")
    private Double averageScore;

    @Column(name = "average_percent")
    private Double averagePercent;

    @Column(name = "city_percent")
    private Double cityPercent;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
