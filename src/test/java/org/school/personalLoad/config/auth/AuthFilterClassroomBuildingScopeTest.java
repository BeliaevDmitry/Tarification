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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterClassroomBuildingScopeTest {

    @Test
    void classesBuildingScopePatchRequiresClassesEditRights() throws Exception {
        AppUserService appUserService = mock(AppUserService.class);
        AuthFilter filter = new AuthFilter(objectMapper(), appUserService);
        SessionUser user = sessionUser(new TabPermissionSnapshot(AppTab.CLASSES, true, false, false, false));
        when(appUserService.findSessionUser(42L)).thenReturn(user);

        MockHttpServletRequest request = requestWithSessionUser(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertFalse(chain.called);
        assertTrue(response.getContentAsString().contains("У пользователя нет прав на редактирование этой вкладки"));
    }

    @Test
    void classesBuildingScopePatchReachesControllerWhenClassesEditRightsExist() throws Exception {
        AppUserService appUserService = mock(AppUserService.class);
        AuthFilter filter = new AuthFilter(objectMapper(), appUserService);
        SessionUser user = sessionUser(new TabPermissionSnapshot(AppTab.CLASSES, true, true, false, false));
        when(appUserService.findSessionUser(42L)).thenReturn(user);

        MockHttpServletRequest request = requestWithSessionUser(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        assertEquals(200, response.getStatus());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private MockHttpServletRequest requestWithSessionUser(SessionUser user) {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/classes/9130/building-scope");
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        return request;
    }

    private SessionUser sessionUser(TabPermissionSnapshot permission) {
        return new SessionUser(
                42L,
                "methodist",
                "Методист",
                null,
                null,
                UserRole.METHODIST,
                true,
                true,
                true,
                null,
                false,
                new java.util.LinkedHashSet<>(),
                List.of(permission)
        );
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response) {
            called = true;
        }
    }
}
