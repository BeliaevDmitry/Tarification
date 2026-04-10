package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "meta_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_meta_group_scope", columnNames = {"numberSchoolBuilding", "parallel", "name"})
})
public class MetaGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @Column(nullable = false)
    private Integer parallel;

    @Column(nullable = false)
    private String name;
}
