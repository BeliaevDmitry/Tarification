package org.school.personalLoad.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.TabPermissionSnapshot;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterClassTeacherExitOrdersTest {

    @Test
    void classTeacherPermissionsOpenWorkspaceAndAllowCreatingApplications() throws Exception {
        SessionUser user = classTeacher(true, true);

        assertTrue(filter("GET", "/class-teacher.html", user).chain.called);
        assertTrue(filter("GET", "/api/exit-orders/references", user).chain.called);
        assertTrue(filter("GET", "/api/exit-orders", user).chain.called);
        assertTrue(filter("POST", "/api/exit-orders", user).chain.called);
    }

    @Test
    void classTeacherCannotUseDocumentWorkflowEndpoints() throws Exception {
        SessionUser user = classTeacher(true, true);

        Result result = filter("POST", "/api/exit-orders/15/acknowledge", user);

        assertFalse(result.chain.called);
        assertEquals(403, result.response.getStatus());
    }

    private Result filter(String method, String path, SessionUser user) throws Exception {
        AppUserService service = mock(AppUserService.class);
        when(service.findSessionUser(42L)).thenReturn(user);
        AuthFilter filter = new AuthFilter(new ObjectMapper().registerModule(new JavaTimeModule()), service);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain);
    }

    private SessionUser classTeacher(boolean create, boolean summary) {
        List<TabPermissionSnapshot> permissions = List.of(
                new TabPermissionSnapshot(AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE, create, create, false, false),
                new TabPermissionSnapshot(AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY, summary, false, false, false));
        return new SessionUser(42L, "teacher", "Классный руководитель", null, null,
                UserRole.CLASS_TEACHER, true, true, true, null, false, new LinkedHashSet<>(), permissions);
    }

    private record Result(MockHttpServletResponse response, RecordingFilterChain chain) {
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response) {
            called = true;
        }
    }
}
