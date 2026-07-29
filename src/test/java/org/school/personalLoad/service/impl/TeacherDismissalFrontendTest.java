package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherDismissalFrontendTest {

    @Test
    void personnelRowsStayCompactAndEmployeeCardContainsContractsAndDismissalActions() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        String js = Files.readString(Path.of("src/main/resources/static/teachers.js"));
        String css = Files.readString(Path.of("src/main/resources/static/styles.css"));
        String filter = Files.readString(Path.of("src/main/java/org/school/personalLoad/config/auth/AuthFilter.java"));

        assertTrue(html.contains("id=\"teacher-card-dialog\""));
        assertTrue(html.contains("Часы внутри ставки"));
        assertTrue(html.contains("id=\"teacher-contract-in-rate-status\""));
        assertTrue(html.contains("Определяются автоматически по должности"));
        assertTrue(html.contains("id=\"teacher-card-cancel-plan\""));
        assertTrue(html.contains("id=\"accept-teacher-btn\""));
        assertTrue(html.contains("id=\"auto-assign-buildings-btn\""));
        assertTrue(html.contains("id=\"teacher-card-initials\""));
        assertTrue(html.contains("id=\"teacher-card-dative\""));
        assertTrue(html.contains("id=\"teacher-personal-section\""));
        assertTrue(html.contains("id=\"teacher-card-data-sheet\""));
        assertTrue(!html.contains("id=\"teacher-duties-create\""));
        assertTrue(js.contains("class=\"teacher-row-actions\""));
        assertTrue(js.contains("additionalDutiesSummary"));
        assertTrue(js.contains("/api/teachers/auto-assign-buildings"));
        assertTrue(js.contains("/api/teachers/accept"));
        assertTrue(js.contains("/api/hr-documents/contracts?teacherId="));
        assertTrue(js.contains("function ruleForPosition(position"));
        assertTrue(js.contains("loadHoursMayBeIncludedInRate: Boolean(inRateRule)"));
        assertTrue(js.contains("loadInRateDocumentLabel: null"));
        assertTrue(js.contains("cancel-plan-dismiss"));
        assertTrue(css.contains("#teachers-main-panel .teachers-table td > input"));
        assertTrue(css.contains("height: 42px"));
        assertTrue(filter.contains("plan-dismiss|cancel-plan-dismiss|dismiss|restore"));
    }
}
