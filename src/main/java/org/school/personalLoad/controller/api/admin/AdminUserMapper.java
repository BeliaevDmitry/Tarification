package org.school.personalLoad.controller.api.admin;

import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.TabPermissionSnapshot;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.auth.UserResponse;
import org.school.personalLoad.dto.auth.UserTabPermissionResponse;

import java.util.List;

public final class AdminUserMapper {
    private AdminUserMapper() {
    }

    public static UserResponse fromEntity(AppUser user, List<TabPermissionSnapshot> permissions) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .managedBuildingCode(user.getManagedBuildingCode())
                .loadEditAllBuildings(user.isLoadEditAllBuildings())
                .loadEditableBuildingCodes(new java.util.ArrayList<>(user.getLoadEditableBuildingCodes()))
                .role(user.getRole())
                .roleDisplayName(user.getRole().getDisplayName())
                .active(user.isActive())
                .canView(user.isCanView() || user.getRole() == UserRole.ADMIN)
                .canEdit(user.isCanEdit() || user.getRole() == UserRole.ADMIN)
                .admin(user.getRole() == UserRole.ADMIN)
                .tabPermissions(toResponses(permissions))
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
                .phone(user.getPhone())
                .managedBuildingCode(user.getManagedBuildingCode())
                .loadEditAllBuildings(user.isLoadEditAllBuildings())
                .loadEditableBuildingCodes(new java.util.ArrayList<>(user.getLoadEditableBuildingCodes()))
                .role(user.getRole())
                .roleDisplayName(user.getRole().getDisplayName())
                .active(user.isActive())
                .canView(user.isCanView() || user.isAdmin())
                .canEdit(user.isCanEdit() || user.isAdmin())
                .admin(user.isAdmin())
                .tabPermissions(toResponses(user.getTabPermissions()))
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private static List<UserTabPermissionResponse> toResponses(List<TabPermissionSnapshot> permissions) {
        return permissions.stream()
                .map(permission -> UserTabPermissionResponse.builder()
                        .tab(permission.getTab())
                        .tabDisplayName(permission.getTabDisplayName())
                        .canView(permission.isCanView())
                        .canEdit(permission.isCanEdit())
                        .build())
                .toList();
    }
}
