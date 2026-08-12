package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentSupportHoursFrontendTest {

    @Test
    void iupHourInputsOnlyAcceptWholeNumbers() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/contingent.js"));

        assertTrue(javascript.contains("data-iup-field=\"classHours\" type=\"number\" min=\"0\" step=\"1\""));
        assertTrue(javascript.contains("data-iup-field=\"individualHours\" type=\"number\" min=\"0\" step=\"1\""));
        assertTrue(javascript.contains("data-iup-field=\"teacherHours\" type=\"number\" min=\"0\" step=\"1\""));
        assertTrue(javascript.contains("Number.isInteger(hours)"));
        assertFalse(javascript.contains("data-iup-field=\"teacherHours\" type=\"number\" min=\"0\" step=\"0.25\""));
    }
}
