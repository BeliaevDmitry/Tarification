package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFrontendTest {

    @Test
    void passwordCanBeShownAndHiddenFromTheLoginForm() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/login.html"));
        String script = Files.readString(Path.of("src/main/resources/static/login.js"));
        String styles = Files.readString(Path.of("src/main/resources/static/styles.css"));

        assertTrue(page.contains("id=\"login-password-toggle\""));
        assertTrue(page.contains("aria-label=\"Показать пароль\""));
        assertTrue(page.contains("type=\"button\""));
        assertTrue(script.contains("setPasswordVisible"));
        assertTrue(script.contains("visible ? 'text' : 'password'"));
        assertTrue(script.contains("'Скрыть пароль' : 'Показать пароль'"));
        assertTrue(styles.contains(".login-password-toggle"));
    }
}
