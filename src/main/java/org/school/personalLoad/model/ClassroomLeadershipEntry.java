package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "classroom_leadership_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_classroom_leadership_class_building", columnNames = {"academicYear", "numberSchoolBuilding", "className"})
})
public class ClassroomLeadershipEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_building_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SchoolBuilding schoolBuilding;

    @com.fasterxml.jackson.annotation.JsonProperty("schoolBuildingId")
    public Long getSchoolBuildingId() {
        return schoolBuilding == null ? null : schoolBuilding.getId();
    }

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String classDirection;

    @Column(nullable = false)
    private String fioTeacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;

    @com.fasterxml.jackson.annotation.JsonProperty("teacherId")
    public Long getTeacherId() {
        return teacher == null ? null : teacher.getId();
    }

    @Column(nullable = false)
    private String campusAddress;

    @Column(nullable = false)
    private String classType = "NORMAL";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
