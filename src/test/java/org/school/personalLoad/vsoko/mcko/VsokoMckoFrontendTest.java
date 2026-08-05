package org.school.personalLoad.vsoko.mcko;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VsokoMckoFrontendTest {
    @Test
    void vsokoHubAndPagesExposeRequestedWorkflows() throws Exception {
        String hub = Files.readString(Path.of("src/main/resources/static/vsoko.html"));
        String mcko = Files.readString(Path.of("src/main/resources/static/vsoko-mcko.html"));
        String summary = Files.readString(Path.of("src/main/resources/static/vsoko-summary.html"));
        String interview = Files.readString(Path.of("src/main/resources/static/vsoko-interview.html"));
        String assignments = Files.readString(Path.of("src/main/resources/static/vsoko-mcko-teachers.html"));

        assertTrue(hub.contains("/vsoko-mcko.html"));
        assertTrue(hub.contains("/vsoko-summary.html"));
        assertTrue(hub.contains("/vsoko-interview.html"));
        assertTrue(mcko.contains("multiple"));
        assertTrue(mcko.contains("Скачать Excel как раньше"));
        assertTrue(summary.contains("По ребёнку"));
        assertTrue(summary.contains("По классу"));
        assertTrue(interview.contains("Список на печать"));
        assertTrue(assignments.contains("Импорт Excel"));
        assertTrue(assignments.contains("Экспорт Excel"));
    }
}
