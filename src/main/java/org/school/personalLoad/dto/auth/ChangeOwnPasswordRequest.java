package org.school.personalLoad.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeOwnPasswordRequest {
    private String currentPassword;
    private String newPassword;
}
