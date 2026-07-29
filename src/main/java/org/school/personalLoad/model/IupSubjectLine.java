package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "iup_subject_line", indexes = {
        @Index(name = "idx_iup_subject_line_plan", columnList = "iup_plan_id")
})
public class IupSubjectLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iup_plan_id", nullable = false)
    private IupPlan iupPlan;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "curriculum_entry_id")
    private Long curriculumEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_mode", nullable = false)
    private IupParticipationMode participationMode = IupParticipationMode.INDIVIDUAL;

    @Column(name = "class_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal classHours = BigDecimal.ZERO;

    @Column(name = "individual_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal individualHours = BigDecimal.ZERO;

    @Column(name = "group_name_educational_plan")
    private String groupNameEducationalPlan;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
