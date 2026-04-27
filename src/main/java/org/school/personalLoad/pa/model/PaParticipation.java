package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_participation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pa_participation_scope", columnNames = {
                "academic_year", "subject_name", "scope_type", "scope_value", "level_code"
        })
})
@Getter
@Setter
public class PaParticipation {

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

    @Column(name = "participates", nullable = false)
    private boolean participates = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
