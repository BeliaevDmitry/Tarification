package org.school.personalLoad.service.auth;

import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.TabPermissionSnapshot;
import org.school.personalLoad.dto.auth.CreateUserRequest;
import org.school.personalLoad.dto.auth.UpdateUserRequest;

import java.util.List;

public interface AppUserService {
    SessionUser authenticate(String username, String password);
    SessionUser findSessionUser(Long userId);
    List<AppUser> findAll();
    List<TabPermissionSnapshot> getTabPermissions(Long userId);
    AppUser createUser(CreateUserRequest request);
    AppUser updateUser(Long userId, UpdateUserRequest request);
    String resetPassword(Long userId);
    void ensureDefaultAdmin();
}
