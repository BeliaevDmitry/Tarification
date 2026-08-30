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
        String mckoScript = Files.readString(Path.of("src/main/resources/static/vsoko-mcko.js"));
        String summaryScript = Files.readString(Path.of("src/main/resources/static/vsoko-summary.js"));

        assertTrue(hub.contains("/vsoko-mcko.html"));
        assertTrue(hub.contains("/vsoko-summary.html"));
        assertTrue(hub.contains("/vsoko-interview.html"));
        assertTrue(mcko.contains("multiple"));
        assertTrue(mcko.contains("Скачать Excel как раньше"));
        assertTrue(mcko.contains("Учебный год"));
        assertTrue(mcko.contains("Дата работы"));
        assertTrue(mcko.contains("Предмет"));
        assertTrue(summary.contains("По ребёнку"));
        assertTrue(summary.contains("По классу"));
        assertTrue(summary.contains("МЦКО по параллелям"));
        assertTrue(summary.contains("Выше города"));
        assertTrue(summary.contains("На уровне города (разница до ±1 п.п.)"));
        assertTrue(summary.contains("Ниже города"));
        assertTrue(summaryScript.contains("/api/vsoko/mcko/parallels/summary"));
        assertTrue(summaryScript.contains("mcko-heat-above"));
        assertTrue(interview.contains("Список на печать"));
        assertTrue(assignments.contains("Импорт Excel"));
        assertTrue(assignments.contains("Экспорт Excel"));
        assertTrue(mckoScript.contains("MCKO_UPLOAD_BATCH_BYTES"));
        assertTrue(mckoScript.contains("splitUploadBatches"));
        assertTrue(mckoScript.contains("Пакет ${index + 1} из ${batches.length}"));
        assertTrue(mcko.contains("mcko-file-summary"));
        assertTrue(mckoScript.contains("mckoState.files = [...mckoState.files, ...files]"));
        assertTrue(mckoScript.contains("const completedFiles = new Set()"));
        assertTrue(mckoScript.contains("Выбрано файлов:"));
        assertTrue(mckoScript.contains("/api/vsoko/mcko/imports?limit=5000"));
    }
}
