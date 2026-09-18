package org.school.personalLoad.pa.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pa_work_material_file", indexes = {
        @Index(name = "idx_pa_material_file_material", columnList = "material_id, file_kind")
})
@Getter
@Setter
public class PaWorkMaterialFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private PaWorkMaterial material;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, length = 20)
    private PaWorkMaterialFileKind kind;

    @Column(name = "original_file_name", nullable = false, length = 1000)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 1200)
    private String storedFileName;

    @Column(name = "uploaded_by_username", nullable = false, length = 255)
    private String uploadedByUsername;

    @Column(name = "uploaded_by_fio", nullable = false, length = 500)
    private String uploadedByFio;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
