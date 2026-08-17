package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "probe_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_probe_order_event_building_year",
                columnNames = {"academic_year", "external_event_id", "school_building_id"})
}, indexes = {
        @Index(name = "idx_probe_order_year_date", columnList = "academic_year,event_date"),
        @Index(name = "idx_probe_order_status_date", columnList = "status,event_date")
})
public class ProbeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "external_event_id", nullable = false, length = 255)
    private String externalEventId;

    @Column(name = "event_name", nullable = false, length = 1000)
    private String eventName;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "venue", nullable = false, length = 1000)
    private String venue;

    @Column(name = "event_address", nullable = false, length = 1500)
    private String eventAddress;

    @Column(name = "organizer", length = 1000)
    private String organizer;

    @Column(name = "partner", length = 1000)
    private String partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_building_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SchoolBuilding schoolBuilding;

    @Column(name = "gathering_time")
    private LocalTime gatheringTime;

    @Column(name = "gathering_place", nullable = false, length = 1500)
    private String gatheringPlace;

    @Column(name = "return_time")
    private LocalTime returnTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_companion_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry primaryCompanion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_companion_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry secondaryCompanion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_teacher_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry signer;

    @Column(name = "signer_position", length = 255)
    private String signerPosition;

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProbeOrderStatus status = ProbeOrderStatus.DRAFT;

    @Column(name = "building_approved_at")
    private LocalDateTime buildingApprovedAt;

    @Column(name = "building_approved_by", length = 255)
    private String buildingApprovedBy;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_by", length = 255)
    private String releasedBy;

    @Column(name = "source_file_name", length = 512)
    private String sourceFileName;

    @Column(name = "source_uploaded_at", nullable = false)
    private LocalDateTime sourceUploadedAt = LocalDateTime.now();

    @Column(name = "source_uploaded_by", nullable = false, length = 255)
    private String sourceUploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("classNameSnapshot ASC, fullNameSnapshot ASC")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ProbeOrderParticipant> participants = new ArrayList<>();
}
