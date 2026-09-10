package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admission_role_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admission_role_user", columnNames = {"user_id", "access_role"})
})
public class AdmissionRoleAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 40)
    private AdmissionAccessRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;
}
