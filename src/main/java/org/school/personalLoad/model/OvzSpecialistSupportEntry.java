package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ovz_specialist_support_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ovz_support_entry", columnNames = {"academic_year", "student_id", "specialist_id"})
}, indexes = {
        @Index(name = "idx_ovz_support_entry_student_year", columnList = "student_id,academic_year")
})
public class OvzSpecialistSupportEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CorrectionSpecialistCatalogEntry specialist;

    @Column(name = "child_deficits", columnDefinition = "text")
    private String childDeficits;

    @Column(name = "child_resources", columnDefinition = "text")
    private String childResources;

    @Column(name = "annual_tasks", columnDefinition = "text")
    private String annualTasks;

    @Column(name = "planned_results", columnDefinition = "text")
    private String plannedResults;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_by_name", length = 255)
    private String updatedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
