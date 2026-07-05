package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "salary_group_coefficient_subject", uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_group_coefficient_subject_id", columnNames = {"subject_id"})
})
public class SalaryGroupCoefficientSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectCatalogEntry subject;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
