package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApplicationTimeFrontendTest {
    @Test
    void headerShowsApplicationTimeAndAdminCanPersistIt() throws Exception {
        String admin = Files.readString(Path.of("src/main/resources/static/admin.html"));
        String adminScript = Files.readString(Path.of("src/main/resources/static/admin.js"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertThat(admin).contains("id=\"admin-tab-time-btn\"", "data-admin-tab=\"time\"",
                "id=\"admin-time-value\"", "Сохранить время");
        assertThat(adminScript).contains("/api/admin/time", "currentDateTime", "loadAdminTime");
        assertThat(auth).contains("id=\"header-server-time\"", "/api/application-time",
                "window.setInterval(renderApplicationClock, 1000)");
    }
}
