package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicLoadOrdersFrontendTest {

    @Test
    void loadOrdersPageProvidesCreationAndHistoryUi() throws Exception {
        String html = resource("/static/load-orders.html");
        String js = resource("/static/load-orders.js");
        String auth = resource("/static/auth.js");

        assertThat(html).contains("Приказы нагрузки", "CURRICULUM_APPROVAL", "LOAD_APPROVAL",
                "История приказов", "Сформировать и сохранить приказ");
        assertThat(js).contains("/api/load-orders", "sourceItemCount");
        assertThat(auth).contains("'/load-orders.html': 'LOAD'", "label: 'Приказы нагрузки'");
    }

    @Test
    void loadSectionMenusContainLoadOrdersLinkWithoutWaitingForJavascript() throws Exception {
        String[] pages = {
                "buildings.html", "classes.html", "curriculum.html", "load.html",
                "people-load.html", "load-issues.html", "load-statistics.html", "rates.html",
                "settings.html", "subjects.html", "mesh.html", "master-fot.html", "load-orders.html"
        };

        for (String page : pages) {
            assertThat(resource("/static/" + page))
                    .as("top menu in %s", page)
                    .contains("href=\"/load-orders.html\"", ">Приказы нагрузки</a>");
        }
    }

    @Test
    void semesterLoadRowsKeepIndependentTeacherPeriods() throws Exception {
        String loadJs = resource("/static/load.js");

        assertThat(loadJs)
                .contains("const periodToken = highSchoolUnifiedSubject(row) ? \"YEAR\" : rowStudyPeriod(row);")
                .contains("h2From: `${yearTo}-01-11`")
                .doesNotContain("h2From: `${yearTo}-01-01`");
    }

    private String resource(String path) throws Exception {
        try (var input = AcademicLoadOrdersFrontendTest.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
