package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OvzApplicationFrontendTest {

    @Test
    void receivedButtonCompletesApplicationAndRefreshesRoadmapAndRegistry() throws Exception {
        String pageScript = Files.readString(Path.of("src/main/resources/static/ovz.js"));
        String dossierService = Files.readString(
                Path.of("src/main/java/org/school/personalLoad/service/OvzDossierService.java"));

        assertTrue(pageScript.contains("data-application-received"));
        assertTrue(pageScript.contains("'Получено ✓' : 'Получено'"));
        assertTrue(pageScript.contains("/application`, {method:'PUT'"));
        assertTrue(pageScript.contains("renderRoadmap(); openStage('APPLICATION'); await loadRegistry();"));
        assertTrue(pageScript.contains("ovzUi.stage_content.querySelectorAll('[data-application-agreed]')"));
        assertTrue(pageScript.contains("data-download-consent>Скачать шаблон"));
        assertTrue(pageScript.contains("data-consent-received class=\"secondary\""));
        assertTrue(pageScript.contains("data-edit-child-ppk"));
        assertTrue(pageScript.contains("data-sign-child-ppk"));
        assertTrue(pageScript.contains("/signed`, {method:'PUT'}"));
        assertTrue(dossierService.contains("existing.getOrDefault(specialist, new OvzApplicationChoice())"));
        assertTrue(dossierService.contains("stage.setStatus(OvzStageStatus.COMPLETED)"));
    }
}
