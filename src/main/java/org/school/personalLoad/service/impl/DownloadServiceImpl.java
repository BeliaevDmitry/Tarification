package org.school.personalLoad.service.impl;

import org.school.personalLoad.service.DownloadService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadServiceImpl implements DownloadService {

    // Конструктор по умолчанию
    public DownloadServiceImpl() {
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

     /*

    // Универсальный метод для скачивания
    public String downloadFile(String fileUrl, String fileName, String downloadDirectory) {
        String downloadPath = downloadDirectory + fileName + ".xlsx";

        System.out.println("Начинаю скачивание файла: " + fileUrl);

        // Задержка перед началом
        try {
            System.out.println("Пауза 5 секунд перед началом скачивания...");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Пауза прервана: " + e.getMessage());
        }

        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Устанавливаем таймауты
            connection.setConnectTimeout(30000); // 30 секунд на соединение
            connection.setReadTimeout(60000);    // 60 секунд на чтение
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, Path.of(downloadPath), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Файл успешно скачан: " + downloadPath);
                return downloadPath;
            }

        } catch (IOException e) {
            System.err.println("Ошибка при скачивании файла: " + e.getMessage());
            throw new RuntimeException("Не удалось скачать файл", e);
        }
    }
*/
    // Перегруженный метод с директорией по умолчанию
    public String downloadFile(String fileUrl, String fileName) {
        return downloadFile(fileUrl, fileName, "C:\\Users\\dimah\\Downloads\\");
    }
}