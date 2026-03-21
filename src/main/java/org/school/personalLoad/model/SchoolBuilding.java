package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "school_building", uniqueConstraints = {
        @UniqueConstraint(name = "uk_school_building_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_school_building_head_user", columnNames = "head_user_id")
})
public class SchoolBuilding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "head_user_id")
    private Long headUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
