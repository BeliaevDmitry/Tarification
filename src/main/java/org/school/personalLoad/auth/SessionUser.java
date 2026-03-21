package org.school.personalLoad.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionUser implements Serializable {
    public static final String SESSION_KEY = "tarification.currentUser";

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private boolean active;
    private boolean canView;
    private boolean canEdit;

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
