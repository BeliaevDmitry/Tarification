package org.school.personalLoad.auth;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_username", columnNames = "username")
})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String fullName;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(length = 255)
    private String documentPosition;

    @Column(length = 255)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(length = 80)
    private String managedBuildingCode;

    @Column(nullable = false)
    private boolean loadEditAllBuildings = false;

    @ElementCollection
    @CollectionTable(name = "app_user_load_building", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "building_code", nullable = false, length = 255)
    private java.util.Set<String> loadEditableBuildingCodes = new java.util.LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    /**
     * Дополнительные роли аккаунта. Поле {@code role} оставлено как основная роль
     * для совместимости со старыми данными, отчётами и интеграциями.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 32)
    private Set<UserRole> roles = new LinkedHashSet<>();

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean canView = true;

    @Column(nullable = false)
    private boolean canEdit = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Set<UserRole> getEffectiveRoles() {
        LinkedHashSet<UserRole> effective = new LinkedHashSet<>();
        if (role != null) {
            effective.add(role);
        }
        if (roles != null) {
            effective.addAll(roles);
        }
        return effective;
    }

    public boolean hasRole(UserRole expectedRole) {
        return expectedRole != null && getEffectiveRoles().contains(expectedRole);
    }
}
