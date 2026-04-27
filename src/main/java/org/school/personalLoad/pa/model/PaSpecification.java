package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_specification", indexes = {
        @Index(name = "idx_pa_spec_year_subject", columnList = "academic_year, subject_name"),
        @Index(name = "idx_pa_spec_scope", columnList = "scope_type, scope_value"),
        @Index(name = "idx_pa_spec_pair", columnList = "pair_key")
})
@Getter
@Setter
public class PaSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "subject_catalog_id")
    private Long subjectCatalogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PaScopeType scopeType = PaScopeType.PARALLEL;

    @Column(name = "scope_value", nullable = false, length = 30)
    private String scopeValue;

    @Column(name = "school_class_id")
    private Long schoolClassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_code", nullable = false, length = 20)
    private PaLevel level = PaLevel.BASIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 20)
    private PaWorkType workType = PaWorkType.EXIT;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "school_name", length = 300)
    private String schoolName;

    @Column(name = "teacher_fio", length = 500)
    private String teacherFio;

    @Column(name = "teacher_fio_normalized", length = 500)
    private String teacherFioNormalized;

    @Column(name = "grade5_percent")
    private Integer grade5Percent;

    @Column(name = "grade4_percent")
    private Integer grade4Percent;

    @Column(name = "grade3_percent")
    private Integer grade3Percent;

    @Column(name = "pair_key", length = 150)
    private String pairKey;

    @Column(name = "source_file_name", length = 1000)
    private String sourceFileName;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "active_version", nullable = false)
    private boolean activeVersion = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
