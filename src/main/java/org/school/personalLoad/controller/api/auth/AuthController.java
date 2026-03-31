package org.school.personalLoad.controller.api.auth;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.controller.api.admin.AdminUserMapper;
import org.school.personalLoad.dto.auth.ChangeOwnPasswordRequest;
import org.school.personalLoad.dto.auth.LoginRequest;
import org.school.personalLoad.dto.auth.UserResponse;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserService appUserService;

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        SessionUser user = appUserService.authenticate(request.getUsername(), request.getPassword());
        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(SessionUser.SESSION_KEY, user);
        return ResponseEntity.ok(AdminUserMapper.fromSession(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }
        Object value = session.getAttribute(SessionUser.SESSION_KEY);
        if (!(value instanceof SessionUser user)) {
            return ResponseEntity.status(401).build();
        }
        SessionUser refreshedUser = appUserService.findSessionUser(user.getId());
        session.setAttribute(SessionUser.SESSION_KEY, refreshedUser);
        return ResponseEntity.ok(AdminUserMapper.fromSession(refreshedUser));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangeOwnPasswordRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new IllegalArgumentException("Тело запроса не передано");
        }
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        appUserService.changeOwnPassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
