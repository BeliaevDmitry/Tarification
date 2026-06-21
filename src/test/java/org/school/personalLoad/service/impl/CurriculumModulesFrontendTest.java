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
        assertTrue(curriculumJs.contains("subjectName: `${row.subjectName}, ${module.moduleName}`"));
    }

    @Test
    void loadIdentityContainsModuleIdAndKeepsBaseSubjectName() throws Exception {
        String loadJs = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(loadJs.contains("`|M:${row.__moduleId}`"));
        assertTrue(loadJs.contains("curriculumModuleId: row.__moduleId || null"));
        assertTrue(loadJs.contains("Модуль ${item.__moduleOrder}: ${item.__moduleName}"));
        assertTrue(loadJs.contains("subjectName: row.subjectName"));
    }
}
