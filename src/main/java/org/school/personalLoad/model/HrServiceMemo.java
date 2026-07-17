package org.school.personalLoad.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hr_service_memo")
public class HrServiceMemo {
    public enum Status { DRAFT, ISSUED, RECEIVED_BY_HR, EXECUTED, ANNULLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String academicYear;
    @Column(nullable = false) private String title;
    @Lob private String itemsJson;
    private LocalDate documentDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DRAFT;
    @Lob private byte[] documentContent;
    private String documentFilename;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private String createdBy;
    private LocalDateTime receivedAt;
    private String receivedBy;
    private LocalDateTime annulledAt;
    private String annulledBy;
    @Column(length = 2000) private String annulReason;
}
