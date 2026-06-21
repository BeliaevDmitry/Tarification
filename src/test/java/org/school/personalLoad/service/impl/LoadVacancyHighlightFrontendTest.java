package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadVacancyHighlightFrontendTest {

    @Test
    void vacancyTeacherNamesHighlightTeacherAndAssignedHours() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));
        String css = Files.readString(Path.of("src/main/resources/static/styles.css"));

        assertTrue(js.contains("includes(\"вакансия\")"));
        assertTrue(js.contains("vacancyTeacher ? \"vacancy-row\""));
        assertTrue(js.contains("vacancyTeacher ? \"vacancy\""));
        assertTrue(css.contains(".hour-pill.vacancy"));
    }
}
