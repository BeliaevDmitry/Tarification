package org.school;

import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.controller.TarifficationController;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.DownloadService;


public class Tariffication {
    public static void main(String[] args) {
        try {
            DownloadService downloadService = new DownloadService();
            TarifficationController controller = new TarifficationController();

            // Скачиваем файл
            String inputPath = downloadService.downloadFile(
                    AppConfig.TARIFFICATION_SHEETS_URL,
                    AppConfig.TARIFFICATION_FILE_NAME
            );

            // Обрабатываем данные
            controller.processTariffication(inputPath, AppConfig.getTarifficationOutputPath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            HibernateConfig.shutdown();
        }
    }
}