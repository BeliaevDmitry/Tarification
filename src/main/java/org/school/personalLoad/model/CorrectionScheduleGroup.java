package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "correction_schedule_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_correction_group_number", columnNames = {"academic_year", "staff_id", "sequence_number"})
}, indexes = {
        @Index(name = "idx_correction_group_staff_year", columnList = "staff_id,academic_year")
})
public class CorrectionScheduleGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrectionSpecialistStaff staff;

    @Column(name = "weekday", nullable = false)
    private Integer weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
