package org.school.personalLoad.oge.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "oge_gia_participant")
@Getter
@Setter
public class OgeGiaParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private OgeGiaVersion version;

    @Column(nullable = false, length = 500)
    private String fullName;

    @Column(length = 50)
    private String snils;

    @Column(length = 255)
    private String document;

    @Column(length = 100)
    private String className;

    @Column
    private Integer examCount;

    @Column(length = 500)
    private String dataIssue;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oge_gia_participant_subject", joinColumns = @JoinColumn(name = "participant_id"))
    @Column(name = "subject_name", nullable = false, length = 200)
    private Set<String> selectedSubjects = new LinkedHashSet<>();
}
