package org.school.personalLoad.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionConfigurationTest {

    @Test
    void authorizationIsKeptForOneDay() throws Exception {
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addFirst);

        ServerProperties properties = Binder.get(environment)
                .bind("server", ServerProperties.class)
                .orElseThrow(() -> new AssertionError("Настройки server не загружены"));

        assertEquals(Duration.ofHours(24), properties.getServlet().getSession().getTimeout());
        assertEquals(Duration.ofHours(24), properties.getServlet().getSession().getCookie().getMaxAge());
    }
}
