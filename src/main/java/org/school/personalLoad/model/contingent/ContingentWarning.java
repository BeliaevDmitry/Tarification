package org.school.personalLoad.model.contingent;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "contingent_warning")
public class ContingentWarning {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ContingentSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ContingentWarningType type;

    @Column(nullable = false, length = 500)
    private String message;

    private String className;
}

