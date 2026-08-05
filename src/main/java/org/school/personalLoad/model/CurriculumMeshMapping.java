package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "curriculum_mesh_mapping", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_curriculum_mesh_mapping_scope",
                columnNames = {"academic_year", "curriculum_entry_id", "group_name_up"}
        )
}, indexes = {
        @Index(name = "idx_curriculum_mesh_mapping_year", columnList = "academic_year"),
        @Index(name = "idx_curriculum_mesh_mapping_entry", columnList = "curriculum_entry_id")
})
public class CurriculumMeshMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "curriculum_entry_id", nullable = false)
    private Long curriculumEntryId;

    @Column(name = "subject_name_up", nullable = false)
    private String subjectNameUp;

    @Column(name = "class_name_up", nullable = false)
    private String classNameUp;

    @Column(name = "group_name_up", nullable = false)
    private String groupNameUp = "";

    @Column(name = "subject_name_mesh", nullable = false)
    private String subjectNameMesh;

    @Column(name = "class_name_mesh", nullable = false)
    private String classNameMesh;

    @Column(name = "group_name_mesh", nullable = false)
    private String groupNameMesh = "";

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
