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
        return ResponseEntity.ok(appUserService.findAll().stream().map(AdminUserMapper::fromEntity).toList());
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@RequestBody CreateUserRequest request) {
        AppUser user = appUserService.createUser(request);
        String tempPassword = appUserService.resetPassword(user.getId());
        return ResponseEntity.ok(CreatedUserResponse.builder()
                .user(AdminUserMapper.fromEntity(user))
                .temporaryPassword(tempPassword)
                .build());
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> update(@PathVariable Long userId, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(AdminUserMapper.fromEntity(appUserService.updateUser(userId, request)));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long userId) {
        List<UserResponse> allUsers = appUserService.findAll().stream().map(AdminUserMapper::fromEntity).toList();
        UserResponse targetUser = allUsers.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        return ResponseEntity.ok(ResetPasswordResponse.builder()
                .userId(userId)
                .username(targetUser.getUsername())
                .temporaryPassword(appUserService.resetPassword(userId))
                .build());
    }
}
