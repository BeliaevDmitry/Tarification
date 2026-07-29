package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "nosology_catalog_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nosology_catalog_code", columnNames = "code")
})
public class NosologyCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ovz_category")
    private String ovzCategory;

    @Column(name = "aoop_variant")
    private String aoopVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_category", nullable = false)
    private StudentCategory studentCategory;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
