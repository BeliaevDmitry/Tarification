package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_profile", indexes = {
        @Index(name = "idx_student_profile_record", columnList = "normalized_record_number"),
        @Index(name = "idx_student_profile_name_birth", columnList = "normalized_full_name,birth_date")
})
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "current_full_name", nullable = false)
    private String currentFullName;

    @Column(name = "normalized_full_name", nullable = false)
    private String normalizedFullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "record_number")
    private String recordNumber;

    @Column(name = "normalized_record_number")
    private String normalizedRecordNumber;

    @Column(name = "child_phone", length = 100)
    private String childPhone;

    @Column(name = "representative_name", length = 500)
    private String representativeName;

    @Column(name = "representative_phone", length = 100)
    private String representativePhone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "first_seen_date")
    private LocalDate firstSeenDate;

    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
