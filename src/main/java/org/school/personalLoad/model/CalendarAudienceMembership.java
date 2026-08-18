package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "calendar_audience_membership", uniqueConstraints = {
        @UniqueConstraint(name = "uk_calendar_audience_group_teacher", columnNames = {"group_code", "teacher_id"})
}, indexes = {
        @Index(name = "idx_calendar_audience_group", columnList = "group_code"),
        @Index(name = "idx_calendar_audience_teacher", columnList = "teacher_id")
})
public class CalendarAudienceMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false, length = 40)
    private CalendarAudienceGroup groupCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;
}
