package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContingentImportFrontendTest {

    @Test
    void importPageOffersMeshScriptCopyDownloadAndCsvUpload() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/contingent.html"));
        String pageScript = Files.readString(Path.of("src/main/resources/static/contingent.js"));
        String exporter = Files.readString(Path.of("src/main/resources/static/mes-contingent-export.js"));

        assertTrue(html.contains("id=\"contingent-copy-mes-script-btn\""));
        assertTrue(html.contains("id=\"contingent-download-mes-script-btn\""));
        assertTrue(html.contains("accept=\".csv,text/csv,.xlsx,.xls\""));
        assertTrue(pageScript.contains("/mes-contingent-export.js"));
        assertTrue(pageScript.contains("navigator.clipboard.writeText"));
        assertTrue(pageScript.contains("MES_EXTENDED_CSV"));
        assertTrue(html.contains("data-contingent-tab=\"mismatches\""));
        assertTrue(html.contains("id=\"contingent-mismatch-dialog\""));
        assertTrue(html.contains("<strong>АИС</strong>"));
        assertTrue(pageScript.contains("/api/contingent/import-mismatches/resolve"));
        assertTrue(exporter.contains("/api/ej/core/teacher/v1/student_profiles"));
        assertTrue(exporter.contains("'ФИО ребёнка'"));
        assertTrue(exporter.contains("`Представитель ${n} — телефон`"));
    }

    @Test
    void studentCardDisplaysImportedContactDetails() throws Exception {
        String card = Files.readString(Path.of("src/main/resources/static/vsoko-summary.js"));

        assertTrue(card.contains("Телефон ребёнка"));
        assertTrue(card.contains("data.childPhone"));
        assertTrue(card.contains("data.representativeName"));
        assertTrue(card.contains("data.representativePhone"));
    }

    @Test
    void repeatedReconciliationClickIsBlockedUntilRequestFinishes() throws Exception {
        String pageScript = Files.readString(Path.of("src/main/resources/static/contingent.js"));

        assertTrue(pageScript.contains("if (supportReconcileInProgress) return;"));
        assertTrue(pageScript.contains("supportReconcileInProgress = true;"));
        assertTrue(pageScript.contains("ui.supportReconcileBtn.disabled = true;"));
        assertTrue(pageScript.contains("ui.supportReconcileBtn.textContent = 'Сопоставляю…';"));
        assertTrue(pageScript.contains("supportReconcileInProgress = false;"));
    }
}
