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
        assertTrue(html.indexOf("data-tab=\"journal\"") < html.indexOf("data-tab=\"memos\""));
        assertTrue(html.indexOf("data-tab=\"memos\"") < html.indexOf("data-tab=\"agreements\""));
        assertTrue(html.contains("data-tab=\"journal\" class=\"active\""));
        assertTrue(html.contains(".hr-tabs button.active"));
        assertTrue(html.contains("personal-body"));
        assertTrue(html.contains("Данные хранятся на сервере"));
        assertTrue(html.contains("hr-form-grid"));
        assertTrue(js.contains("Обязанность из справочника"));
        assertTrue(js.contains("Добавить вручную"));
        assertTrue(js.contains("STANDARD_CONTRACT_CLAUSES = ['2.1','2.4','2.5']"));
        assertTrue(js.contains("'2.1':'2.1 — учебная нагрузка'"));
        assertTrue(js.contains("'2.4':'2.4 — дополнительные функции'"));
        assertTrue(js.contains("'2.5':'2.5 — стимулирующие выплаты'"));
        assertTrue(js.contains("Проверить или изменить автоматический текст"));
        assertTrue(js.contains("система сама соберёт полную актуальную редакцию пункта"));
        assertTrue(js.contains("Пока нет договора — служебка всё равно сформируется"));
        assertTrue(js.contains("ожидает договор"));
        assertTrue(js.contains("Promise.allSettled"));
        assertTrue(js.contains("Не удалось загрузить список работников"));
        assertTrue(js.contains("loadTeachersForDocuments"));
        assertTrue(js.contains("api('/api/teachers')"));
        assertTrue(html.contains("teachers-notification.js?v=20260725-3"));
        assertTrue(js.contains("Служебная записка создана и добавлена в таблицу"));
        assertTrue(js.contains("await loadMemos()"));
        assertTrue(html.contains("Дополнительные соглашения"));
        assertTrue(js.contains("/api/hr-documents/agreements?academicYear="));
        assertTrue(js.contains("data-delete-catalog"));
        assertTrue(js.contains("data-delete-memo"));
        assertTrue(js.contains("Нет — изменить выбранный пункт"));
        assertTrue(js.contains("Да — отдельное соглашение"));
        assertTrue(js.contains("data-issue-memo"));
        assertTrue(js.contains("Ожидает служебку"));
        assertTrue(js.contains("/api/hr-documents/load-memos"));
        assertTrue(js.contains("Способ изменения"));
        assertTrue(js.contains("CATEGORY_LABELS"));
        assertTrue(js.contains("personal-data/import"));
        assertTrue(js.contains("data-reject"));
        assertTrue(html.contains("Сформировать на 1 сентября"));
        assertTrue(js.contains("черновик уже сформирован"));
        assertTrue(js.contains("data-edit-agreement"));
        assertTrue(js.contains("Сформировать DOCX"));
        assertTrue(js.contains("Заполнить договор"));
        assertTrue(js.contains("Сохранить как шаблон"));
        assertTrue(js.contains("/prepare"));
        assertTrue(js.contains("loadPersonalData"));
        assertTrue(js.contains("api('/api/hr-documents/personal-data')"));
        assertTrue(js.contains("Создано автоматически из служебной записки"));
        assertTrue(js.contains("Дополнительное соглашение вне служебной записки"));
        assertTrue(!js.contains("ID полученной служебки"));
    }
}
