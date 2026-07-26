package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PedagogicalCouncilsFrontendTest {

    @Test
    void mainMenuAndPermissionMatrixExposeDocumentsModule() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));
        String admin = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(index.contains("href=\"/documents.html\""));
        assertTrue(index.contains("data-documents-card"));
        assertTrue(auth.contains("DOCUMENTS_PEDAGOGICAL_COUNCILS"));
        assertTrue(admin.contains("key: 'DOCUMENTS'"));
        assertTrue(admin.contains("key: 'DOCUMENTS_PEDAGOGICAL_COUNCILS'"));
    }

    @Test
    void pageOffersBothConstructorAndArchiveWordStorage() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/pedagogical-councils.html"));
        String js = Files.readString(Path.of("src/main/resources/static/pedagogical-councils.js"));

        assertTrue(html.contains("Создать протокол"));
        assertTrue(html.contains("Загрузить старый Word"));
        assertTrue(html.contains("<select id=\"archive-academic-year\" name=\"academicYear\" required>"));
        assertTrue(html.contains("id=\"archive-year-date-hint\""));
        assertTrue(html.contains("id=\"protocol-chair-position\""));
        assertTrue(html.contains("id=\"protocol-chair-fio\""));
        assertTrue(html.contains("id=\"protocol-secretary-position\""));
        assertTrue(html.contains("id=\"protocol-secretary-fio\""));
        assertTrue(html.contains("value=\"Заместитель директора\""));
        assertTrue(html.contains("data-field=\"agendaDurationMinutes\""));
        assertTrue(html.contains("value=\"10\" required"));
        assertTrue(html.contains("data-vote-hint"));
        assertTrue(html.contains("Приложения к этому пункту"));
        assertTrue(html.contains("Можно выбрать одного или нескольких сотрудников"));
        assertTrue(js.contains("/api/pedagogical-councils/archive"));
        assertTrue(js.contains("pedApi('/api/academic-years')"));
        assertTrue(js.contains("pedApi('/api/public/branding')"));
        assertTrue(js.contains("function currentSchoolName()"));
        assertTrue(js.contains("function updateVoteHint(node)"));
        assertTrue(js.contains("Осталось: ${remaining}"));
        assertTrue(js.contains("Превышение: ${exceeded}"));
        assertTrue(js.contains("updateArchiveYearBounds"));
        assertTrue(js.contains("pedUi.archiveDate.min = from"));
        assertTrue(js.contains("pedUi.archiveDate.max = to"));
        assertTrue(js.contains("/extract"));
        assertTrue(js.contains("certifierUserIds"));
    }

    @Test
    void authFilterProtectsDocumentsPageAndApi() throws Exception {
        String filter = Files.readString(Path.of("src/main/java/org/school/personalLoad/config/auth/AuthFilter.java"));

        assertTrue(filter.contains("Map.entry(\"/documents.html\", AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)"));
        assertTrue(filter.contains("Map.entry(\"/pedagogical-councils.html\", AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)"));
        assertTrue(filter.contains("path.startsWith(\"/api/pedagogical-councils\")"));
    }
}
