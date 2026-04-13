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
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String fioTeacher;

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

    private String correctedFilename;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime archivedAt;
}
