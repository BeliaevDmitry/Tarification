package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadViewOptionsFrontendTest {

    @Test
    void loadPageSupportsAddressModePrimaryClassToggleAndItalicExtracurricularSubject() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/load.html"));
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(html.contains("id=\"load-scope-address-btn\""));
        assertTrue(html.contains("id=\"load-scope-building-btn\""));
        assertTrue(html.contains("id=\"toggle-primary-classes-btn\""));
        assertTrue(js.contains("visibleClassesForSelectedBuilding"));
        assertTrue(js.contains("parallel == null || parallel >= 5"));
        assertTrue(js.contains("return leftParallel - rightParallel"));
        assertTrue(js.contains("state.hidePrimaryClasses && teacherName && classCount === 0 && !hasVisiblePlannedLoad"));
        assertTrue(js.contains("building.scope === \"address\""));
        assertTrue(js.contains("row.curriculumPart === \"EXTRACURRICULAR\" ? \"extracurricular-subject\""));
    }

    @Test
    void peopleLoadAndCurriculumExposeAllAndAddressExport() throws Exception {
        String peopleHtml = Files.readString(Path.of("src/main/resources/static/people-load.html"));
        String peopleJs = Files.readString(Path.of("src/main/resources/static/people-load.js"));
        String curriculumHtml = Files.readString(Path.of("src/main/resources/static/curriculum.html"));
        String curriculumJs = Files.readString(Path.of("src/main/resources/static/curriculum.js"));
        String authJs = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(peopleHtml.contains("Выберите «ВСЕ»"));
        assertTrue(peopleJs.contains("const ALL_BUILDINGS_VALUE = \"__ALL__\""));
        assertTrue(peopleJs.contains("allOption.textContent = \"ВСЕ\""));
        assertTrue(curriculumHtml.contains("id=\"export-curriculum-addresses-btn\""));
        assertTrue(curriculumHtml.contains("data-requires-export data-allow-readonly=\"true\""));
        assertTrue(curriculumJs.contains("/api/curriculum/export-addresses"));
        assertTrue(authJs.contains("function canExportCurrentPage"));
        assertTrue(authJs.contains("[data-requires-export]"));
    }
}
