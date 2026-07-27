package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_directory_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_directory_fio", columnNames = "fioTeacher")
})
public class TeacherDirectoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fioTeacher;

    private String fioTeacherDative;
    private String initials;
    private String initialsDative;
    private String phone;
    @Column(unique = true)
    private String email;
    private String additionalDuties;
    private String numberSchoolBuilding;
    private String primaryPosition;
    private String personnelNumber;
    private String employmentType;
    private LocalDate employmentDate;
    @Column(name = "last_one_c_sync_at")
    private LocalDateTime lastOneCSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_group_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BuildingGroup buildingGroup;

    private LocalDate dismissalDate;
    private LocalDate plannedDismissalDate;
    private String plannedDismissalComment;
    private String plannedDismissalMarkedBy;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    private LocalDateTime archivedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
