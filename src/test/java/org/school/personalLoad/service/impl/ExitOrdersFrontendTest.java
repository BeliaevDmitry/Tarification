package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitOrdersFrontendTest {

    @Test
    void exitOrderModuleExposesConstructorSettingsAndApprovalWorkflow() throws Exception {
        String documents = Files.readString(Path.of("src/main/resources/static/documents.html"));
        String page = Files.readString(Path.of("src/main/resources/static/exit-orders.html"));
        String script = Files.readString(Path.of("src/main/resources/static/exit-orders.js"));
        String settingsPage = Files.readString(Path.of("src/main/resources/static/exit-order-settings.html"));
        String settingsScript = Files.readString(Path.of("src/main/resources/static/exit-order-settings.js"));
        String summaryPage = Files.readString(Path.of("src/main/resources/static/exit-orders-summary.html"));
        String summaryScript = Files.readString(Path.of("src/main/resources/static/exit-orders-summary.js"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));
        String admin = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(documents.contains("href=\"/exit-orders.html\""));
        assertTrue(page.contains("href=\"/exit-order-settings.html\""));
        assertTrue(page.contains("href=\"/exit-orders-summary.html\""));
        assertTrue(page.contains("id=\"exit-preamble\""));
        assertTrue(page.contains("id=\"exit-class-picker\""));
        assertTrue(page.contains("id=\"exit-attendance-dialog\""));
        assertTrue(script.contains("/api/exit-orders/references"));
        assertTrue(script.contains("suggestedClassIds"));
        assertTrue(script.contains("data-exit-class"));
        assertTrue(script.contains("data-exit-student"));
        assertTrue(script.contains("/acknowledge"));
        assertTrue(script.contains("/generate"));
        assertTrue(script.contains("/release"));
        assertTrue(script.contains("/attendance"));
        assertTrue(settingsPage.contains("id=\"exit-settings-preambles\""));
        assertTrue(settingsPage.contains("id=\"exit-settings-event-names\""));
        assertTrue(settingsPage.contains("id=\"exit-settings-venues\""));
        assertTrue(settingsPage.contains("id=\"exit-settings-addresses\""));
        assertTrue(settingsPage.contains("id=\"exit-settings-gathering\""));
        assertTrue(settingsScript.contains("/api/exit-orders/settings"));
        assertTrue(settingsScript.contains("PREAMBLE"));
        assertTrue(summaryPage.contains("Самые активные классы"));
        assertTrue(summaryPage.contains("Активные педагоги"));
        assertTrue(summaryScript.contains("/api/exit-orders/summary"));
        assertTrue(auth.contains("'/exit-orders.html': 'DOCUMENTS_EXIT_ORDERS'"));
        assertTrue(admin.contains("key: 'DOCUMENTS_EXIT_ORDERS'"));
    }

    @Test
    void approvedExitOrdersAreLoadedIntoHomeCalendar() throws Exception {
        String calendar = Files.readString(Path.of("src/main/resources/static/home-calendar.js"));

        assertTrue(calendar.contains("/api/exit-orders/calendar"));
        assertTrue(calendar.contains("EXIT_ORDER"));
        assertTrue(calendar.contains("Мероприятие из согласованного приказа на выход"));
    }
}
