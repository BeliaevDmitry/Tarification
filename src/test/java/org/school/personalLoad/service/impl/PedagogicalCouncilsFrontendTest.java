package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(html.contains("<select id=\"protocol-chair-fio\" name=\"chairFio\">"));
        assertTrue(html.contains("id=\"protocol-secretary-position\""));
        assertTrue(html.contains("<select id=\"protocol-secretary-fio\" name=\"secretaryFio\">"));
        assertFalse(html.contains("<select id=\"protocol-status\""));
        assertFalse(html.contains("На проверке"));
        assertFalse(html.contains("Исправленная версия"));
        assertTrue(html.contains("id=\"protocol-status-view\""));
        assertTrue(html.contains("id=\"protocol-editor-save-draft\" data-save-status=\"DRAFT\""));
        assertTrue(html.contains("id=\"protocol-editor-save\" data-save-status=\"REGISTERED\""));
        assertTrue(html.contains("Сохранить черновик"));
        assertTrue(html.contains("Сохранить и выпустить"));
        assertTrue(html.contains("value=\"Заместитель директора\""));
        assertTrue(html.contains("data-field=\"agendaDurationMinutes\""));
        assertTrue(html.contains("value=\"10\" required"));
        assertTrue(html.contains("data-vote-hint"));
        assertTrue(html.contains("Приложения к этому пункту"));
        assertTrue(html.contains("data-field=\"speakerPosition\""));
        assertTrue(html.contains("ФИО докладчика"));
        assertTrue(html.contains("Следующий свободный номер"));
        assertTrue(html.contains("Файлы загрузятся при сохранении протокола"));
        assertTrue(html.contains("Сотрудник, который формирует выписку, добавляется автоматически"));
        assertTrue(html.contains("id=\"extract-approver-position\""));
        assertTrue(html.contains("id=\"extract-approver-row\" class=\"grid\" hidden"));
        assertTrue(html.contains("id=\"extract-source-signers\" type=\"checkbox\" checked"));
        assertFalse(html.contains("id=\"extract-external\""));
        assertFalse(html.contains("Выписка выдаётся во внешнюю организацию"));
        assertFalse(html.contains("Где хранится подлинник"));
        assertTrue(js.contains("/api/pedagogical-councils/archive"));
        assertTrue(js.contains("pedApi('/api/academic-years')"));
        assertTrue(js.contains("pedApi('/api/public/branding')"));
        assertTrue(js.contains("function currentSchoolName()"));
        assertTrue(js.contains("function staffFioOptions(selectedFio = '')"));
        assertTrue(js.contains("person.shortFio || person.fio"));
        assertTrue(js.contains("function makePersonSelectSearchable(select"));
        assertTrue(js.contains("placeholder = 'Начните вводить фамилию'"));
        assertTrue(js.contains("data-search="));
        assertTrue(js.contains("makePersonSelectSearchable(pedUi.chairFio)"));
        assertTrue(js.contains("makePersonSelectSearchable(pedUi.secretaryFio)"));
        assertTrue(js.contains("makePersonSelectSearchable(speaker)"));
        assertTrue(js.contains("makePersonSelectSearchable(userSelect)"));
        assertTrue(js.contains("makePersonSelectSearchable(pedUi.extractApprover)"));
        assertTrue(js.contains("addCertifierRow(current.userId, current.position, false, true)"));
        assertTrue(js.contains("Можно заменить."));
        assertTrue(js.contains("pedUi.extractSourceSigners.checked = true"));
        assertTrue(js.contains("externalRecipient: true"));
        assertFalse(js.contains("extractExternal"));
        assertTrue(js.contains("function workflowStatus(status)"));
        assertTrue(js.contains("Сохранить и перевыпустить"));
        assertTrue(js.contains("editorPayload('DRAFT')"));
        assertTrue(js.contains("/release"));
        assertTrue(js.contains("Протокол перевыпущен."));
        assertTrue(js.contains("baseHeaderFingerprint"));
        assertTrue(js.contains("baseFingerprint"));
        assertTrue(js.contains("data-delete-protocol"));
        assertTrue(js.contains("Восстановить протокол после удаления нельзя"));
        assertTrue(js.contains("editorSaved: false"));
        assertTrue(js.contains("event.target !== pedUi.editor"));
        assertTrue(js.contains("pedState.editorSaved"));
        assertTrue(js.contains("Есть несохранённые изменения. Сначала сохраните протокол"));
        assertTrue(js.contains("function updateVoteHint(node)"));
        assertTrue(js.contains("Осталось: ${remaining}"));
        assertTrue(js.contains("Превышение: ${exceeded}"));
        assertTrue(js.contains("updateArchiveYearBounds"));
        assertTrue(js.contains("pedUi.archiveDate.min = from"));
        assertTrue(js.contains("pedUi.archiveDate.max = to"));
        assertTrue(js.contains("/extract"));
        assertTrue(js.contains("async function uploadPendingAttachments"));
        assertTrue(js.contains("includeSourceSigners"));
        assertTrue(js.contains("approverPosition"));
    }

    @Test
    void authFilterProtectsDocumentsPageAndApi() throws Exception {
        String filter = Files.readString(Path.of("src/main/java/org/school/personalLoad/config/auth/AuthFilter.java"));

        assertTrue(filter.contains("\"/documents.html\".equals(path) && !hasAnyDocumentsPageAccess(currentUser)"));
        assertTrue(filter.contains("user.canViewTab(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)"));
        assertTrue(filter.contains("Map.entry(\"/pedagogical-councils.html\", AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)"));
        assertTrue(filter.contains("path.startsWith(\"/api/pedagogical-councils\")"));
    }
}
