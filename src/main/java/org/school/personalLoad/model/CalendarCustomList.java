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
@Table(name = "calendar_custom_list", uniqueConstraints = {
        @UniqueConstraint(name = "uk_calendar_custom_list_owner_name", columnNames = {"owner_user_id", "name"})
}, indexes = @Index(name = "idx_calendar_custom_list_owner", columnList = "owner_user_id"))
public class CalendarCustomList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser owner;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToMany
    @JoinTable(name = "calendar_custom_list_member",
            joinColumns = @JoinColumn(name = "list_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_calendar_custom_list_member",
                    columnNames = {"list_id", "teacher_id"}))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<TeacherDirectoryEntry> members = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
