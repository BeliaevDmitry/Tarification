package org.school.personalLoad.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hr_service_memo")
public class HrServiceMemo {
    public enum Status { DRAFT, ISSUED, SIGNED, RECEIVED_BY_HR, EXECUTED, ANNULLED, ARCHIVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String academicYear;
    // The database may already contain legacy memos created before employee binding was introduced.
    // New memos are required to have a teacher by HrDocumentService validation.
    @Column(name = "teacher_id") private Long teacherId;
    @Column(name = "contract_id") private Long contractId;
    private Long catalogItemId;
    @Column(nullable = false) private String title;
    private String assignmentName;
    @Column(length = 4000) private String assignmentText;
    @Column(length = 4000) private String agreementText;
    private String contractClause;
    @Lob private String dutiesText;
    private java.math.BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    @Column(nullable = false) private boolean separateAgreement;
    @Lob private String itemsJson;
    private LocalDate documentDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DRAFT;
    @Lob private byte[] documentContent;
    private String documentFilename;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private String createdBy;
    private LocalDateTime issuedAt;
    private String issuedBy;
    private String issuedByFullName;
    private String issuedByPosition;
    private LocalDateTime signedAt;
    private String signedBy;
    private LocalDateTime receivedAt;
    private String receivedBy;
    private LocalDateTime annulledAt;
    private String annulledBy;
    @Column(length = 2000) private String annulReason;
    private LocalDateTime archivedAt;
    private String archivedBy;
    @Column(length = 2000) private String archiveReason;
}
