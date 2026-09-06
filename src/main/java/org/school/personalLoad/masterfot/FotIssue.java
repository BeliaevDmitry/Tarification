package org.school.personalLoad.masterfot;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "master_fot_issue", indexes = @Index(name = "idx_master_fot_issue_year", columnList = "academic_year"))
public class FotIssue {
    @Id @Column(length = 64) private String id;
    @Column(name = "academic_year", nullable = false) private String academicYear;
    @Column(nullable = false, columnDefinition = "text") private String findingJson;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false, length = 32) private String status = "OPEN";
    @Column(length = 4000) private String comment = "";
    private boolean archived;
    private Long firstBatchId;
    private Long lastBatchId;
    private Long archivedBatchId;
    private LocalDateTime updatedAt;
    private String updatedBy;
    @Version private long version;
}
