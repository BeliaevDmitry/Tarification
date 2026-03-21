package org.school.personalLoad.dto.auth;

import lombok.Data;
import org.school.personalLoad.auth.UserRole;

import java.util.List;

@Data
public class CreateUserRequest {
    private String username;
    private String fullName;
    private String email;
    private String managedBuildingCode;
    private UserRole role;
    private Boolean canView;
    private Boolean canEdit;
    private List<UserTabPermissionRequest> tabPermissions;
}
