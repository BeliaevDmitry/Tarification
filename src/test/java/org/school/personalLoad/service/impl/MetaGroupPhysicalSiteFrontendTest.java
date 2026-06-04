package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaGroupPhysicalSiteFrontendTest {

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
    void curriculumImportDoesNotCreateFakeClassroomForExplicitMetaGroups() throws Exception {
        String java = Files.readString(Path.of("src/main/java/org/school/personalLoad/service/impl/CurriculumImportServiceImpl.java"));

        assertTrue(java.contains("boolean explicitMetaGroupRow = isExplicitMetaGroupClassName(normalizedClassName);"));
        assertTrue(java.contains("boolean createdClass = !explicitMetaGroupRow"));
        assertTrue(java.contains("&& ensureClassroom"));
    }
}
