package org.school;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.config.EnvFileLoader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class Tariffication implements CommandLineRunner {

    private final AppConfig appConfig;

    public static void main(String[] args) {
        EnvFileLoader.loadDotEnvIfExists();
        SpringApplication.run(Tariffication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Path outputDir = Path.of(appConfig.getOutputDirectory());
        Files.createDirectories(outputDir);

        if (appConfig.isLegacyModeEnabled()) {
            Path downloadDir = Path.of(appConfig.getDownloadDirectory());
            Files.createDirectories(downloadDir);
            log.warn("Запущен legacy-режим обработки файлов (LEGACY_MODE_ENABLED=true)");
        }

        log.info("Приложение запущено в API+Frontend режиме без Google Sheets. Используйте / и /api/*.");
    }
}
