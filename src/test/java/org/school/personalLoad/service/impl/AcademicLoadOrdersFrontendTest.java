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

    private String resource(String path) throws Exception {
        try (var input = AcademicLoadOrdersFrontendTest.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
