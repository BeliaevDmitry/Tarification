package org.school.personalLoad.controller.api.admin;

import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.auth.UserResponse;

public final class AdminUserMapper {
    private AdminUserMapper() {
    }

    public static UserResponse fromEntity(AppUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .roleDisplayName(user.getRole().getDisplayName())
                .active(user.isActive())
                .canView(user.isCanView() || user.getRole() == UserRole.ADMIN)
                .canEdit(user.isCanEdit() || user.getRole() == UserRole.ADMIN)
                .admin(user.getRole() == UserRole.ADMIN)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static UserResponse fromSession(SessionUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .roleDisplayName(user.getRole().getDisplayName())
                .active(user.isActive())
                .canView(user.isCanView() || user.isAdmin())
                .canEdit(user.isCanEdit() || user.isAdmin())
                .admin(user.isAdmin())
                .createdAt(null)
                .updatedAt(null)
                .build();
    }
}
