package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadInRateFrontendTest {
    @Test
    void peopleLoadContainsAllocationWorkspaceAndUsesServerSalaryBreakdown() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/people-load.html"));
        String js = Files.readString(Path.of("src/main/resources/static/people-load.js"));
        String issuesJs = Files.readString(Path.of("src/main/resources/static/load-issues.js"));

        assertTrue(html.contains("id=\"people-load-in-rate-tab\""));
        assertTrue(html.contains("id=\"people-load-in-rate-panel\""));
        assertTrue(html.contains("id=\"in-rate-hours-table\""));
        assertTrue(html.contains("Настроить правила в кадрах"));
        assertTrue(html.contains("/teachers.html#settings"));
        assertTrue(js.contains("/api/manual-load/salary-breakdown"));
        assertTrue(js.contains("/api/manual-load/in-rate"));
        assertTrue(js.contains("data-included-hours"));
        assertTrue(js.contains("data-study-period"));
        assertTrue(js.contains("remainingCapacityHoursH1"));
        assertTrue(js.contains("rateFractionH1"));
        assertTrue(js.contains("capacityHoursH1"));
        assertTrue(js.contains("teacherRowKey(row)"));
        assertTrue(js.contains("const manualRows = await api(\"/api/manual-load\")"));
        assertTrue(js.contains("state.salaryBreakdownAvailable"));
        assertTrue(js.contains("Нагрузка загружена. Временно недоступны дополнительные данные"));
        assertTrue(js.contains("Ранее загруженная нагрузка оставлена на экране"));
        assertTrue(issuesJs.contains("row.targetPage === \"inRate\""));
        assertTrue(issuesJs.contains("/people-load.html"));

        String teachersHtml = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        assertTrue(teachersHtml.contains("id=\"in-rate-rules-settings\""));
        assertTrue(teachersHtml.contains("Должность, для которой действует правило"));
        assertTrue(teachersHtml.contains("Размер занимаемой ставки"));
        assertTrue(teachersHtml.contains("Пример для ОБЗР"));
        assertTrue(teachersHtml.contains("от 1 до 4 часов — 0,5 ставки"));
        assertTrue(teachersHtml.contains("от 5 до 9 часов — 1 ставка"));
        assertTrue(teachersHtml.contains("Фактические часы внутри ставки распределяются отдельно"));
        assertTrue(!teachersHtml.contains("Из них входит в оклад, не более"));
    }
}
