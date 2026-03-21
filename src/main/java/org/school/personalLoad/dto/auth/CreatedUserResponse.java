package org.school.personalLoad.dto.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreatedUserResponse {
    UserResponse user;
    String temporaryPassword;
}
