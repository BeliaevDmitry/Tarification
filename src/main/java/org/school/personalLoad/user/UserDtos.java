package org.school.personalLoad.user;

import lombok.Data;

public final class UserDtos {
    private UserDtos() {
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String fullName;
        private RoleName role;
        private Boolean enabled = true;
        private Long buildingId;
    }

    @Data
    public static class UpdateUserRequest {
        private String username;
        private String email;
        private String fullName;
        private RoleName role;
        private Boolean enabled;
        private Long buildingId;
    }

    @Data
    public static class ResetPasswordRequest {
        private String newPassword;
    }

    @Data
    public static class UserResponse {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private RoleName role;
        private boolean enabled;
        private Long buildingId;
        private String buildingCode;
        private String buildingName;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    public static class PasswordResetResponse {
        private Long userId;
        private String username;
        private String temporaryPassword;
    }

    @Data
    public static class ProfileUpdateRequest {
        private String email;
        private String fullName;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;
    }
}
