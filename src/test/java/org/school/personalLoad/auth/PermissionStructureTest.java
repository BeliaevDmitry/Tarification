package org.school.personalLoad.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionStructureTest {

    @Test
    void directorDoesNotReceiveSalaryAccessWithoutExplicitPermission() {
        SessionUser director = user(UserRole.DIRECTOR, List.of());

        assertFalse(director.canViewSalary());
        assertFalse(director.canExportSalary());
    }

    @Test
    void salaryAccessRequiresItsExplicitSensitivePermission() {
        SessionUser director = user(UserRole.DIRECTOR, List.of(
                new TabPermissionSnapshot(AppTab.LOAD_SALARY, true, false, false, true)
        ));

        assertTrue(director.canViewSalary());
        assertTrue(director.canExportSalary());
    }

    @Test
    void adminMatrixContainsRequestedGroupsAndSeparatePageKeys() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(script.contains("label: 'Нагрузка'"));
        assertTrue(script.contains("key: 'PEOPLE_LOAD'"));
        assertTrue(script.contains("key: 'LOAD_ISSUES'"));
        assertTrue(script.contains("key: 'TEACHERS_ARCHIVE'"));
        assertTrue(script.contains("key: 'TEACHERS_DISMISSALS'"));
        assertTrue(script.contains("key: 'TEACHERS_MCKO'"));
        assertTrue(script.contains("label: 'Чувствительные данные'"));
        assertTrue(script.contains("key: 'LOAD_SALARY'"));
        assertTrue(script.contains("key: 'OGE_MISMATCH_VIEW'"));
    }

    private SessionUser user(UserRole role, List<TabPermissionSnapshot> permissions) {
        return new SessionUser(1L, "user", "User", null, null, role, true, true, true,
                null, false, new LinkedHashSet<>(), permissions);
    }
}
