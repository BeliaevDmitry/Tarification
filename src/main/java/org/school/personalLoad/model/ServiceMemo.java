package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "service_memo")
public class ServiceMemo {

    public enum Status {
        PROCESSED,
        RECEIVED_BY_HR,
        EXECUTED,
        ANNULLED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(nullable = false)
    private LocalDate changeStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PROCESSED;

    @Column(nullable = false)
    private String createdBy;

    @Lob
    @Column(nullable = false)
    private byte[] generatedDocument;

    @Lob
    private byte[] correctedDocument;

    @Column(nullable = false)
    private String generatedFilename;

    @Column(length = 4000)
    private String loadSignature;

    @Lob
    private String beforeSnapshotJson;

    @Lob
    private String afterSnapshotJson;

    private String correctedFilename;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime archivedAt;

    private LocalDateTime receivedAt;

    private String receivedBy;

    private LocalDateTime annulledAt;

    private String annulledBy;

    @Column(length = 2000)
    private String annulReason;
}
