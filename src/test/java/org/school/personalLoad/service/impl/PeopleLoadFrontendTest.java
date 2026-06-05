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
        assertTrue(html.contains("Нагрузка укрупнённо"));
        assertTrue(js.contains("exportConsolidatedLoadBtn"));
        assertTrue(js.contains("/api/manual-load/export-consolidated"));
        assertFalse(js.contains("exportConsolidatedLoadBtn: document.getElementById(\"export-full-load-btn\")"));
    }
}
