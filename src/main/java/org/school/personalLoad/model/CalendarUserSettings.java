package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.school.personalLoad.auth.AppUser;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "calendar_user_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_calendar_user_settings_user", columnNames = "user_id")
})
public class CalendarUserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser user;

    @Column(nullable = false, length = 16)
    private String color = "#2563eb";

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false, length = 32)
    private CalendarEventVisibility defaultVisibility = CalendarEventVisibility.PARTICIPANTS;

    @ManyToMany
    @JoinTable(name = "calendar_user_shared_viewer",
            joinColumns = @JoinColumn(name = "settings_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_calendar_user_shared_viewer",
                    columnNames = {"settings_id", "teacher_id"}))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<TeacherDirectoryEntry> sharedWith = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
