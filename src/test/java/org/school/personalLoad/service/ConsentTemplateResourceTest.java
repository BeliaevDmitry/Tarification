package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsentTemplateResourceTest {

    @Test
    void consentTemplateIsPackagedAsValidDocx() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "templates/ovz/consent-diagnostics-support-template.docx");
        assertNotNull(stream);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }

        assertTrue(entries.contains("[Content_Types].xml"));
        assertTrue(entries.contains("word/document.xml"));
    }
}
