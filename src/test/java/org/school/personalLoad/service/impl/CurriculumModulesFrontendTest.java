package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumModulesFrontendTest {

    @Test
    void curriculumEditorSupportsMultipleModulesAndDetailedViewByDefault() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/curriculum.html"));
        String curriculumJs = Files.readString(Path.of("src/main/resources/static/curriculum.js"));

        assertTrue(html.contains("id=\"module-list\""));
        assertTrue(html.contains("<details id=\"subject-create-panel\""));
        assertTrue(html.contains("<summary>Добавить/обновить предмет</summary>"));
        assertTrue(html.indexOf("value=\"detailed\"") < html.indexOf("value=\"general\""));
        assertTrue(curriculumJs.contains("modules.push(defaultModule(modules.length))"));
        assertTrue(curriculumJs.contains("assertModulesPersisted(saved, payload)"));
        assertTrue(curriculumJs.contains("Сумма часов модулей"));
        assertTrue(curriculumJs.contains("subjectName: `${row.subjectName} (${module.moduleName})`"));
    }

    @Test
    void loadIdentityContainsModuleIdAndKeepsBaseSubjectName() throws Exception {
        String loadJs = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(loadJs.contains("`|M:${row.__moduleId}`"));
        assertTrue(loadJs.contains("|M:${row.__moduleId}${groupSuffix(row)}`"));
        assertTrue(loadJs.contains("`${row.subjectName} (${row.__moduleName})`"));
        assertTrue(loadJs.contains("displaySubjectName: displaySubjectName(row)"));
        assertTrue(loadJs.contains("subgroupFamilyKey(row) !== familyKey"));
        org.junit.jupiter.api.Assertions.assertFalse(loadJs.contains("|MODULAR`"));
        assertTrue(loadJs.contains("curriculumModuleId: row.__moduleId || null"));
        assertTrue(loadJs.contains("Модуль ${item.__moduleOrder}: ${item.__moduleName}"));
        assertTrue(loadJs.contains("subjectName: row.subjectName"));
    }

    @Test
    void subgroupDrawerCombinesBothGroupsButDoesNotCombineDifferentModules() throws Exception {
        String loadJs = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(loadJs.contains("function subgroupFamilyKey(row)"));
        assertTrue(loadJs.contains("const moduleToken = row.__moduleId ? `|M:${row.__moduleId}` : \"\""));
        assertTrue(loadJs.contains("if (subgroupFamilyKey(row) !== familyKey) return false"));
        assertTrue(loadJs.contains("presentationRow.displaySubjectName || presentationRow.subjectName"));
    }

    @Test
    void disabledModuleSystemHidesModuleEditorAndDisablesItsRequiredFields() throws Exception {
        String styles = Files.readString(Path.of("src/main/resources/static/styles.css"));
        String curriculumJs = Files.readString(Path.of("src/main/resources/static/curriculum.js"));

        assertTrue(styles.contains(".module-config.hidden"));
        assertTrue(curriculumJs.contains("config?.classList.toggle(\"hidden\", !enabled)"));
        assertTrue(curriculumJs.contains("config?.querySelectorAll(\"[data-module-field]\")"));
        assertTrue(curriculumJs.contains("control.disabled = !enabled"));
    }
}
