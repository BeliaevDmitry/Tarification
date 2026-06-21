package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherDismissalFrontendTest {

    @Test
    void dismissalActionsUseDismissalPermissionAndExposeCancellation() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/teachers.js"));
        String filter = Files.readString(Path.of("src/main/java/org/school/personalLoad/config/auth/AuthFilter.java"));

        assertTrue(js.contains("if (!canEditTeacherPermission(\"TEACHERS_DISMISSALS\"))"));
        assertTrue(js.contains("cancel-plan-dismiss"));
        assertTrue(js.contains(">Передумал</button>"));
        assertTrue(filter.contains("plan-dismiss|cancel-plan-dismiss|dismiss|restore"));
    }
}
