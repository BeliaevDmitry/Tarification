package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "correction_specialist_catalog_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_correction_specialist_name", columnNames = "name")
})
public class CorrectionSpecialistCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
