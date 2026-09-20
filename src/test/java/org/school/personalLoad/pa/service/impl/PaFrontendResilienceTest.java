package org.school.personalLoad.pa.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaFrontendResilienceTest {

    @Test
    void paRegistryDoesNotDisappearWhenAuxiliaryCatalogRequestFails() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/vsoko-pa.js"));

        assertTrue(script.contains("const optionalPaData = (path, fallback, label)"));
        assertTrue(script.contains("paApi('/api/pa/specifications/summary')"));
        assertTrue(script.contains("paApi('/api/pa/specifications')"));
        assertTrue(script.contains("optionalPaData('/api/subjects'"));
        assertTrue(script.contains("optionalPaData('/api/curriculum'"));
    }

    @Test
    void specificationsPageDownloadsImportCompatibleTemplate() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/vsoko-pa-spec.html"));
        String script = Files.readString(Path.of("src/main/resources/static/vsoko-pa.js"));

        assertTrue(page.contains("id=\"pa-spec-template-btn\""));
        assertTrue(page.contains("Скачать шаблон спецификации"));
        assertTrue(page.contains("/instructions/pa-methodist-instruction.docx"));
        assertTrue(script.contains("'/api/pa/specifications/template'"));
        assertTrue(script.contains("bindClick('pa-spec-template-btn', downloadSpecificationTemplate)"));
    }
}
