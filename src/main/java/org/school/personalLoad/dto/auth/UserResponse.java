package org.school.personalLoad.dto.auth;

import lombok.Builder;
import lombok.Value;
import org.school.personalLoad.auth.UserRole;

import java.time.LocalDateTime;

@Value
@Builder
public class UserResponse {
    Long id;
    String username;
    String fullName;
    String email;
    UserRole role;
    String roleDisplayName;
    boolean active;
    boolean canView;
    boolean canEdit;
    boolean admin;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
