package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Data
@Entity
@Table(name = "probe_order_participant", indexes = {
        @Index(name = "idx_probe_order_participant_order", columnList = "order_id"),
        @Index(name = "idx_probe_order_participant_student", columnList = "student_id")
})
public class ProbeOrderParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProbeOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @Column(name = "full_name_snapshot", nullable = false, length = 500)
    private String fullNameSnapshot;

    @Column(name = "normalized_full_name", nullable = false, length = 500)
    private String normalizedFullName;

    @Column(name = "class_name_snapshot", nullable = false, length = 100)
    private String classNameSnapshot;

    @Column(name = "representative_name", length = 500)
    private String representativeName;

    @Column(name = "representative_phone", length = 100)
    private String representativePhone;
}
