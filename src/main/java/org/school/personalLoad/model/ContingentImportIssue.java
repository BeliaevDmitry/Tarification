package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "contingent_import_issue", indexes = {
        @Index(name = "idx_contingent_import_issue_snapshot", columnList = "snapshot_id")
})
public class ContingentImportIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "full_name", length = 500)
    private String fullName;

    @Column(name = "placement_name", length = 500)
    private String placementName;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    private String rawPayload;
}
