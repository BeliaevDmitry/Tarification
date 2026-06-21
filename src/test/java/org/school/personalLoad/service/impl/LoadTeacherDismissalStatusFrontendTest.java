package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTeacherDismissalStatusFrontendTest {

    @Test
    void loadShowsPlannedDismissalBelowTeacherName() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(js.contains("plannedDismissalDateOfTeacher"));
        assertTrue(js.contains("Планирует уволиться с"));
        assertTrue(js.contains("${match[3]}.${match[2]}.${match[1]}"));
    }
}
