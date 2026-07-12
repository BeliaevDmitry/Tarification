package org.school.personalLoad.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
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
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login.html",
            "/login.js",
            "/school-crest.png",
            "/branding.js",
            "/api/public/branding",
            "/request-exit.html",
            "/open-forms.html",
            "/pa.html",
            "/pa-folders.js",
            "/public-chat.html",
            "/public-chat.js",
            "/api/public/chat/messages",
            "/styles.css",
            "/table-scroll.js",
            "/favicon.ico",
            "/error"
    );

    private static final Map<String, AppTab> PAGE_TABS = Map.ofEntries(
            Map.entry("/buildings.html", AppTab.BUILDINGS),
            Map.entry("/classes.html", AppTab.CLASSES),
            Map.entry("/subjects.html", AppTab.SUBJECTS),
            Map.entry("/curriculum.html", AppTab.CURRICULUM),
            Map.entry("/load.html", AppTab.LOAD),
            Map.entry("/people-load.html", AppTab.PEOPLE_LOAD),
            Map.entry("/load-issues.html", AppTab.LOAD_ISSUES),
            Map.entry("/load-statistics.html", AppTab.LOAD_STATS),
            Map.entry("/service-notes.html", AppTab.SERVICE_NOTES),
            Map.entry("/settings.html", AppTab.SETTINGS),
            Map.entry("/subject-areas.html", AppTab.SUBJECT_AREAS),
            Map.entry("/teachers-notification.html", AppTab.HR_NOTIFICATIONS_VIEW),
            Map.entry("/educational-work.html", AppTab.EDUCATIONAL_WORK),
            Map.entry("/vsoko.html", AppTab.VSOKO_VIEW),
            Map.entry("/vsoko-oge.html", AppTab.VSOKO_VIEW),
            Map.entry("/vsoko-ege.html", AppTab.VSOKO_VIEW),
            Map.entry("/vsoko-pa.html", AppTab.VSOKO_VIEW),
            Map.entry("/admin.html", AppTab.USERS)
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
                || path.startsWith("/school-crests/")
                || PUBLIC_PATHS.contains(path)
                || isPublicPaApiPath(request)
                || "/api/auth/login".equals(path);
    }

    private boolean isPublicPaApiPath(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if ("/api/pa/reports/folders".equals(path)) {
            return true;
        }
        return path.matches("^/api/pa/reports/\\d+/download$");
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

        if ("/teachers.html".equals(path) && !hasAnyHrPageAccess(currentUser)) {
            rejectForbidden(request, response, "У пользователя нет прав на просмотр раздела кадров");
            return;
        }

        AppTab pageTab = PAGE_TABS.get(path);
        if (pageTab != null && !currentUser.canViewTab(pageTab)) {
            rejectForbidden(request, response, "У пользователя нет прав на просмотр этой вкладки");
            return;
        }

        AppTab apiTab = apiTabForPath(path);
        if (isWriteApiRequest(request, path) && apiTab != null && !currentUser.canEditTab(apiTab)) {
            rejectForbidden(request, response, "У пользователя нет прав на редактирование этой вкладки");
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

    private boolean hasAnyHrPageAccess(SessionUser user) {
        return user.canViewTab(AppTab.TEACHERS)
                || user.canViewTab(AppTab.TEACHERS_ARCHIVE)
                || user.canViewTab(AppTab.TEACHERS_DISMISSALS)
                || user.canViewTab(AppTab.TEACHERS_SETTINGS)
                || user.canViewTab(AppTab.TEACHERS_MCKO);
    }

    private AppTab apiTabForPath(String path) {
        if (path.startsWith("/api/building-groups")) return AppTab.BUILDINGS;
        if (path.startsWith("/api/buildings")) return AppTab.BUILDINGS;
        if (path.startsWith("/api/classroom-leadership")) return AppTab.CLASSES;
        if (path.startsWith("/api/classes")) return AppTab.CLASSES;
        if (path.startsWith("/api/subjects")) return AppTab.SUBJECTS;
        if (path.startsWith("/api/curriculum")) return AppTab.CURRICULUM;
        if (path.startsWith("/api/manual-load/issues")) return AppTab.LOAD_ISSUES;
        if (path.startsWith("/api/manual-load/stats") || path.startsWith("/api/manual-load/health")) return AppTab.LOAD_STATS;
        if (path.startsWith("/api/manual-load/export-full") || path.startsWith("/api/manual-load/export-consolidated")) return AppTab.PEOPLE_LOAD;
        if (path.startsWith("/api/manual-load")) return AppTab.LOAD;
        if (path.startsWith("/api/primary-subjects")) return AppTab.PEOPLE_LOAD;
        if (path.startsWith("/api/service-memos")) return AppTab.SERVICE_NOTES;
        if (path.startsWith("/api/settings/")) return AppTab.SETTINGS;
        if (path.startsWith("/api/teachers-notification")) return AppTab.HR_NOTIFICATIONS_VIEW;
        if (path.startsWith("/api/mcko")) return AppTab.TEACHERS_MCKO;
        if (path.startsWith("/api/salary-group-coefficient-subjects")) return AppTab.TEACHERS_SETTINGS;
        if (path.matches("^/api/teachers/\\d+/(plan-dismiss|cancel-plan-dismiss|dismiss|restore)$")) return AppTab.TEACHERS_DISMISSALS;
        if (path.matches("^/api/teachers/\\d+/(archive|unarchive)$")) return AppTab.TEACHERS_ARCHIVE;
        if (path.startsWith("/api/teachers")) return AppTab.TEACHERS;
        if (path.startsWith("/api/admin/users")) return AppTab.USERS;
        if (path.startsWith("/api/educational-work")) return AppTab.EDUCATIONAL_WORK;
        if (path.startsWith("/api/pa")) return AppTab.VSOKO_EDIT;
        return null;
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
