package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mcko_subject_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mcko_subject_mapping", columnNames = {"mcko_subject", "subject_id", "grade_band"})
})
public class MckoSubjectMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mcko_subject", nullable = false)
    private String mckoSubject;

    @Column(name = "subject_id")
    private Long subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectCatalogEntry subject;

    @Column(name = "subject_name")
    private String subjectName;

    @Column(name = "grade_band", nullable = false, columnDefinition = "varchar(32) default 'ALL'")
    private String gradeBand = "ALL";

    @Column(name = "ignored", nullable = false, columnDefinition = "boolean default false")
    private boolean ignored;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default now()")
    private LocalDateTime createdAt = LocalDateTime.now();
}
