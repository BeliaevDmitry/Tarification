package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEmployeeUserPrefillFrontendTest {

    @Test
    void administratorCreatesUserBySelectingPersonnelDirectoryEmployee() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/admin.html"));
        String script = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(page.contains("id=\"create-teacher\""));
        assertTrue(page.contains("name=\"teacherId\""));
        assertTrue(page.contains("id=\"create-username\""));
        assertTrue(page.contains("id=\"create-email\""));
        assertTrue(page.contains("id=\"create-phone\""));
        assertTrue(page.contains("value=\"EMPLOYEE\""));
        assertTrue(script.contains("function applySelectedTeacher()"));
        assertTrue(script.contains("ui.createUsername.value = email"));
        assertTrue(script.contains("ui.createEmail.value = email"));
        assertTrue(script.contains("ui.createPhone.value"));
        assertTrue(script.contains("teacherId: Number(form.get('teacherId')) || null"));
        assertTrue(script.contains("!linkedIds.has(Number(teacher.id))"));
    }
}
