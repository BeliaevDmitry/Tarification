package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "building_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_building_group_code", columnNames = "code")
})
public class BuildingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;
}
