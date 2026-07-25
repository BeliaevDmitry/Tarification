package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "employment_contract", uniqueConstraints =
        @UniqueConstraint(name = "uk_employment_contract_teacher_number", columnNames = {"teacher_id", "contractNumber"}))
public class EmploymentContract {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeacherDirectoryEntry teacher;
    @Column(nullable = false) private String contractNumber;
    @Column(nullable = false) private LocalDate contractDate;
    @Column(nullable = false) private String positionName;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false) private boolean primaryContract = true;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
