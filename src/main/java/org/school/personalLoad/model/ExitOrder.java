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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
@Table(name = "exit_order", indexes = {
        @Index(name = "idx_exit_order_year_date", columnList = "academic_year,event_date"),
        @Index(name = "idx_exit_order_status_date", columnList = "status,event_date"),
        @Index(name = "idx_exit_order_requester", columnList = "requested_by_user_id")
})
public class ExitOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "preamble", nullable = false, length = 2000)
    private String preamble;

    @Column(name = "event_name", nullable = false, length = 1000)
    private String eventName;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "venue", nullable = false, length = 1000)
    private String venue;

    @Column(name = "event_address", nullable = false, length = 1500)
    private String eventAddress;

    @Column(name = "gathering_time", nullable = false)
    private LocalTime gatheringTime;

    @Column(name = "gathering_place", nullable = false, length = 1500)
    private String gatheringPlace;

    @Column(name = "return_time", nullable = false)
    private LocalTime returnTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_building_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SchoolBuilding schoolBuilding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_companion_id", nullable = false)
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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "exit_order_additional_companion",
            joinColumns = @JoinColumn(name = "order_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "teacher_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(name = "uk_exit_order_additional_companion",
                    columnNames = {"order_id", "teacher_id"}))
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<TeacherDirectoryEntry> additionalCompanions = new LinkedHashSet<>();

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

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

    @Column(name = "attendance_marked_at")
    private LocalDateTime attendanceMarkedAt;

    @Column(name = "attendance_marked_by", length = 255)
    private String attendanceMarkedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("classNameSnapshot ASC, fullNameSnapshot ASC")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ExitOrderParticipant> participants = new ArrayList<>();
}
