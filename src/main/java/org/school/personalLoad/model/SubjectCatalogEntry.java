package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subject_catalog_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_catalog_subject_name", columnNames = "subjectName")
})
public class SubjectCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubjectCatalogType subjectType;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
