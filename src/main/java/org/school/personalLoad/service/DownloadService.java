package org.school.personalLoad.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadService {

    // Конструктор по умолчанию
    public DownloadService() {
    }

    // Универсальный метод для скачивания
    public String downloadFile(String fileUrl, String fileName, String downloadDirectory) {
        String downloadPath = downloadDirectory + fileName + ".xlsx";

        System.out.println("Начинаю скачивание файла: " + fileUrl);

        try (InputStream in = new URL(fileUrl).openStream()) {
            Files.copy(in, Path.of(downloadPath), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Файл успешно скачан: " + downloadPath);
            return downloadPath;

        } catch (IOException e) {
            System.err.println("Ошибка при скачивании файла: " + e.getMessage());
            throw new RuntimeException("Не удалось скачать файл", e);
        }
    }

    // Перегруженный метод с директорией по умолчанию
    public String downloadFile(String fileUrl, String fileName) {
        return downloadFile(fileUrl, fileName, "C:\\Users\\dimah\\Downloads\\");
    }
}