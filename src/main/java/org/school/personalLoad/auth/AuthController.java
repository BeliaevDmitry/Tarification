package org.school.personalLoad.auth;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthUserResponse> login(@RequestBody AuthDtos.LoginRequest request,
                                                           HttpServletRequest httpServletRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        AppUser appUser = appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        auditService.log(ActionType.LOGIN, null, null, null, Map.of("username", appUser.getUsername()), "Successful login");
        return ResponseEntity.ok(toResponse(appUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                      HttpServletResponse response,
                                                      Authentication authentication) {
        if (authentication != null) {
            auditService.log(ActionType.LOGOUT, null, null, null, Map.of("username", authentication.getName()), "User logout");
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return ResponseEntity.ok(Map.of("status", "logged-out"));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.AuthUserResponse> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        AppUser appUser = appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return ResponseEntity.ok(toResponse(appUser));
    }

    private AuthDtos.AuthUserResponse toResponse(AppUser appUser) {
        AuthDtos.AuthUserResponse response = new AuthDtos.AuthUserResponse();
        response.setId(appUser.getId());
        response.setUsername(appUser.getUsername());
        response.setEmail(appUser.getEmail());
        response.setFullName(appUser.getFullName());
        response.setRole(appUser.getRole());
        response.setEnabled(appUser.isEnabled());
        return response;
    }
}
