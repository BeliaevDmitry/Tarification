package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherTimeOffFrontendTest {

    @Test
    void personnelPageContainsTimeOffSummaryAccrualAndUsageWorkflows() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        String js = Files.readString(Path.of("src/main/resources/static/teacher-time-off.js"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(html.contains("data-tab=\"TEACHERS_TIME_OFF\""));
        assertTrue(html.contains("href=\"/teachers.html#time-off\""));
        assertTrue(html.contains("href=\"/teachers.html#time-off-add\""));
        assertTrue(html.contains("id=\"teacher-time-off-summary-body\""));
        assertTrue(html.contains("id=\"teacher-time-off-history-body\""));
        assertTrue(html.contains("id=\"teacher-time-off-use-button\""));
        assertTrue(html.contains("type=\"search\" list=\"teacher-time-off-teachers\""));
        assertTrue(html.contains("name=\"teacher-time-off-lesson-removal\""));
        assertTrue(html.contains("<option value=\"55\">55</option>"));
        assertTrue(html.contains("src=\"/teacher-time-off.js\""));

        assertTrue(js.contains("durationMinutes = hours * 60 + minutes"));
        assertTrue(js.contains("/api/teacher-time-off/accruals"));
        assertTrue(js.contains("/api/teacher-time-off/usages"));
        assertTrue(js.contains("safe / 480"));
        assertTrue(js.contains("createdByFio"));
        assertTrue(auth.contains("TEACHERS_TIME_OFF"));
    }
}
