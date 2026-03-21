package org.school.personalLoad.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    Page<UserDtos.UserResponse> findAll(RoleName role, Pageable pageable);

    UserDtos.UserResponse findById(Long id);

    UserDtos.UserResponse create(UserDtos.CreateUserRequest request);

    UserDtos.UserResponse update(Long id, UserDtos.UpdateUserRequest request);

    void delete(Long id);

    UserDtos.PasswordResetResponse resetPassword(Long id, String newPassword);

    UserDtos.UserResponse updateProfile(UserDtos.ProfileUpdateRequest request);

    void changePassword(UserDtos.ChangePasswordRequest request);
}
