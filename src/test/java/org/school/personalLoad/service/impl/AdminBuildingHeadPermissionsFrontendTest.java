package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBuildingHeadPermissionsFrontendTest {

    @Test
    void buildingHeadManagedBuildingDropdownUsesOrganizationalBuildingGroups() throws Exception {
        String adminJs = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(adminJs.contains("let buildingGroups = [];"));
        assertTrue(adminJs.contains("api('/api/building-groups')"));
        assertTrue(adminJs.contains("option.value = building.value"));
        assertTrue(adminJs.contains("managedBuildingCode: String(form.get('managedBuildingCode') || '').trim()"));

        String buildingGroupOptions = functionBody(adminJs, "function buildingGroupOptions()", "function buildingAccessOptions()");
        assertTrue(buildingGroupOptions.contains("(buildingGroups || [])"));
        assertTrue(buildingGroupOptions.contains("return { value: code, label: buildingGroupOptionLabel(group) };"));
        assertFalse(buildingGroupOptions.contains("(buildings || [])"));
        assertFalse(buildingGroupOptions.contains("buildingAccessValue"));
    }


    @Test
    void organizationalBuildingGroupWithoutPhysicalSiteIsAvailableForNewBuildingHeadAssignment() {
        java.util.List<Group> groups = java.util.List.of(
                new Group("СП3", "СП3"),
                new Group("МЕХМАТ", "МЕХМАТ")
        );
        java.util.List<Building> physicalSites = java.util.List.of(
                new Building("СП3", "Кравченко, д.14, корп.1")
        );

        java.util.List<Option> options = groupOptions(groups);

        assertTrue(options.contains(new Option("МЕХМАТ", "МЕХМАТ")));
        assertTrue(options.contains(new Option("СП3", "СП3")));
        assertFalse(physicalSites.stream().anyMatch((site) -> "МЕХМАТ".equals(site.code())));
        assertTrue(options.stream().noneMatch((option) -> option.value().contains("Кравченко")));
        assertTrue(savePayloadManagedBuildingCode("МЕХМАТ").contains("\"managedBuildingCode\":\"МЕХМАТ\""));
    }

    @Test
    void buildingHeadDropdownPreservesLegacyAddressScopeValues() throws Exception {
        String adminJs = Files.readString(Path.of("src/main/resources/static/admin.js"));

        String renderBuildingSelect = functionBody(adminJs, "function renderBuildingSelect", "function scopeInputs");
        assertTrue(renderBuildingSelect.contains("const rawSelected = String(selectedValue || '').trim();"));
        assertTrue(renderBuildingSelect.contains("legacyOption.value = rawSelected"));
        assertTrue(renderBuildingSelect.contains("Текущее значение: ${rawSelected}"));
        assertTrue(renderBuildingSelect.contains("legacyOption.selected = true"));
        assertTrue(renderBuildingSelect.contains("const options = buildingGroupOptions();"));
    }


    private java.util.List<Option> groupOptions(java.util.List<Group> groups) {
        return groups.stream()
                .map((group) -> new Option(group.code(), group.name().equals(group.code()) ? group.code() : group.code() + " — " + group.name()))
                .toList();
    }

    private String savePayloadManagedBuildingCode(String selectedCode) {
        return "{\"managedBuildingCode\":\"" + selectedCode.trim() + "\"}";
    }

    private record Group(String code, String name) {}

    private record Building(String code, String address) {}

    private record Option(String value, String label) {}

    private String functionBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        assertTrue(start >= 0, () -> "Missing marker: " + startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(end > start, () -> "Missing marker after " + startMarker + ": " + endMarker);
        return source.substring(start, end);
    }
}
