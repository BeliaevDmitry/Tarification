package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Data
@Entity
@Table(name = "meta_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_meta_group_scope", columnNames = {"numberSchoolBuilding", "parallel", "name", "classType"})
})
public class MetaGroup {

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
    private Integer parallel;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String classType = "NORMAL";

    private Long studyPeriodSettingId;
}
