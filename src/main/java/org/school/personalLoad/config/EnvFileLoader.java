package org.school.personalLoad.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class EnvFileLoader {

    private EnvFileLoader() {
    }

    public static void loadDotEnvIfExists() {
        Path dotEnvPath = Path.of(".env");
        if (!Files.exists(dotEnvPath)) {
            log.info("Файл .env не найден, используются переменные окружения/системные свойства");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(dotEnvPath);
            int loaded = 0;
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }

                int separator = line.indexOf('=');
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();

                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (key.isEmpty()) {
                    continue;
                }

                // Приоритет у реально заданных переменных окружения
                if (System.getenv(key) == null || System.getenv(key).isBlank()) {
                    System.setProperty(key, value);
                    loaded++;
                }
            }

            log.info("Загружено {} значений из .env в системные свойства", loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать .env", e);
        }
    }
}
