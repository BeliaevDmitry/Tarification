package org.school;

import org.school.personalLoad.controller.TarifficationController;
import org.school.personalLoad.service.DownloadService;

import java.io.IOException;

public class Tariffication {
    public static void main(String[] args) throws IOException {
        //String inputPath = "C:\\Users\\dimah\\Desktop\\1 полугодие нагрузка 2025-2026.xlsx";

        String googleSheetsUrl = "https://docs.google.com/spreadsheets/d/1CgxahrURqJw79TtINoEsgfyZoVMO4NKuQxhk0NwDOHg/export?format=xlsx";
        String nameFileDownload = "Нагрузка 1 полугодие автоскачанный";
        // Создаем экземпляр сервиса
        DownloadService downloadService = new DownloadService(googleSheetsUrl, nameFileDownload);

        // Вызываем метод для скачивания
        String inputPath = downloadService.downloadFile();

        String outputPath = "C:\\Users\\dimah\\Desktop\\Тарификация.xlsx";

        TarifficationController controller = new TarifficationController();
        controller.processTariffication(inputPath, outputPath);
    }
}