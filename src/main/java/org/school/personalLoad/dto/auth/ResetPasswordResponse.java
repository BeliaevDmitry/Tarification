package org.school.personalLoad.dto.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResetPasswordResponse {
    Long userId;
    String username;
    String temporaryPassword;
}
