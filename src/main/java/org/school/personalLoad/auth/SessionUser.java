package org.school.personalLoad.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private String phone;
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

    public boolean canViewSalary() {
        if (isAdmin() || role == UserRole.DIRECTOR || role == UserRole.DEPUTY_DIRECTOR) return true;
        if (!canView) return false;
        return tabPermissions.stream().anyMatch(permission -> permission.getTab() == AppTab.LOAD_SALARY && permission.isCanView());
    }

    public boolean canExportSalary() {
        if (isAdmin() || role == UserRole.DIRECTOR || role == UserRole.DEPUTY_DIRECTOR) return true;
        if (!canView) return false;
        return tabPermissions.stream().anyMatch(permission -> permission.getTab() == AppTab.LOAD_SALARY && permission.isCanExport());
    }

    public boolean canEditLoadBuilding(String buildingCode) {
        if (!canEditTab(AppTab.LOAD)) {
            return false;
        }
        if (isAdmin() || loadEditAllBuildings) {
            return true;
        }
        String requestedAccessCode = normalizeBuildingCode(buildingCode);
        String requestedGroupCode = normalizeBuildingGroupCode(buildingCode);
        if (!loadEditableBuildingCodes.isEmpty()) {
            return loadEditableBuildingCodes.stream().anyMatch(permissionCode -> {
                String normalizedPermission = normalizeBuildingCode(permissionCode);
                if (normalizedPermission.isBlank()) return false;
                if (normalizedPermission.equals(requestedAccessCode)) return true;
                boolean groupWidePermission = !normalizedPermission.contains("|") && !normalizedPermission.contains("::");
                return groupWidePermission && normalizeBuildingGroupCode(normalizedPermission).equals(requestedGroupCode);
            });
        }
        if (role == UserRole.BUILDING_HEAD) {
            return normalizeBuildingGroupCode(managedBuildingCode).equals(requestedGroupCode);
        }
        return false;
    }

    private String normalizeBuildingCode(String value) {
        String normalized = String.valueOf(value == null ? "" : value)
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП")
                .replaceAll("\\s*\\|\\s*", "|")
                .replaceAll("\\s+", "");
        int idx = normalized.indexOf("|");
        if (idx >= 0) {
            return normalizeBuildingGroupAlias(normalized.substring(0, idx)) + normalized.substring(idx);
        }
        return normalizeBuildingGroupAlias(normalized);
    }

    private String normalizeBuildingGroupAlias(String value) {
        return String.valueOf(value == null ? "" : value).replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private String normalizeBuildingGroupCode(String value) {
        String normalized = normalizeBuildingCode(value);
        int siteIdx = normalized.indexOf("::");
        if (siteIdx >= 0) return normalized.substring(0, siteIdx);
        int idx = normalized.indexOf("|");
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }
}
