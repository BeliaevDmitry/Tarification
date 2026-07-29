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
}
