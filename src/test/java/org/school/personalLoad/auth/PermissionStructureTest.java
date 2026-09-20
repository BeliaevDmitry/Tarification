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

        assertTrue(script.contains("label: 'Учебный план и нагрузка · справочники и планирование'"));
        assertTrue(script.contains("key: 'PEOPLE_LOAD'"));
        assertTrue(script.contains("key: 'LOAD_ISSUES'"));
        assertTrue(script.contains("key: 'TEACHERS_ARCHIVE'"));
        assertTrue(script.contains("key: 'TEACHERS_DISMISSALS'"));
        assertTrue(script.contains("key: 'TEACHERS_MCKO'"));
        assertTrue(script.contains("key: 'TEACHERS_TIME_OFF'"));
        assertTrue(script.contains("key: 'CONTINGENT_CLASS_TRANSFERS'"));
        assertTrue(script.contains("key: 'VSOKO_MCKO'"));
        assertTrue(script.contains("label: 'Качество образования'"));
        assertTrue(script.contains("key: 'LOAD_SALARY'"));
        assertTrue(script.contains("key: 'OGE_MISMATCH_VIEW'"));
        assertTrue(script.contains("key: 'EDIT_PAST_ACADEMIC_YEARS'"));
        String authScript = Files.readString(Path.of("src/main/resources/static/auth.js"));
        assertTrue(authScript.contains("isPastAcademicYearSelected"));
        assertTrue(authScript.contains("EDIT_PAST_ACADEMIC_YEARS"));
    }

    @Test
    void sessionCombinesPrimaryAndAdditionalRoles() {
        SessionUser user = user(UserRole.METHODIST, List.of());
        user.setRoles(new LinkedHashSet<>(List.of(UserRole.METHODIST, UserRole.CLASS_TEACHER)));

        assertTrue(user.hasRole(UserRole.METHODIST));
        assertTrue(user.hasRole(UserRole.CLASS_TEACHER));
        assertFalse(user.hasRole(UserRole.ADMIN));
    }

    private SessionUser user(UserRole role, List<TabPermissionSnapshot> permissions) {
        return new SessionUser(1L, "user", "User", null, null, role, true, true, true,
                null, false, new LinkedHashSet<>(), permissions);
    }
}
