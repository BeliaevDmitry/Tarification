package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MckoFrontendResilienceTest {

    @Test
    void certificateRegistryRemainsAvailableWhenLoadOverviewFails() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/teachers.js"));

        assertTrue(script.contains("mckoCertificates = await api(\"/api/mcko/certificates?mode=all\")"));
        assertTrue(script.contains("Нагрузка временно недоступна"));
        assertTrue(script.contains("Сертификаты МЦКО загружены, но проверка по текущей нагрузке временно недоступна"));
        assertTrue(script.contains("Не удалось сохранить соответствие МЦКО"));
    }
}
