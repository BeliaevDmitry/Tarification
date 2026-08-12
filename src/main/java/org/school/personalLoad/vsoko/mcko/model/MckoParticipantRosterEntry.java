package org.school.personalLoad.vsoko.mcko.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vsoko_mcko_participant_roster", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vsoko_mcko_roster_fingerprint", columnNames = "fingerprint")
}, indexes = {
        @Index(name = "idx_vsoko_mcko_roster_work", columnList = "academic_year,class_name,subject_name,work_date"),
        @Index(name = "idx_vsoko_mcko_roster_code", columnList = "student_code"),
        @Index(name = "idx_vsoko_mcko_roster_student", columnList = "student_id")
})
@Getter
@Setter
public class MckoParticipantRosterEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_fio", nullable = false, length = 500)
    private String studentFio;

    @Column(name = "student_code", length = 100)
    private String studentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_link_status", nullable = false, length = 40)
    private MckoStudentLinkStatus studentLinkStatus = MckoStudentLinkStatus.NOT_FOUND;

    @Column(name = "student_link_message", length = 1000)
    private String studentLinkMessage;

    @Column(name = "student_number", nullable = false)
    private Integer studentNumber;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(name = "subject_name", nullable = false, length = 500)
    private String subjectName;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "school_name", length = 500)
    private String schoolName;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
