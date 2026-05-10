package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_notification_record", uniqueConstraints = @UniqueConstraint(columnNames = {"academicYear", "fioTeacher"}))
public class TeacherNotificationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String fioTeacher;

    @Column(nullable = false)
    private LocalDate notificationDate;

    @Column(nullable = false)
    private String dataHash;

    @Column(nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String generatedBy;

    private LocalDateTime downloadedAt;
    private String downloadedBy;
}
