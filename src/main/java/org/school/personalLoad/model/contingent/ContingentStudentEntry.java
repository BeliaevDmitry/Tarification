package org.school.personalLoad.model.contingent;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "contingent_student_entry")
public class ContingentStudentEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ContingentSnapshot snapshot;

    @Column(nullable = false)
    private String fullName;

    private LocalDate birthDate;

    @Column(nullable = false)
    private String classNameRaw;

    @Column(nullable = false)
    private String classNameNormalized;

    @Column(nullable = false)
    private Integer parallel;

    private String buildingCode;

    @Lob
    @Column(nullable = false)
    private String rawDataJson;
}

