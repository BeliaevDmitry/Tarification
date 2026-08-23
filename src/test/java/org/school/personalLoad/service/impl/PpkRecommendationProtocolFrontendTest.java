package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PpkRecommendationProtocolFrontendTest {

    @Test
    void recommendationProtocolIsVisibleAndSharesAppointmentRoadmapStage() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/ovz.html"));
        String script = Files.readString(Path.of("src/main/resources/static/ovz.js"));

        assertThat(page).contains("value=\"RECOMMENDATION_SUPPORT\">Сопровождение по рекомендации ЦМПК");
        assertThat(script).contains("p.protocolType === 'RECOMMENDATION_SUPPORT'");
        assertThat(script).contains("if (activeType !== 'IOM' && defaults.protocolType)");
        assertThat(script).contains("Сопровождение по рекомендации");
    }
}
