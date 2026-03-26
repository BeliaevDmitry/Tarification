package org.school.personalLoad.dto.auth;

import lombok.Builder;
import lombok.Value;
import org.school.personalLoad.auth.UserRole;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class UserResponse {
    Long id;
    String username;
    String fullName;
    String email;
    String managedBuildingCode;
    boolean loadEditAllBuildings;
    List<String> loadEditableBuildingCodes;
    UserRole role;
    String roleDisplayName;
    boolean active;
    boolean canView;
    boolean canEdit;
    boolean admin;
    List<UserTabPermissionResponse> tabPermissions;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
