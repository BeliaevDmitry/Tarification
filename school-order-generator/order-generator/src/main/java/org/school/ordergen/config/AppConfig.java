package org.school.ordergen.config;

public final class AppConfig {

    // Базовый путь к папке с данными
    public static final String DATA_PATH = "C:/Users/dimah/Yandex.Disk/ГБОУ №7/Воспитательная работа/Профпробы";

    // Имена файлов
    public static final String STUDENTS_FILE = "9 класс.xlsx";
    public static final String TEACHERS_FILE = "структура корпусов.xlsx";
    public static final String EVENTS_FOLDER = "C:/Users/dimah/Yandex.Disk/ГБОУ №7/" +
            "Воспитательная работа/Профпробы/экспорт";
    public static final String TEMPLATE_FILE = "prikaz_template.docx";

    // Полный путь к шаблону
    public static final String TEMPLATE_PATH = DATA_PATH + "/" + TEMPLATE_FILE;

    // Папка для выходных приказов
    public static final String OUTPUT_PATH = DATA_PATH + "/Приказы";

    // База данных – путь без кириллицы
    public static final String DB_PATH = "./data/processed_events";

    // Расписание
    public static final String SCHEDULER_CRON = "0 0 0 * * *";

    private AppConfig() {}
}