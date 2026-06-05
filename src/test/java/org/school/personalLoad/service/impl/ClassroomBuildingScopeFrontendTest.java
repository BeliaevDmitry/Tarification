package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomBuildingScopeFrontendTest {

    @Test
    void classEditFormUsesExplicitOrganizationalAndPhysicalScopeCopy() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/classes.html"));
        String js = Files.readString(Path.of("src/main/resources/static/classes.js"));

        assertTrue(html.contains("Основной корпус / площадка"));
        assertTrue(html.contains("Основной корпус"));
        assertTrue(html.contains("Физическая площадка / адрес"));
        assertTrue(html.contains("Класс будет отображаться во вкладке выбранного основного корпуса. Физическая площадка может быть любой существующей площадкой; педагоги и часы не изменятся."));
        assertTrue(js.contains("function fillPhysicalSiteOptions(selectEl, selectedId = null, fallbackAddress = \"\", buildingCode = \"\")"));
        assertFalse(js.contains(".filter((b) => !selectedGroup || b.code === selectedGroup)"));
        assertTrue(js.contains("const ordinaryPatchEntry = shouldTransferScope"));
        assertTrue(js.contains("numberSchoolBuilding: buildingGroupCode(editingOriginalEntry.numberSchoolBuilding)"));
        assertTrue(js.contains("schoolBuildingId: Number(editingOriginalEntry.schoolBuildingId) || null"));
        assertTrue(js.contains("campusAddress: norm(editingOriginalEntry.campusAddress)"));
        assertTrue(js.contains("body: JSON.stringify(ordinaryPatchEntry)"));
        assertTrue(js.contains("/api/classes/${encodeURIComponent(entry.id)}/building-scope"));
        assertTrue(js.contains("function buildingGroupIdForCode(code)"));
        assertTrue(js.contains("buildingGroupId: buildingGroupIdForCode(entry.numberSchoolBuilding)"));
        assertTrue(js.contains("schoolBuildingId: entry.schoolBuildingId"));
        assertTrue(js.contains("teacherId,"));

        int updateEntryStart = js.indexOf("async function updateEntry(entry)");
        String updateEntry = js.substring(updateEntryStart, js.indexOf("updateTemplateLink();", updateEntryStart));
        assertTrue(!updateEntry.contains("catch"), "updateEntry must propagate transfer errors to the form-level error handler");
    }
}
