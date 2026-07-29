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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterMckoPaAccessTest {

    @Test
    void mckoReadRequiresMckoViewPermission() throws Exception {
        assertReadAccess("/api/mcko/certificates", List.of(), false);
        assertReadAccess("/api/mcko/certificates",
                List.of(new TabPermissionSnapshot(AppTab.TEACHERS_MCKO, true, false, false, false)), true);
    }

    @Test
    void paReadRequiresVsokoViewOrEditPermission() throws Exception {
        assertReadAccess("/api/pa/specifications", List.of(), false);
        assertReadAccess("/api/pa/specifications",
                List.of(new TabPermissionSnapshot(AppTab.VSOKO_VIEW, true, false, false, false)), true);
        assertReadAccess("/api/pa/specifications",
                List.of(new TabPermissionSnapshot(AppTab.VSOKO_EDIT, true, true, false, false)), true);
    }

    @Test
    void everyProtectedPaPageRequiresVsokoViewPermission() throws Exception {
        for (String path : List.of(
                "/vsoko-pa-spec.html",
                "/vsoko-pa-entry.html",
                "/vsoko-pa-exit.html",
                "/vsoko-pa-folders.html",
                "/vsoko-pa-analysis.html",
                "/vsoko-pa-teachers.html",
                "/vsoko-pa-upload.html")) {
            assertPageAccess(path, List.of(), false);
            assertPageAccess(path,
                    List.of(new TabPermissionSnapshot(AppTab.VSOKO_VIEW, true, false, false, false)), true);
        }
    }

    private void assertReadAccess(String path, List<TabPermissionSnapshot> permissions, boolean expected) throws Exception {
        assertAccess(new MockHttpServletRequest("GET", path), permissions, expected);
    }

    private void assertPageAccess(String path, List<TabPermissionSnapshot> permissions, boolean expected) throws Exception {
        assertAccess(new MockHttpServletRequest("GET", path), permissions, expected);
    }

    private void assertAccess(MockHttpServletRequest request,
                              List<TabPermissionSnapshot> permissions,
                              boolean expected) throws Exception {
        AppUserService appUserService = mock(AppUserService.class);
        AuthFilter filter = new AuthFilter(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                appUserService);
        SessionUser user = new SessionUser(
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
                new LinkedHashSet<>(),
                permissions);
        when(appUserService.findSessionUser(42L)).thenReturn(user);
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(expected, chain.called);
        if (expected) {
            assertEquals(200, response.getStatus());
        } else if (request.getRequestURI().startsWith("/api/")) {
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("нет прав на просмотр"));
        } else {
            assertEquals(302, response.getStatus());
            assertEquals("/", response.getRedirectedUrl());
        }
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response) {
            called = true;
        }
    }
}
