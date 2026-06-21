package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "curriculum_module", uniqueConstraints = {
        @UniqueConstraint(name = "uk_curriculum_module_order", columnNames = {"curriculum_entry_id", "module_order"})
})
public class CurriculumModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_entry_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CurriculumPlanEntry curriculumEntry;

    @Column(name = "module_order", nullable = false)
    private Integer moduleOrder;

    @Column(name = "module_name", nullable = false)
    private String moduleName;

    @Column(name = "planned_hours", nullable = false)
    private BigDecimal plannedHours;

    @Column(name = "subgroup_required", nullable = false)
    private boolean subgroupRequired;

    @Column(name = "subgroup_count", nullable = false)
    private Integer subgroupCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", nullable = false)
    private EducationLevel educationLevel = EducationLevel.BASIC;

    @Column(name = "subgroup1_hours")
    private Integer subgroup1Hours;

    @Enumerated(EnumType.STRING)
    @Column(name = "subgroup1_education_level")
    private EducationLevel subgroup1EducationLevel;

    @Column(name = "subgroup2_hours")
    private Integer subgroup2Hours;

    @Enumerated(EnumType.STRING)
    @Column(name = "subgroup2_education_level")
    private EducationLevel subgroup2EducationLevel;
}
