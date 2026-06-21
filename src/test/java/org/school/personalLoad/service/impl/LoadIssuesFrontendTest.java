package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadIssuesFrontendTest {

    @Test
    void issuesDefaultToUnresolvedAndCommentsAutoSave() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/load-issues.html"));
        String js = Files.readString(Path.of("src/main/resources/static/load-issues.js"));

        assertTrue(html.contains("id=\"load-issues-status-filter\""));
        assertTrue(html.indexOf("value=\"unresolved\"") < html.indexOf("value=\"all\""));
        assertFalse(html.contains("data-save-comment"));
        assertTrue(js.contains("setTimeout(() => saveCommentNow(tr, comment), 700)"));
        assertTrue(js.contains("target=\"_blank\""));
    }

    @Test
    void issueLinksAreHandledByLoadAndCurriculumPages() throws Exception {
        String issuesJs = Files.readString(Path.of("src/main/resources/static/load-issues.js"));
        String loadJs = Files.readString(Path.of("src/main/resources/static/load.js"));
        String curriculumJs = Files.readString(Path.of("src/main/resources/static/curriculum.js"));

        assertTrue(issuesJs.contains("params.set(\"issueClass\""));
        assertTrue(issuesJs.contains("params.set(\"issueSubject\""));
        assertTrue(loadJs.contains("focusIssueNavigationTarget()"));
        assertTrue(loadJs.contains("!issueBuildingOption && !canEditSelectedBuildingLoad()"));
        assertTrue(loadJs.contains("buildingGroupCode(row.value || row.code) === buildingGroupCode(requestedIssueBuilding)"));
        assertTrue(loadJs.contains("normalizeClassName(row?.className) === normalizeClassName(issueNavigation.className)"));
        assertTrue(curriculumJs.contains("data-summary-class"));
    }
}
