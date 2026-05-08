package org.school.personalLoad.config.auth;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.model.UserActionLog;
import org.school.personalLoad.service.UserActionLogService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserActionAuditFilter extends OncePerRequestFilter {
    private final UserActionLogService userActionLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/")) return;

        String actionType = resolveAction(method, path);
        if (actionType == null) return;

        HttpSession session = request.getSession(false);
        SessionUser user = session != null ? (SessionUser) session.getAttribute(SessionUser.SESSION_KEY) : null;
        String loginName = request.getParameter("username");

        UserActionLog log = new UserActionLog();
        if (user != null) {
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
            log.setFullName(user.getFullName());
            log.setRole(user.getRole().name());
        } else if (path.equals("/api/auth/login") && loginName != null) {
            log.setUsername(loginName);
        }
        log.setActionType(actionType);
        log.setEntityType(extractEntity(path));
        log.setDetails(method + " " + path);
        log.setIp(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setStatusCode(response.getStatus());
        log.setSuccess(response.getStatus() < 400);
        log.setCreatedAt(Instant.now());
        userActionLogService.save(log);
    }

    private String resolveAction(String method, String path) {
        if ("/api/auth/login".equals(path)) return "LOGIN";
        if ("/api/auth/logout".equals(path)) return "LOGOUT";
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> null;
        };
    }

    private String extractEntity(String path) {
        String normalized = path.replaceFirst("^/api/", "");
        int slashIdx = normalized.indexOf('/');
        return (slashIdx > 0 ? normalized.substring(0, slashIdx) : normalized).toUpperCase(Locale.ROOT);
    }
}
