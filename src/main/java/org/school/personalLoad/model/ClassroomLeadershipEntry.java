package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "classroom_leadership_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_classroom_leadership_class", columnNames = {"academicYear", "numberSchoolBuilding", "className"})
})
public class ClassroomLeadershipEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String numberSchoolBuilding;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String classDirection;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(nullable = false)
    private String campusAddress;

    @Column(nullable = false)
    private boolean manualBuildingAssignment = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
