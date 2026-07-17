package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HrDocumentsFrontendTest {
    @Test
    void pageContainsReworkedMemoAndPersonalDataControls() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/teachers-notification.html"));
        String js = Files.readString(Path.of("src/main/resources/static/teachers-notification.js"));

        assertTrue(html.contains("Предварительная нагрузка (уведомления)"));
        assertTrue(html.contains("Импортировать Excel"));
        assertTrue(html.contains("hr-form-grid"));
        assertTrue(js.contains("Обязанность из справочника"));
        assertTrue(js.contains("Нет — пункт 2.4"));
        assertTrue(js.contains("Да — отдельное соглашение"));
        assertTrue(js.contains("data-issue-memo"));
        assertTrue(js.contains("Ожидает служебку"));
        assertTrue(js.contains("/api/hr-documents/load-memos"));
        assertTrue(js.contains("Способ изменения"));
        assertTrue(js.contains("CATEGORY_LABELS"));
        assertTrue(js.contains("personal-data/import"));
    }
}
