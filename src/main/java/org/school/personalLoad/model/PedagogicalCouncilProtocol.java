package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "pedagogical_council_protocol", indexes = {
        @Index(name = "idx_ped_council_year_date", columnList = "academic_year, meeting_date"),
        @Index(name = "idx_ped_council_status", columnList = "status")
})
public class PedagogicalCouncilProtocol {

    public enum SourceType {
        CONSTRUCTOR,
        ARCHIVE_WORD
    }

    public enum Status {
        DRAFT,
        REVIEW,
        REGISTERED,
        CORRECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "protocol_number", nullable = false, length = 64)
    private String protocolNumber;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "agenda_time")
    private LocalTime agendaTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private SourceType sourceType = SourceType.CONSTRUCTOR;

    @Column(name = "school_code_snapshot", nullable = false, length = 32)
    private String schoolCodeSnapshot;

    @Column(name = "school_name_snapshot", nullable = false, length = 512)
    private String schoolNameSnapshot;

    @Column(name = "attendee_count", nullable = false)
    private int attendeeCount;

    @Column(name = "chair_teacher_id")
    private Long chairTeacherId;

    @Column(name = "chair_position_snapshot", length = 255)
    private String chairPositionSnapshot;

    @Column(name = "chair_fio_snapshot", length = 255)
    private String chairFioSnapshot;

    @Column(name = "secretary_teacher_id")
    private Long secretaryTeacherId;

    @Column(name = "secretary_position_snapshot", length = 255)
    private String secretaryPositionSnapshot;

    @Column(name = "secretary_fio_snapshot", length = 255)
    private String secretaryFioSnapshot;

    @Column(name = "archive_filename", length = 512)
    private String archiveFilename;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "archive_document")
    private byte[] archiveDocument;

    @Column(name = "created_by_username", nullable = false, length = 100)
    private String createdByUsername;

    @Column(name = "created_by_fio", nullable = false, length = 255)
    private String createdByFio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "registered_by", length = 255)
    private String registeredBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PedagogicalCouncilItem> items = new ArrayList<>();
}
