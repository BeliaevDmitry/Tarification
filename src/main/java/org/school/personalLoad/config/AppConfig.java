package org.school.personalLoad.config;

import java.util.Set;

public class AppConfig {
    // Google Sheets URLs
    public static final String TARIFFICATION_SHEETS_URL =
            "https://docs.google.com/spreadsheets/d/1_2XDnInfHUKfj8jrzyU7EtzQz9G2oUTRZ-ALz1cePfU/export?format=xlsx";

    public static final String PRACTICUM_SHEETS_URL =
            "https://docs.google.com/spreadsheets/d/1CgxahrURqJw79TtINoEsgfyZoVMO4NKuQxhk0NwDOHg/export?format=xlsx";

    // File names
    public static final String TARIFFICATION_FILE_NAME = "Нагрузка 1 полугодие автоскачанный " +
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm"));

    public static final String PRACTICUM_FILE_NAME = "ЕГЭ 2026 автоскачанный";

    // Directories
    public static final String DOWNLOAD_DIRECTORY = "C:\\Users\\dimah\\Desktop\\для расчёта тарификации\\выгрузки нагрузки\\";
    public static final String OUTPUT_DIRECTORY =   "C:\\Users\\dimah\\Desktop\\для расчёта тарификации\\отчёт\\";

    public static final String OFFLINE_FILES_DIRECTORY =
            "C:\\Users\\dimah\\Desktop\\для расчёта тарификации\\журнал на 27.09.2025\\";
    public static final String EXPELLED_FILE_PATH =
            "C:\\Users\\dimah\\Desktop\\для расчёта тарификации\\Учащиеся. Только отчисленные на 03.10.2025.xlsx";

    // Output file names
    public static final String TARIFFICATION_OUTPUT = "Тарификация" +
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm")) + ".xlsx";
    public static final String PRACTICUM_OUTPUT = "контингент практикумы.xlsx";

    // Database
    public static final String DB_URL = "jdbc:postgresql://localhost:5432/tariffication_db";
    public static final String DB_USER = "tarif_user";
    public static final String DB_PASSWORD = "tarif_password";

    // Helper methods
    public static String getTarifficationOutputPath() {
        return OUTPUT_DIRECTORY + TARIFFICATION_OUTPUT;
    }

    public static String getPracticumOutputPath() {
        return OUTPUT_DIRECTORY + PRACTICUM_OUTPUT;
    }

    public static String getDownloadPath(String fileName) {
        return DOWNLOAD_DIRECTORY + fileName;
    }

    // Helper method для получения пути к офлайн папке
    public static String getOfflineFilesPath() {
        return OFFLINE_FILES_DIRECTORY;
    }
    public static String getExpelledFilePath() {
        return EXPELLED_FILE_PATH;
    }

    // список исключений для тарификации
    public static Set<String> EXCLUDED_TEACHERS = Set.of(
            "Сухомлинова Вера Борисовна"


    );
}