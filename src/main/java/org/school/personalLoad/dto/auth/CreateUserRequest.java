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
    private Boolean loadEditAllBuildings;
    private List<String> loadEditableBuildingCodes;
    private UserRole role;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canEditAllAcademicYears;
    private List<UserTabPermissionRequest> tabPermissions;
}
