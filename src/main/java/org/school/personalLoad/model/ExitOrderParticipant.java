package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Data
@Entity
@Table(name = "exit_order_participant", indexes = {
        @Index(name = "idx_exit_order_participant_order", columnList = "order_id"),
        @Index(name = "idx_exit_order_participant_student", columnList = "student_id")
})
public class ExitOrderParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ExitOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @Column(name = "full_name_snapshot", nullable = false, length = 500)
    private String fullNameSnapshot;

    @Column(name = "class_name_snapshot", nullable = false, length = 100)
    private String classNameSnapshot;

    @Column(name = "organizational_building_code", nullable = false, length = 255)
    private String organizationalBuildingCode;

    @Column(name = "school_building_id", nullable = false)
    private Long schoolBuildingId;

    @Column(name = "absent", nullable = false)
    private boolean absent;
}
