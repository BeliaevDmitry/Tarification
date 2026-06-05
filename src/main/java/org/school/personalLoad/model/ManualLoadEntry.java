package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "manual_load_entry")
public class ManualLoadEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(name = "teacher_id")
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    @Column(name = "school_building_id")
    private Long schoolBuildingId;

    @Column(nullable = false)
    private String subjectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectCatalogEntry subject;

    public Long getSubjectId() {
        return subject == null ? null : subject.getId();
    }

    @Column(nullable = false)
    private String className;

    @Column(name = "class_id")
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ClassroomLeadershipEntry classRef;

    @Column(name = "meta_group_id")
    private Long metaGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MetaGroup metaGroupRef;


    public Long getSchoolBuildingId() {
        if (schoolBuildingId != null) {
            return schoolBuildingId;
        }
        if (metaGroupRef != null && metaGroupRef.getSchoolBuildingId() != null) {
            return metaGroupRef.getSchoolBuildingId();
        }
        if (classRef != null) {
            return classRef.getSchoolBuildingId();
        }
        return null;
    }

    @Column(nullable = false)
    private Integer load;

    private String groupNameEducationalPlan;

    private Integer groupLoad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationLevel educationLevel;

    @Enumerated(EnumType.STRING)
    private StudyPeriod studyPeriod;

    private LocalDate loadFromDate;

    private LocalDate loadToDate;

    private LocalDate backupLoadToDate;

    @Column(nullable = false)
    private boolean dismissalAdjusted = false;

    @Column(nullable = false)
    private boolean orphaned = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContinuityStatus continuityStatus = ContinuityStatus.UNKNOWN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
