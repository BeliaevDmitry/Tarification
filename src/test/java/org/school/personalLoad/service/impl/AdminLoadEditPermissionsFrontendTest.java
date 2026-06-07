package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminLoadEditPermissionsFrontendTest {

    @Test
    void loadEditScopeOptionsUseBuildingGroupsAsOrganizationalSource() throws Exception {
        String adminJs = Files.readString(Path.of("src/main/resources/static/admin.js"));

        String buildingGroupOptions = functionBody(adminJs, "function buildingGroupOptions()", "function buildingAccessOptions()");
        assertTrue(buildingGroupOptions.contains("(buildingGroups || [])"));
        assertTrue(buildingGroupOptions.contains("return { value: code, label: buildingGroupOptionLabel(group) };"));
        assertFalse(buildingGroupOptions.contains("(buildings || [])"));

        String buildingAccessOptions = functionBody(adminJs, "function buildingAccessOptions()", "function buildingByCode");
        assertTrue(buildingAccessOptions.contains("buildingGroupOptions().forEach((group)"));
        assertTrue(buildingAccessOptions.contains("options.push({ ...group, groupWide: true })"));
        assertTrue(buildingAccessOptions.contains("label: `${buildingDisplayName(building)} — ${address}`"));
    }

    @Test
    void loadEditScopeSavePayloadContainsSelectedBuildingGroupCode() throws Exception {
        String adminJs = Files.readString(Path.of("src/main/resources/static/admin.js"));

        String selectedLoadBuildings = functionBody(adminJs, "function selectedLoadBuildings", "function loadScopeSummary");
        assertTrue(selectedLoadBuildings.contains("[data-load-building]:checked"));
        assertTrue(selectedLoadBuildings.contains("el.dataset.loadBuilding"));

        String loadScopeState = functionBody(adminJs, "function loadScopeState", "function validateLoadScopeSelection");
        assertTrue(loadScopeState.contains("loadEditableBuildingCodes: selectedLoadBuildings(prefix)"));

        assertTrue(savePayloadLoadEditableBuildingCode("МЕХМАТ").contains("\"loadEditableBuildingCodes\":[\"МЕХМАТ\"]"));
    }

    @Test
    void loadEditScopePreservesLegacySelectedValues() throws Exception {
        String adminJs = Files.readString(Path.of("src/main/resources/static/admin.js"));

        String renderLoadBuildings = functionBody(adminJs, "function renderLoadBuildings", "function selectedLoadBuildings");
        assertTrue(renderLoadBuildings.contains("label: `Текущее значение: ${code}`"));
        assertTrue(renderLoadBuildings.contains("options.concat(legacyOptions)"));
        assertTrue(renderLoadBuildings.contains("selected.has(normalizeBuildingAccessCode(building.value))"));
    }

    private String savePayloadLoadEditableBuildingCode(String selectedCode) {
        return "{\"loadEditableBuildingCodes\":[\"" + selectedCode.trim() + "\"]}";
    }

    private String functionBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        assertTrue(start >= 0, () -> "Missing marker: " + startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(end > start, () -> "Missing marker after " + startMarker + ": " + endMarker);
        return source.substring(start, end);
    }
}
