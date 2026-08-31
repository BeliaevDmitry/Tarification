package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeOrdersFrontendTest {

    @Test
    void documentsModuleExposesCompleteProbeOrderWorkflow() throws Exception {
        String documents = Files.readString(Path.of("src/main/resources/static/documents.html"));
        String page = Files.readString(Path.of("src/main/resources/static/probe-orders.html"));
        String script = Files.readString(Path.of("src/main/resources/static/probe-orders.js"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));
        String admin = Files.readString(Path.of("src/main/resources/static/admin.js"));

        assertTrue(documents.contains("href=\"/probe-orders.html\""));
        assertTrue(documents.contains("data-probe-orders-card"));
        assertTrue(page.contains("Свежая выгрузка регистрации"));
        assertTrue(page.contains("data-probe-sort=\"eventDate\""));
        assertTrue(page.contains("id=\"probe-companions-dialog\""));
        assertTrue(page.contains("id=\"probe-add-companion\""));
        assertTrue(page.contains("id=\"probe-additional-companions\""));
        assertTrue(page.contains("id=\"probe-generate-dialog\""));
        assertTrue(page.contains("id=\"probe-scan-dialog\""));
        assertTrue(page.contains("id=\"probe-settings-dialog\""));
        assertTrue(page.contains("id=\"probe-deputy-director\""));
        assertTrue(page.contains("Власова Юлия Сергеевна"));
        assertTrue(page.contains("id=\"probe-refresh-contacts-btn\""));
        assertTrue(page.contains("Телефон ребёнка"));
        assertTrue(page.contains("value=\"ORGANIZATIONAL_BUILDING\""));
        assertTrue(page.contains("value=\"PHYSICAL_SITE\""));
        assertTrue(page.contains("value=\"BOTH\""));
        assertTrue(script.contains("/api/probe-orders/import"));
        assertTrue(script.contains("/api/probe-orders/settings"));
        assertTrue(script.contains("deputyDirectorTeacherId"));
        assertTrue(script.contains("/refresh-contacts"));
        assertTrue(script.contains("item.childPhone"));
        assertTrue(script.contains("Только информация"));
        assertTrue(script.contains("/acknowledge"));
        assertTrue(script.contains("additionalTeacherIds"));
        assertTrue(script.contains("/generate"));
        assertTrue(script.contains("/release"));
        assertTrue(script.contains("/scan"));
        assertTrue(auth.contains("'/probe-orders.html': 'DOCUMENTS_PROBE_ORDERS'"));
        assertTrue(admin.contains("key: 'DOCUMENTS_PROBE_ORDERS'"));
    }

    @Test
    void homePageContainsMonthWeekDayCalendarForReleasedOrders() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));
        String calendar = Files.readString(Path.of("src/main/resources/static/home-calendar.js"));

        assertTrue(index.contains("id=\"home-probe-calendar\""));
        assertTrue(index.contains("data-calendar-view=\"month\""));
        assertTrue(index.contains("data-calendar-view=\"week\""));
        assertTrue(index.contains("data-calendar-view=\"day\""));
        assertTrue(calendar.contains("/api/probe-orders/calendar"));
        assertTrue(calendar.contains("event.classNames"));
        assertTrue(calendar.contains("event.companions"));
        assertTrue(index.contains("data-calendar-audience=\"DEPUTIES\""));
        assertTrue(index.contains("data-calendar-audience=\"ADMINISTRATION\""));
        assertTrue(index.contains("data-calendar-audience=\"FULL_ADMINISTRATION\""));
        assertTrue(index.contains("data-calendar-audience=\"BUILDING_HEADS\""));
        assertTrue(index.contains("data-calendar-audience=\"BUILDING\""));
        assertTrue(index.contains("data-calendar-audience=\"PERSONAL\""));
        assertTrue(index.contains("id=\"calendar-group-settings\""));
        assertTrue(index.contains("id=\"calendar-groups-dialog\""));
        assertTrue(calendar.contains("/api/calendar/audiences"));
        assertTrue(calendar.contains("event.participants"));
        assertTrue(calendar.contains("data-calendar-filter-building"));
        assertTrue(calendar.contains("data-calendar-filter-person"));
        assertTrue(index.contains("id=\"calendar-event-dialog\""));
        assertTrue(index.contains("id=\"calendar-event-duration\""));
        assertTrue(index.contains("id=\"calendar-own-settings-dialog\""));
        assertTrue(index.contains("id=\"calendar-list-dialog\""));
        assertTrue(calendar.contains("/api/calendar/events"));
        assertTrue(calendar.contains("/response"));
        assertTrue(calendar.contains("data-calendar-response=\"ACCEPTED\""));
        assertTrue(calendar.contains("Ожидается ответ"));
        assertTrue(calendar.contains("item.fullName"));
        assertTrue(calendar.contains("/api/calendar/settings"));
        assertTrue(calendar.contains("/api/calendar/lists"));
    }

    @Test
    void studentAndTeacherCardsExposeReleasedEventHistory() throws Exception {
        String teacherPage = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        String teacherScript = Files.readString(Path.of("src/main/resources/static/teachers.js"));
        String studentScript = Files.readString(Path.of("src/main/resources/static/vsoko-summary.js"));

        assertTrue(teacherPage.contains("Сопровождение мероприятий"));
        assertTrue(teacherScript.contains("/probe-events"));
        assertTrue(studentScript.contains("Участие в мероприятиях по выпущенным приказам"));
        assertTrue(studentScript.contains("data.probeEvents"));
    }
}
