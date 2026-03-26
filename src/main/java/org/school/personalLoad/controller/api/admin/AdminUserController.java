package org.school.personalLoad.controller.api.admin;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.dto.auth.*;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserService appUserService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(appUserService.findAll().stream()
                .map(user -> AdminUserMapper.fromEntity(user, appUserService.getTabPermissions(user.getId())))
                .toList());
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@RequestBody CreateUserRequest request) {
        AppUser user = appUserService.createUser(request);
        String tempPassword = appUserService.resetPassword(user.getId());
        return ResponseEntity.ok(CreatedUserResponse.builder()
                .user(AdminUserMapper.fromEntity(user, appUserService.getTabPermissions(user.getId())))
                .temporaryPassword(tempPassword)
                .build());
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> update(@PathVariable Long userId, @RequestBody UpdateUserRequest request) {
        AppUser updatedUser = appUserService.updateUser(userId, request);
        return ResponseEntity.ok(AdminUserMapper.fromEntity(updatedUser, appUserService.getTabPermissions(updatedUser.getId())));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long userId) {
        UserResponse targetUser = appUserService.findAll().stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .map(user -> AdminUserMapper.fromEntity(user, appUserService.getTabPermissions(user.getId())))
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        return ResponseEntity.ok(ResetPasswordResponse.builder()
                .userId(userId)
                .username(targetUser.getUsername())
                .temporaryPassword(appUserService.resetPassword(userId))
                .build());
    }
}
