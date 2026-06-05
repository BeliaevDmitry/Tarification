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
        assertTrue(js.contains("Number(rowSchoolBuildingId) === Number(selectedSchoolBuildingId)"));
    }
}
