package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subject_catalog_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_catalog_name_type", columnNames = {"subjectName", "subjectType"})
})
public class SubjectCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubjectType subjectType;

    @Column(nullable = false)
    private String subjectAreaName = SubjectAreaNames.defaultArea();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_area_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("subjectArea")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectArea subjectAreaRef;

    @Column(nullable = false)
    private java.math.BigDecimal subjectCoefficient = java.math.BigDecimal.ONE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
