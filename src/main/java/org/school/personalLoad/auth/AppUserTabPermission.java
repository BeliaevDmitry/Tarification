package org.school.personalLoad.auth;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "app_user_tab_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_tab_permission", columnNames = {"user_id", "tab_name"})
})
public class AppUserTabPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tab_name", nullable = false, length = 32)
    private AppTab tab;

    @Column(nullable = false)
    private boolean canView;

    @Column(nullable = false)
    private boolean canEdit;
}
