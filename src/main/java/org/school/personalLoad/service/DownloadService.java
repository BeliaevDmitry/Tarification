package org.school.personalLoad.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadService {

    private final String googleSheetsUrl;
    private final String nameFileDownload;

    public DownloadService(String googleSheetsUrl, String nameFileDownload) {
        this.googleSheetsUrl = googleSheetsUrl;
        this.nameFileDownload = nameFileDownload;
    }

    public String downloadFile() throws IOException {
        // Прямая ссылка для скачивания Google Sheets в формате Excel

        // Путь для сохранения скачанного файла
        String downloadPath = "C:\\Users\\dimah\\Downloads\\" + nameFileDownload + ".xlsx";

        System.out.println("Начинаю скачивание файла...");

        try (InputStream in = new URL(googleSheetsUrl).openStream()) {
            Files.copy(in, Path.of(downloadPath), StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Файл успешно скачан: " + downloadPath);
        return downloadPath;
    }
}