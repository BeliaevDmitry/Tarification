package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_work_material", indexes = {
        @Index(name = "idx_pa_material_year_subject", columnList = "academic_year, subject_name"),
        @Index(name = "idx_pa_material_scope", columnList = "scope_type, scope_value")
})
@Getter
@Setter
public class PaWorkMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PaScopeType scopeType;

    @Column(name = "scope_value", nullable = false, length = 30)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_code", nullable = false, length = 20)
    private PaLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 20)
    private PaWorkType workType;

    @Column(name = "variant_count", nullable = false)
    private Integer variantCount;

    @Column(name = "text_original_file_name", length = 1000)
    private String textOriginalFileName;

    @Column(name = "text_stored_file_name", length = 1200)
    private String textStoredFileName;

    @Column(name = "text_uploaded_by_username", length = 255)
    private String textUploadedByUsername;

    @Column(name = "text_uploaded_by_fio", length = 500)
    private String textUploadedByFio;

    @Column(name = "text_uploaded_at")
    private LocalDateTime textUploadedAt;

    @Column(name = "answers_original_file_name", length = 1000)
    private String answersOriginalFileName;

    @Column(name = "answers_stored_file_name", length = 1200)
    private String answersStoredFileName;

    @Column(name = "answers_uploaded_by_username", length = 255)
    private String answersUploadedByUsername;

    @Column(name = "answers_uploaded_by_fio", length = 500)
    private String answersUploadedByFio;

    @Column(name = "answers_uploaded_at")
    private LocalDateTime answersUploadedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
