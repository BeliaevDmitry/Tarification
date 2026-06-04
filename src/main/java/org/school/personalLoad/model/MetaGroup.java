package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Data
@Entity
@Table(name = "meta_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_meta_group_year_scope", columnNames = {"academic_year", "numberSchoolBuilding", "parallel", "name", "classType"})
})
public class MetaGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_group_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    public Long getBuildingGroupId() {
        return buildingGroup == null ? null : buildingGroup.getId();
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_building_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SchoolBuilding schoolBuilding;

    public Long getSchoolBuildingId() {
        return schoolBuilding == null ? null : schoolBuilding.getId();
    }

    @Column(nullable = false)
    private Integer parallel;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String classType = "NORMAL";

    private Long studyPeriodSettingId;
}
