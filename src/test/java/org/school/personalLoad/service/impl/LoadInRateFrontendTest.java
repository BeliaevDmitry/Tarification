package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadInRateFrontendTest {
    @Test
    void ratesModuleIsSeparateAndUsesAllowedSubjectsAcrossAllBuildings() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/people-load.html"));
        String js = Files.readString(Path.of("src/main/resources/static/people-load.js"));
        String ratesHtml = Files.readString(Path.of("src/main/resources/static/rates.html"));
        String ratesJs = Files.readString(Path.of("src/main/resources/static/rates.js"));
        String issuesJs = Files.readString(Path.of("src/main/resources/static/load-issues.js"));

        assertTrue(!html.contains("id=\"people-load-in-rate-tab\""));
        assertTrue(!html.contains("id=\"people-load-in-rate-panel\""));
        assertTrue(js.contains("/api/manual-load/salary-breakdown"));
        assertTrue(!js.contains("api(\"/api/manual-load/in-rate\")"));
        assertTrue(js.contains("teacherRowKey(row)"));
        assertTrue(js.contains("const manualRows = await api(\"/api/manual-load\")"));
        assertTrue(js.contains("state.salaryBreakdownAvailable"));
        assertTrue(js.contains("Нагрузка загружена. Временно недоступны дополнительные данные"));
        assertTrue(js.contains("Ранее загруженная нагрузка оставлена на экране"));
        assertTrue(ratesHtml.contains("<h1>Ставки</h1>"));
        assertTrue(ratesHtml.contains("сразу по всем корпусам"));
        assertTrue(ratesHtml.contains("id=\"rates-table\""));
        assertTrue(ratesJs.contains("/api/manual-load/in-rate"));
        assertTrue(ratesJs.contains("<th>Корпус</th>"));
        assertTrue(ratesJs.contains("data-included-hours"));
        assertTrue(ratesJs.contains("remainingCapacityHoursH1"));
        assertTrue(issuesJs.contains("row.targetPage === \"inRate\""));
        assertTrue(issuesJs.contains("/rates.html"));

        String teachersHtml = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        assertTrue(teachersHtml.contains("id=\"in-rate-rules-settings\""));
        assertTrue(teachersHtml.contains("Должность, для которой действует правило"));
        assertTrue(teachersHtml.contains("Предметы, которые могут входить в ставку"));
        assertTrue(teachersHtml.contains("Размер занимаемой ставки"));
        assertTrue(teachersHtml.contains("Пример для ОБЗР"));
        assertTrue(teachersHtml.contains("от 1 до 4 часов — 0,5 ставки"));
        assertTrue(teachersHtml.contains("от 5 до 9 часов — 1 ставка"));
        assertTrue(teachersHtml.contains("Фактические часы распределяются отдельно"));
        assertTrue(!teachersHtml.contains("Из них входит в оклад, не более"));
    }
}
