package org.school.personalLoad.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final UserService userService;

    @PutMapping
    public ResponseEntity<UserDtos.UserResponse> updateProfile(@RequestBody UserDtos.ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(@RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(java.util.Map.of("status", "password-changed"));
    }
}
