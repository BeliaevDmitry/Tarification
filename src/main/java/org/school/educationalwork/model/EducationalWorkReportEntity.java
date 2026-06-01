package org.school.educationalwork.model;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "educational_work_report", uniqueConstraints = {
        @UniqueConstraint(name = "uk_educational_work_report_year_class", columnNames = {"academic_year", "school_class"})
})
public class EducationalWorkReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 32)
    private String academicYear;

    @Column(name = "school_class", nullable = false, length = 32)
    private String schoolClass;

    @Column(name = "teacher_full_name", nullable = false)
    private String teacherFullName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Lob
    @Column(name = "file_bytes", nullable = false)
    private byte[] fileBytes;

    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
