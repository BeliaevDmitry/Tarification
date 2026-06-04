package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumManualLoadExclusionFrontendTest {

    @Test
    void loadFilteringUsesDedicatedExclusionFlagButAlwaysIncludesExplicitMetaGroups() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(js.contains("if (row?.schoolBuildingId !== null && row?.schoolBuildingId !== undefined) return Number(row.schoolBuildingId);"));
        assertTrue(js.contains("if (isExplicitMetaGroupRow(row)) return true;"));
        assertTrue(js.contains("return !Boolean(row?.excludedFromManualLoad);"));
        assertFalse(js.contains("return !Boolean(row?.metaGroup);"));
    }

    @Test
    void loadTabsCreateSyntheticOrganizationalAddressScopesForExplicitMetaGroups() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(js.contains("function mergeMetaGroupAddressScopeOptions(buildingGroups, curriculumSourceRows, physicalBuildingRows)"));
        assertTrue(js.contains(".filter(contributesToManualLoad)"));
        assertTrue(js.contains(".filter(isExplicitMetaGroupRow)"));
        assertTrue(js.contains("physicalById.get(schoolBuildingId)"));
        assertTrue(js.contains("const organizationalSp = normalizeBuildingCode(row.numberSchoolBuilding);"));
        assertTrue(js.contains("buildingGroups.set(organizationalSp, existing);"));
        assertTrue(js.contains("Number(site?.id ?? site?.schoolBuildingId) === schoolBuildingId"));
        assertTrue(js.contains("mergeMetaGroupAddressScopeOptions(buildingGroups, allCurriculumRows || [], buildingRows || []);"));
        assertTrue(js.contains("const allCurriculumPromise = api(\"/api/curriculum\");"));
    }

    @Test
    void curriculumUiSubmitsExclusionFlagAndProtectsExplicitMetaGroups() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/curriculum.js"));
        String html = Files.readString(Path.of("src/main/resources/static/curriculum.html"));

        assertTrue(html.contains("Не переносить в нагрузку"));
        assertTrue(html.contains("name=\"excludedFromManualLoad\""));
        assertTrue(html.contains("Строка метагруппы переносится в нагрузку и распределяется педагогу."));
        assertTrue(js.contains("control.disabled = true;"));
        assertTrue(js.contains("excludedFromManualLoad: isExplicitMetaGroupClassName(className) ? false"));
        assertTrue(js.contains("className: v.className"));
        assertTrue(js.contains("excludedFromManualLoad: Boolean(v.excludedFromManualLoad)"));
        assertTrue(js.contains("markers.push(\"Н\")"));
        assertFalse(js.contains("markers.push(\"М\")"));
    }
}
