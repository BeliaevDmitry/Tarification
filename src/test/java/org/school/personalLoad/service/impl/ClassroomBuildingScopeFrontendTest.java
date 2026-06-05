package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomBuildingScopeFrontendTest {

    @Test
    void classEditFormUsesExplicitOrganizationalAndPhysicalScopeCopy() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/classes.html"));
        String js = Files.readString(Path.of("src/main/resources/static/classes.js"));

        assertTrue(html.contains("Основной корпус / площадка"));
        assertTrue(html.contains("Основной корпус"));
        assertTrue(html.contains("Физическая площадка / адрес"));
        assertTrue(html.contains("Класс будет перенесён в другой основной корпус. Учебный план и уже распределённая нагрузка этого класса будут отображаться в новой вкладке корпуса. Педагоги и часы не изменятся."));
        assertTrue(js.contains("function fillPhysicalSiteOptions(selectEl, selectedId = null, fallbackAddress = \"\", buildingCode = \"\")"));
        assertTrue(js.contains(".filter((b) => !selectedGroup || b.code === selectedGroup)"));
        assertTrue(js.contains("/api/classes/${encodeURIComponent(entry.id)}/building-scope"));
        assertTrue(js.contains("body: JSON.stringify({ schoolBuildingId: entry.schoolBuildingId })"));
        assertTrue(js.contains("teacherId,"));
    }
}
