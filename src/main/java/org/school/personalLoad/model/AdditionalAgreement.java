package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "additional_agreement")
public class AdditionalAgreement {
    public enum Status { WAITING_FOR_MEMO, DRAFT, READY, ISSUED, SIGNING, SIGNED, EXPIRED, CANCELLED, ANNULLED, REJECTED, REQUIRES_DECISION }
    public enum ChangeMode { AMEND, CANCEL_AND_RESTATE }
    public enum Kind { PAY_TERMS, ADDITIONAL_WORK }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "teacher_id") private Long teacherId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;
    @Column(name = "contract_id") private Long contractId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmploymentContract contract;
    private Long serviceMemoId;
    private Long loadServiceMemoId;
    @Column(nullable = false) private String academicYear;
    @Column(nullable = false) private String internalNumber;
    private String visibleNumber;
    @Column(nullable = false) private int revision = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Kind kind = Kind.PAY_TERMS;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ChangeMode changeMode = ChangeMode.AMEND;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DRAFT;
    private LocalDate documentDate;
    @Column(nullable = false) private LocalDate validFrom;
    @Column(nullable = false) private LocalDate validTo;
    @Column(length = 2000) private String summary;
    @Lob private String conditionsJson;
    @Lob private String personalDataSnapshotJson;
    @Lob private String sourceSnapshotJson;
    private BigDecimal totalAmount;
    @Lob private byte[] generatedDocument;
    @Lob private byte[] currentDocument;
    private String currentFilename;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private String createdBy;
    private LocalDateTime issuedAt;
    private String issuedBy;
    private boolean reissueRequired;
    private LocalDateTime annulledAt;
    private String annulledBy;
    @Column(length = 2000) private String annulReason;
    private Long replacesAgreementId;
}
