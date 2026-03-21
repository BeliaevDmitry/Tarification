package org.school.personalLoad.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserDtos.UserResponse>> findAll(@RequestParam(required = false) RoleName role,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.findAll(role, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDtos.UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserDtos.UserResponse> create(@RequestBody UserDtos.CreateUserRequest request) {
        return ResponseEntity.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDtos.UserResponse> update(@PathVariable Long id,
                                                        @RequestBody UserDtos.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<UserDtos.PasswordResetResponse> resetPassword(@PathVariable Long id,
                                                                        @RequestBody(required = false) UserDtos.ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(id, request == null ? null : request.getNewPassword()));
    }
}
