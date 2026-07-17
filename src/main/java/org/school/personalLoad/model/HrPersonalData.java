package org.school.personalLoad.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hr_personal_data", uniqueConstraints = @UniqueConstraint(name = "uk_hr_personal_teacher", columnNames = "teacher_id"))
public class HrPersonalData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "teacher_id", nullable = false) private Long teacherId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "teacher_id", insertable = false, updatable = false) private TeacherDirectoryEntry teacher;
    private LocalDate birthDate;
    private String passportSeries;
    private String passportNumber;
    private String passportIssuedBy;
    private LocalDate passportIssueDate;
    private String passportDepartmentCode;
    @Column(length = 2000) private String registrationAddress;
    @Column(length = 2000) private String actualAddress;
    private String phone;
    private String inn;
    private String snils;
    @Column(nullable = false) private int revision = 1;
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    private String updatedBy;
}
