package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAwareNavigationFrontendTest {

    @Test
    void mainMenuAndSidebarUseFunctionalSections() throws Exception {
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(home.contains("data-section-card=\"employees\""));
        assertTrue(home.contains("data-section-card=\"students\""));
        assertTrue(home.contains("data-section-card=\"quality\""));
        assertTrue(home.contains("data-section-card=\"documents\""));
        assertTrue(auth.contains("const NAV_SECTIONS"));
        assertTrue(auth.contains("className = 'app-section-sidebar card'"));
        assertTrue(auth.contains("МЦКО обучающихся"));
        assertTrue(auth.contains("МЦКО педагогов"));
        assertTrue(auth.contains("firstAccessibleNavigationItem"));
    }

    @Test
    void administratorCanAssignSeveralRolesAndClassTeacherProfile() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/admin.html"));
        String script = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(page.contains("id=\"create-roles\""));
        assertTrue(page.contains("name=\"roles\" value=\"CLASS_TEACHER\""));
        assertTrue(page.contains("Права и разделы складываются"));
        assertTrue(script.contains("function selectedRoles(prefix)"));
        assertTrue(script.contains("tab.key === 'EDUCATIONAL_WORK'"));
        assertTrue(script.contains("roles,"));
    }
}
