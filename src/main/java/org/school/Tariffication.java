package org.school;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.config.EnvFileLoader;
import org.school.personalLoad.controller.TarifficationController;
import org.school.personalLoad.service.DownloadService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class Tariffication implements CommandLineRunner {

    private final DownloadService downloadService;
    private final TarifficationController controller;
    private final AppConfig appConfig;

    public static void main(String[] args) {
        EnvFileLoader.loadDotEnvIfExists();
        SpringApplication.run(Tariffication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!appConfig.isRunBatchOnStartup()) {
            log.info("Batch-режим отключен (app.run-batch-on-startup=false). Приложение запущено как API-сервис.");
            return;
        }

        Path downloadDir = Path.of(appConfig.getDownloadDirectory());
        Path outputDir = Path.of(appConfig.getOutputDirectory());
        Files.createDirectories(downloadDir);
        Files.createDirectories(outputDir);

        String inputPath = downloadService.downloadFile(
                appConfig.getSheetsUrl(),
                appConfig.getTarifficationFileName(),
                appConfig.getDownloadDirectory()
        );

        controller.processTariffication(inputPath, appConfig.getTarifficationOutputPath());
        log.info("Обработка тарификации завершена");
    }
}
