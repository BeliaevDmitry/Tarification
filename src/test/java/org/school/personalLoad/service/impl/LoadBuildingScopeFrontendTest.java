package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadBuildingScopeFrontendTest {

    @Test
    void loadScopesUseOrganizationalBuildingAndPhysicalSiteId() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(js.contains("function buildingSiteIdToken(value)"));
        assertTrue(js.contains("api(\"/api/building-groups\")"));
        assertTrue(js.contains("return buildingSiteIdToken(value) != null || Boolean(buildingAddressToken(value));"));
        assertTrue(js.contains("codes.push(`${groupCode}::${rowSchoolBuildingId}`);"));
        assertTrue(js.contains("const value = schoolBuildingId != null ? `${group.code}::${schoolBuildingId}` : `${group.code}|${addressKey}`;"));
        assertTrue(js.contains("if (siteId != null) return siteId;"));
        assertTrue(js.contains("const selectedSiteId = buildingSiteIdToken(accessCode);"));
        assertTrue(js.contains("if (selectedSiteId != null)"));
        assertTrue(js.contains("Number(rowSchoolBuildingId) === Number(selectedSiteId)"));
    }

    @Test
    void physicalSiteScopeFilteringKeepsClassesAndSharedSitesSeparated() {
        java.util.List<Row> sp1Rows = java.util.List.of(
                new Row("СП1", 101L, "5-А"),
                new Row("СП1", 102L, "5-Б")
        );

        assertVisibleClasses(sp1Rows, "СП1", "5-А", "5-Б");
        assertVisibleClasses(sp1Rows, "СП1::101", "5-А");
        assertVisibleClasses(sp1Rows, "СП1::102", "5-Б");

        java.util.List<Row> sharedSiteRows = java.util.List.of(
                new Row("СП3", 38L, "6-А"),
                new Row("СП3 МЕХМАТ", 38L, "6-М")
        );

        assertVisibleClasses(sharedSiteRows, "СП3::38", "6-А");
        assertVisibleClasses(sharedSiteRows, "СП3 МЕХМАТ::38", "6-М");
    }

    private void assertVisibleClasses(java.util.List<Row> rows, String activeScope, String... expectedClasses) {
        java.util.List<String> actual = rows.stream()
                .filter(row -> rowMatchesBuildingAccess(row, activeScope))
                .map(Row::className)
                .toList();

        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(expectedClasses), actual);
    }

    private boolean rowMatchesBuildingAccess(Row row, String accessCode) {
        String selectedOrganizationalSp = buildingGroupCode(accessCode);
        String rowOrganizationalSp = buildingGroupCode(row.numberSchoolBuilding());
        if (!rowOrganizationalSp.equals(selectedOrganizationalSp)) return false;

        Long selectedSiteId = buildingSiteIdToken(accessCode);
        if (selectedSiteId != null) {
            return java.util.Objects.equals(row.schoolBuildingId(), selectedSiteId);
        }

        return true;
    }

    private String buildingGroupCode(String value) {
        String normalized = normalizeBuildingAccessCode(value);
        int siteSeparatorIndex = normalized.indexOf("::");
        if (siteSeparatorIndex >= 0) return normalized.substring(0, siteSeparatorIndex);
        int separatorIndex = normalized.indexOf("|");
        return separatorIndex >= 0 ? normalized.substring(0, separatorIndex) : normalized;
    }

    private Long buildingSiteIdToken(String value) {
        String normalized = normalizeBuildingAccessCode(value);
        int separatorIndex = normalized.indexOf("::");
        if (separatorIndex < 0) return null;
        return Long.valueOf(normalized.substring(separatorIndex + 2));
    }

    private String normalizeBuildingAccessCode(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .toUpperCase()
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s+", "");
    }

    private record Row(String numberSchoolBuilding, Long schoolBuildingId, String className) {}
}
