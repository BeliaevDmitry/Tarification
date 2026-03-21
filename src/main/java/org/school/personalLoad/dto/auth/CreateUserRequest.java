package org.school.personalLoad.dto.auth;

import lombok.Data;
import org.school.personalLoad.auth.UserRole;

@Data
public class CreateUserRequest {
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private Boolean canView;
    private Boolean canEdit;
}
