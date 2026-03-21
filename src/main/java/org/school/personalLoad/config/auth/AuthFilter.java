package org.school.personalLoad.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ApiErrorResponse;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login.html",
            "/login.js",
            "/styles.css",
            "/table-scroll.js",
            "/favicon.ico",
            "/error"
    );

    private final ObjectMapper objectMapper;
    private final AppUserService appUserService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/webjars/")
                || path.startsWith("/assets/")
                || path.startsWith("/images/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || PUBLIC_PATHS.contains(path)
                || "/api/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        SessionUser currentUser = currentUser(request.getSession(false));

        if (currentUser == null) {
            rejectUnauthenticated(request, response);
            return;
        }
        try {
            currentUser = appUserService.findSessionUser(currentUser.getId());
            request.getSession(false).setAttribute(SessionUser.SESSION_KEY, currentUser);
        } catch (RuntimeException ex) {
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            rejectUnauthenticated(request, response);
            return;
        }
        if (isAdminPath(path) && !currentUser.isAdmin()) {
            rejectForbidden(request, response, "Только администратор может работать с пользователями");
            return;
        }
        if (isWriteApiRequest(request, path) && !currentUser.isAdmin() && !currentUser.isCanEdit()) {
            rejectForbidden(request, response, "У пользователя нет прав на редактирование");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private SessionUser currentUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SessionUser.SESSION_KEY);
        return value instanceof SessionUser ? (SessionUser) value : null;
    }

    private boolean isAdminPath(String path) {
        return "/admin.html".equals(path) || path.startsWith("/api/admin/");
    }

    private boolean isWriteApiRequest(HttpServletRequest request, String path) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return false;
        }
        return !(HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod()));
    }

    private void rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            writeApiError(response, HttpStatus.UNAUTHORIZED, "Требуется вход в систему", request.getRequestURI());
            return;
        }
        response.sendRedirect("/login.html");
    }

    private void rejectForbidden(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            writeApiError(response, HttpStatus.FORBIDDEN, message, request.getRequestURI());
            return;
        }
        response.sendRedirect("/");
    }

    private void writeApiError(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse("error", message, path, LocalDateTime.now()));
    }
}
