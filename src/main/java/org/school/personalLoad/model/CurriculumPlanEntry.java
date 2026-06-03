package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "curriculum_plan_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_curriculum_entry_scope", columnNames = {"numberSchoolBuilding", "academicYear", "stage", "className", "subjectName", "educationLevel", "curriculumPart", "studyPeriod"})
})
public class CurriculumPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    @Column(nullable = false)
    private String academicYear = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurriculumStage stage = CurriculumStage.NOO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyPeriod studyPeriod = StudyPeriod.YEAR;

    private Long studyPeriodSettingId;

    @Column(nullable = false)
    private boolean deprecated = false;

    @Column(nullable = false)
    private String className;

    @Column(name = "class_id", insertable = false, updatable = false)
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ClassroomLeadershipEntry classRef;

    @Column(name = "meta_group_id", insertable = false, updatable = false)
    private Long metaGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MetaGroup metaGroupRef;

    @Column(nullable = false)
    private String subjectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectCatalogEntry subject;

    @Column(nullable = false)
    private java.math.BigDecimal plannedHours;

    @Column(nullable = false)
    private boolean subgroupRequired;

    @Column(nullable = false)
    private Integer subgroupCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    private Integer subgroup1Hours;

    @Enumerated(EnumType.STRING)
    private EducationLevel subgroup1EducationLevel;

    private Integer subgroup2Hours;

    @Enumerated(EnumType.STRING)
    private EducationLevel subgroup2EducationLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurriculumPart curriculumPart = CurriculumPart.CORE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean metaGroup = false;
}
