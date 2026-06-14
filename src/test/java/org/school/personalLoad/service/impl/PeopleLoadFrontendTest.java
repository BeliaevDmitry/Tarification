package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeopleLoadFrontendTest {

    @Test
    void peopleLoadPageExposesConsolidatedExportButtonAndEndpoint() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/people-load.html"));
        String js = Files.readString(Path.of("src/main/resources/static/people-load.js"));

        assertTrue(html.contains("id=\"export-consolidated-load-btn\""));
        assertTrue(html.contains("По основному предмету"));
        assertTrue(html.contains("Педагог/предмет"));
        assertTrue(html.contains("Определить основной предмет"));
        assertTrue(html.contains("Основные предметы"));
        assertTrue(js.contains("exportConsolidatedLoadBtn"));
        assertTrue(js.contains("/api/manual-load/export-consolidated"));
        assertTrue(js.contains("/api/primary-subjects/determine"));
        assertFalse(js.contains("exportConsolidatedLoadBtn: document.getElementById(\"export-full-load-btn\")"));
    }
}
