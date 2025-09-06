package org.school.analizJournal.config;

public class JournalConfig {
    // URL МЭШ
    public static final String MESH_BASE_URL = "https://dnevnik.mos.ru";

    // URL для экспорта журналов
    public static final String MESH_JOURNAL_EXPORT_URL = MESH_BASE_URL + "/export/journal.xlsx";

    // Файлы для скачивания из МЭШ
    public static final String[] MESH_GROUP_IDS = {
            "12017593", "12017583", "12017633", "12017606", "12017081", "12018737",
            "12015950", "12015994", "12016050", "12018248", "12016533", "12016475",
            "12012769", "12012808", "12012792", "12013410", "12013560", "12013562",
            "12013338", "12013454", "12020969", "12020896", "12021067", "12021052",
            "12014448", "12014456", "12014599", "12014626", "12014640", "12014708",
            "12014713", "12014731", "12094222", "12015276", "12015231", "12015489", "12094228"
    };

    public static final String[] MESH_ADDITIONAL_GROUP_IDS = {
            "12017068", "12015933", "12014553", "12013377"
    };

    // Имена файлов
    public static final String MESH_MAIN_FILE = "mos_ru_journal_main.xlsx";
    public static final String MESH_EXTRA_FILE_PREFIX = "mos_ru_journal_extra";

    // Cookie файл
    public static final String COOKIE_FILE_PATH = "C:\\Users\\dimah\\Downloads\\mos_ru_cookie.txt";

    // Настройки подключения
    public static final int CONNECTION_TIMEOUT = 30000;
    public static final int READ_TIMEOUT = 30000;
    public static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    public static final int RETRY_DELAY_MS = 2000;

    // User-Agent для запросов
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Методы для генерации URL
    public static String getMainMeshUrl() {
        return MESH_JOURNAL_EXPORT_URL + "?group_ids=" + String.join(",", MESH_GROUP_IDS);
    }

    public static String getAdditionalMeshUrl(String groupId) {
        return MESH_JOURNAL_EXPORT_URL + "?group_ids=" + groupId +
                "&extended=false&start_at=2025-09-01T00:00:00.000Z&stop_at=2025-10-03T00:00:00.000Z";
    }

    public static String getMeshFilePath(String fileName) {
        return "C:\\Users\\dimah\\Downloads\\" + fileName;
    }

    public static String getExtraMeshFilePath(int index) {
        return getMeshFilePath(MESH_EXTRA_FILE_PREFIX + index + ".xlsx");
    }

    // Метод для получения всех group_ids основной группы
    public static String getMainGroupIds() {
        return String.join(",", MESH_GROUP_IDS);
    }

    // Метод для получения URL с параметрами
    public static String buildMeshUrl(String groupIds, boolean extended, String startDate, String endDate) {
        StringBuilder url = new StringBuilder(MESH_JOURNAL_EXPORT_URL);
        url.append("?group_ids=").append(groupIds);

        if (!extended) {
            url.append("&extended=false");
        }

        if (startDate != null) {
            url.append("&start_at=").append(startDate);
        }

        if (endDate != null) {
            url.append("&stop_at=").append(endDate);
        }

        return url.toString();
    }
}