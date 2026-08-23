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

class AuthFilterCurriculumExportPermissionTest {

    @Test
    void exportWorksWithoutEditWhenExportPermissionIsEnabled() throws Exception {
        SessionUser user = user(false, true);
        Result result = filter("/api/curriculum/export-addresses", user);

        assertTrue(result.chain.called);
        assertEquals(200, result.response.getStatus());
    }

    @Test
    void editPermissionDoesNotReplaceMissingExportPermission() throws Exception {
        SessionUser user = user(true, false);
        Result result = filter("/api/curriculum/export", user);

        assertFalse(result.chain.called);
        assertEquals(403, result.response.getStatus());
        assertTrue(result.response.getContentAsString().contains("нет права на экспорт учебного плана"));
    }

    private Result filter(String path, SessionUser user) throws Exception {
        AppUserService appUserService = mock(AppUserService.class);
        when(appUserService.findSessionUser(42L)).thenReturn(user);
        AuthFilter filter = new AuthFilter(
                new ObjectMapper().registerModule(new JavaTimeModule()), appUserService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain);
    }

    private SessionUser user(boolean canEdit, boolean canExport) {
        TabPermissionSnapshot permission = new TabPermissionSnapshot(
                AppTab.CURRICULUM, true, canEdit, false, canExport);
        return new SessionUser(42L, "methodist", "Методист", null, null, UserRole.METHODIST,
                true, true, canEdit, null, false, new LinkedHashSet<>(), List.of(permission));
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
