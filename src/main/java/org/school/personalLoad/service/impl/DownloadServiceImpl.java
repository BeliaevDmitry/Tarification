package org.school.personalLoad.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.service.DownloadService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class DownloadServiceImpl implements DownloadService {

    @Override
    public String downloadFile(String fileUrl, String fileName, String downloadDirectory) {
        String downloadPath = downloadDirectory + fileName + ".xlsx";
        log.info("Начинаю скачивание файла: {}", fileUrl);

        try (InputStream in = new URL(fileUrl).openStream()) {
            Files.copy(in, Path.of(downloadPath), StandardCopyOption.REPLACE_EXISTING);
            log.info("Файл успешно скачан: {}", downloadPath);
            return downloadPath;

        } catch (IOException e) {
            log.error("Ошибка при скачивании файла", e);
            throw new RuntimeException("Не удалось скачать файл", e);
        }
    }

    @Override
    public String downloadFile(String fileUrl, String fileName) {
        return downloadFile(fileUrl, fileName, "./data/downloads/");
    }
}
