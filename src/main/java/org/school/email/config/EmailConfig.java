package org.school.email.config;

public class EmailConfig {
    // Настройки почтового сервера
    public static final String SMTP_HOST = "smtp.gmail.com";
    public static final int SMTP_PORT = 587;
    public static final String EMAIL_USERNAME = "ваш.email@gmail.com";
    public static final String EMAIL_PASSWORD = "ваш-пароль-приложения";

    // Настройки по умолчанию
    public static final boolean USE_SSL = false;
    public static final boolean USE_TLS = true;

    // Получатели по умолчанию
    public static final String[] DEFAULT_RECIPIENTS = {
            "recipient1@example.com",
            "recipient2@example.com"
    };

    // Тема письма по умолчанию
    public static final String DEFAULT_SUBJECT = "Тарификация - автоматическая отправка";
}