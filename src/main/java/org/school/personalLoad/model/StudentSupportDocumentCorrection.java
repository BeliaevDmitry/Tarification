package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_support_document_correction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_support_document_correction", columnNames = {"document_id", "specialist_id"})
})
public class StudentSupportDocumentCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentSupportDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id", nullable = false)
    private CorrectionSpecialistCatalogEntry specialist;

    @Column(name = "tasks", nullable = false, length = 4000)
    private String tasks;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
