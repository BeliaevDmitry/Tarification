package org.school.personalLoad.auth;

import lombok.Data;
import org.school.personalLoad.user.RoleName;

public final class AuthDtos {
    private AuthDtos() {
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class AuthUserResponse {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private RoleName role;
        private boolean enabled;
    }
}
