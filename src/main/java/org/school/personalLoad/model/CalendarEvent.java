package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.school.personalLoad.auth.AppUser;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@Entity
@Table(name = "calendar_event", indexes = {
        @Index(name = "idx_calendar_event_time", columnList = "starts_at,ends_at"),
        @Index(name = "idx_calendar_event_owner", columnList = "owner_user_id")
})
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser owner;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(length = 1000)
    private String place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CalendarEventVisibility visibility = CalendarEventVisibility.PARTICIPANTS;

    @ManyToMany
    @JoinTable(name = "calendar_event_participant",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_calendar_event_participant",
                    columnNames = {"event_id", "teacher_id"}))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<TeacherDirectoryEntry> participants = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "calendar_event_participant_response", joinColumns = @JoinColumn(name = "event_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_calendar_event_participant_response",
                    columnNames = {"event_id", "teacher_id"}))
    @MapKeyColumn(name = "teacher_id")
    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private Map<Long, CalendarAttendanceStatus> participantResponses = new LinkedHashMap<>();

    @ManyToMany
    @JoinTable(name = "calendar_event_building",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "building_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_calendar_event_building",
                    columnNames = {"event_id", "building_id"}))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<SchoolBuilding> buildings = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "calendar_event_selected_person", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "teacher_id", nullable = false)
    private Set<Long> selectedPersonIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "calendar_event_selected_group", joinColumns = @JoinColumn(name = "event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false, length = 40)
    private Set<CalendarAudienceGroup> selectedGroups = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "calendar_event_selected_list", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "list_id", nullable = false)
    private Set<Long> selectedCustomListIds = new LinkedHashSet<>();

    @Column(name = "audience_summary", nullable = false, length = 2000)
    private String audienceSummary = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
