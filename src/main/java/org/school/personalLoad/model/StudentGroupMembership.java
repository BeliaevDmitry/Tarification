package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_group_membership", indexes = {
        @Index(name = "idx_student_group_membership_student", columnList = "student_id,academic_year"),
        @Index(name = "idx_student_group_membership_scope", columnList = "curriculum_entry_id,meta_group_id")
})
public class StudentGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "curriculum_entry_id")
    private Long curriculumEntryId;

    @Column(name = "meta_group_id")
    private Long metaGroupId;

    @Column(name = "group_name_educational_plan", nullable = false)
    private String groupNameEducationalPlan;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private StudentGroupMembershipSource source = StudentGroupMembershipSource.MANUAL;

    @Column(name = "iup_subject_line_id")
    private Long iupSubjectLineId;

    @Column(name = "source_batch_id")
    private Long sourceBatchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
