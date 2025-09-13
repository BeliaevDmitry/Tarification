package org.school.personalLoad.service;

/**
 * Интерфейс сервиса для скачивания файлов
 */
public interface DownloadService {

    /**
     * Скачивает файл по указанному URL и сохраняет его с заданным именем в указанную директорию
     *
     * @param fileUrl URL файла для скачивания
     * @param fileName имя файла для сохранения (без расширения)
     * @param downloadDirectory директория для сохранения файла
     * @return полный путь к скачанному файлу
     */
    String downloadFile(String fileUrl, String fileName, String downloadDirectory);

    /**
     * Скачивает файл по указанному URL и сохраняет его с заданным именем в директорию по умолчанию
     *
     * @param fileUrl URL файла для скачивания
     * @param fileName имя файла для сохранения (без расширения)
     * @return полный путь к скачанному файлу
     */
    String downloadFile(String fileUrl, String fileName);
}