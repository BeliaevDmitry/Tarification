package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OvzSpecialistsFrontendTest {

    @Test
    void ovzNavigationContainsAllWorkingAreas() throws Exception {
        String ovz = Files.readString(Path.of("src/main/resources/static/ovz.html"));
        String distribution = Files.readString(Path.of("src/main/resources/static/ovz-specialist-distribution.html"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(ovz.contains("href=\"/ovz-specialists.html\""));
        assertTrue(auth.contains("label: 'Реестр'"));
        assertTrue(auth.contains("label: 'Справки'"));
        assertTrue(auth.contains("label: 'Справочник нозологий'"));
        assertTrue(auth.contains("label: 'ППк'"));
        assertTrue(auth.contains("label: 'Распределение по специалистам'"));
        assertTrue(auth.contains("label: 'Специалисты'"));
        assertTrue(distribution.contains("class=\"page-nav\""));
    }

    @Test
    void specialistsWorkspaceContainsIomSupportFieldsAndResponsibleSettings() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/ovz-specialists.html"));
        String script = Files.readString(Path.of("src/main/resources/static/ovz-specialists.js"));

        assertTrue(page.contains("id=\"specialists-settings-open\""));
        assertTrue(page.contains("Ответственный из кадров"));
        assertTrue(page.contains("id=\"specialists-children-body\""));
        assertTrue(page.contains("id=\"specialists-show-mine\""));
        assertTrue(page.contains("Отобразить привязанных"));
        assertTrue(page.contains("id=\"specialists-show-all\""));
        assertTrue(page.contains("Отобразить всех"));
        assertTrue(script.contains("let specialistsScope = 'mine'"));
        assertTrue(script.contains("specialistsMineChildren"));
        assertTrue(script.contains("Основные дефициты ребёнка"));
        assertTrue(script.contains("Ресурсы ребёнка"));
        assertTrue(script.contains("Основные задачи развития на год"));
        assertTrue(script.contains("Планируемые результаты"));
        assertTrue(script.contains("support-status-not-started"));
        assertTrue(script.contains("support-status-in-progress"));
        assertTrue(script.contains("support-status-completed"));
        assertTrue(script.contains("/api/ovz/specialist-workspace"));
    }
}
