package org.school.personalLoad.masterfot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "master_fot_batch", indexes = @Index(name = "idx_master_fot_batch_year", columnList = "academic_year"))
public class FotBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "academic_year", nullable = false) private String academicYear;
    @Column(nullable = false, length = 1000) private String filename;
    @Column(nullable = false) private LocalDate snapshotDate;
    @Column(nullable = false) private LocalDateTime importedAt;
    private String importedBy;
    private int rowCount;
    private int findingCount;
    private boolean comparisonComplete;
    @JsonIgnore @Column(nullable = false, columnDefinition = "text") private String sourceJson;
    @JsonIgnore @Column(nullable = false, columnDefinition = "text") private String findingsJson;
}
