package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "correction_specialist_staff", uniqueConstraints = {
        @UniqueConstraint(name = "uk_correction_specialist_staff", columnNames = {"specialist_id", "teacher_id"})
})
public class CorrectionSpecialistStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrectionSpecialistCatalogEntry specialist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
