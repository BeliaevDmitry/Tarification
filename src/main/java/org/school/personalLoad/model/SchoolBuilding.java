package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "school_building")
public class SchoolBuilding {

    public Long getBuildingGroupId() {
        return buildingGroup == null ? null : buildingGroup.getId();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_group_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String managerFio;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
