package org.school.personalLoad.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionUser implements Serializable {
    public static final String SESSION_KEY = "tarification.currentUser";

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private boolean active;
    private boolean canView;
    private boolean canEdit;
    private String managedBuildingCode;
    private boolean loadEditAllBuildings;
    private Set<String> loadEditableBuildingCodes = new LinkedHashSet<>();
    private List<TabPermissionSnapshot> tabPermissions = new ArrayList<>();

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean canViewTab(AppTab tab) {
        if (isAdmin()) return true;
        if (tab == null || !canView) return false;
        return tabPermissions.stream().anyMatch(permission -> permission.getTab() == tab && permission.isCanView());
    }

    public boolean canEditTab(AppTab tab) {
        if (isAdmin()) return true;
        if (tab == null || !canView || !canEdit) return false;
        return tabPermissions.stream().anyMatch(permission -> permission.getTab() == tab && permission.isCanEdit());
    }

    public boolean canEditLoadBuilding(String buildingCode) {
        if (!canEditTab(AppTab.LOAD)) {
            return false;
        }
        if (isAdmin() || loadEditAllBuildings) {
            return true;
        }
        String normalizedRequested = normalizeBuildingGroupCode(buildingCode);
        if (!loadEditableBuildingCodes.isEmpty()) {
            return loadEditableBuildingCodes.stream().map(this::normalizeBuildingGroupCode).anyMatch(normalizedRequested::equals);
        }
        if (role == UserRole.BUILDING_HEAD) {
            return normalizeBuildingGroupCode(managedBuildingCode).equals(normalizedRequested);
        }
        return false;
    }

    private String normalizeBuildingCode(String value) {
        return String.valueOf(value == null ? "" : value).trim().toUpperCase().replace(" ", "");
    }

    private String normalizeBuildingGroupCode(String value) {
        String normalized = normalizeBuildingCode(value);
        int idx = normalized.indexOf("|");
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }
}
