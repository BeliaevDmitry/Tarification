package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "subject_alias", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_alias_scope_source", columnNames = {"scope", "source_name"})
})
@Getter
@Setter
public class SubjectAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String scope;

    @Column(name = "source_name", nullable = false, length = 200)
    private String sourceName;

    @Column(name = "target_name", nullable = false, length = 200)
    private String targetName;

    @Column(nullable = false)
    private boolean active = true;
}
