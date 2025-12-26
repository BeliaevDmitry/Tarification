package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.school.analizJournal.config.JournalConfig;
import org.school.personalLoad.service.DownloadService;
import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.service.impl.DownloadServiceImpl;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.school.analizJournal.config.JournalConfig.PRACTICUM_OUTPUT;
public class AnalizPracticumNewOnlineMESH {

    public static final String OUTPUT_DIRECTORY_PR = "C:\\Users\\dimah\\Yandex.Disk\\" +
            "1811\\ЕГЭ 2026\\";

    public static void main(String[] args) {
        try {
            String outputFilePath = OUTPUT_DIRECTORY_PR+PRACTICUM_OUTPUT;

            // Скачиваем файлы из МЭШ с авторизацией
            List<String> practicumFilePaths = downloadMosRuFiles();
            System.out.println("Все файлы из МЭШ успешно скачаны");

            // Скачиваем основной файл из Google Sheets
            DownloadService downloadService = new DownloadServiceImpl();
            String mainFilePath = downloadService.downloadFile(
                    AppConfig.PRACTICUM_SHEETS_URL,
                    AppConfig.PRACTICUM_FILE_NAME
            );
            System.out.println("Файл из Google Sheets успешно скачан: " + mainFilePath);

            // Обработка файлов
            processExcelFiles(practicumFilePaths, mainFilePath, outputFilePath);

            System.out.println("Файлы успешно обработаны. Результат сохранен в: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Неожиданная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> downloadMosRuFiles() throws IOException {
        List<String> downloadedFiles = new ArrayList<>();
        String cookie = getCookie();
        String manualFolder = "C:\\Users\\dimah\\Yandex.Disk\\1811\\ЕГЭ 2026\\журнал практикумов";

        // Создаем папку для ручного скачивания, если ее нет
        File folder = new File(manualFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        System.out.println("Получен cookie, начинаю скачивание файлов...");

        // 1. Пытаемся скачать основной файл автоматически
        String mainFilePath = JournalConfig.getMeshFilePath(JournalConfig.MESH_MAIN_FILE);
        boolean mainDownloaded = false;

        try {
            if (cookie != null && !cookie.isEmpty()) {
                String mainUrl = JournalConfig.getMainMeshUrl();
                System.out.println("Скачиваю основной файл...");
                String mainFile = tryDownloadFile(mainUrl, mainFilePath, cookie);
                downloadedFiles.add(mainFile);
                mainDownloaded = true;
                System.out.println("✅ Основной файл скачан автоматически");
            }
        } catch (IOException e) {
            System.out.println("⚠️ Не удалось скачать основной файл автоматически: " + e.getMessage());
        }

        // 2. Пытаемся скачать дополнительные файлы автоматически
        List<Integer> failedAdditionalDownloads = new ArrayList<>();

        if (cookie != null && !cookie.isEmpty()) {
            for (int i = 0; i < JournalConfig.MESH_ADDITIONAL_GROUP_IDS.length; i++) {
                try {
                    String additionalUrl = JournalConfig.getAdditionalMeshUrl(JournalConfig.MESH_ADDITIONAL_GROUP_IDS[i]);
                    String additionalFilePath = JournalConfig.getExtraMeshFilePath(i + 1);

                    System.out.println("Скачиваю дополнительный файл " + (i + 1) + "...");
                    String additionalFile = tryDownloadFile(additionalUrl, additionalFilePath, cookie);
                    downloadedFiles.add(additionalFile);
                    System.out.println("✅ Дополнительный файл " + (i + 1) + " скачан автоматически");
                } catch (IOException e) {
                    System.out.println("⚠️ Не удалось скачать дополнительный файл " + (i + 1) + " автоматически: " + e.getMessage());
                    failedAdditionalDownloads.add(i + 1);
                }
            }
        }

        // 3. Если какие-то файлы не скачались автоматически, предлагаем ручное скачивание
        if (!mainDownloaded || !failedAdditionalDownloads.isEmpty()) {
            handleManualDownload(mainDownloaded, failedAdditionalDownloads);

            // 4. Ищем все файлы в папке ручного скачивания
            System.out.println("\n📁 Ищу файлы в папке: " + manualFolder);
            File[] files = folder.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"));

            if (files != null && files.length > 0) {
                System.out.println("Найдено файлов: " + files.length);
                for (File file : files) {
                    downloadedFiles.add(file.getAbsolutePath());
                    System.out.println("✅ Добавлен файл: " + file.getName());
                }
            } else {
                System.out.println("⚠️ В папке не найдено Excel-файлов");
            }
        }

        // 5. Проверяем, есть ли скачанные файлы
        if (downloadedFiles.isEmpty()) {
            throw new IOException("Не удалось получить ни одного файла. Проверьте ручную папку: " + manualFolder);
        }

        System.out.println("\n📊 Итого получено файлов: " + downloadedFiles.size());
        return downloadedFiles;
    }

    private static void handleManualDownload(boolean mainDownloaded, List<Integer> failedAdditional) {
        String manualFolder = "C:\\Users\\dimah\\Yandex.Disk\\1811\\ЕГЭ 2026\\журнал практикумов";

        System.out.println("\n" + "=".repeat(80));
        System.out.println("ТРЕБУЕТСЯ РУЧНОЕ СКАЧИВАНИЕ ФАЙЛОВ");
        System.out.println("=".repeat(80));

        int totalFilesNeeded = (mainDownloaded ? 0 : 1) + failedAdditional.size();
        System.out.println("\nНеобходимо скачать " + totalFilesNeeded + " файл(ов) вручную");

        // Генерируем ссылки для ручного скачивания
        List<String> urls = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();

        if (!mainDownloaded) {
            String mainUrl = JournalConfig.getMainMeshUrl();
            urls.add(mainUrl);
            descriptions.add("Основной файл (все группы)");
        }

        for (int num : failedAdditional) {
            String additionalUrl = JournalConfig.getAdditionalMeshUrl(JournalConfig.MESH_ADDITIONAL_GROUP_IDS[num - 1]);
            urls.add(additionalUrl);
            descriptions.add("Дополнительный файл " + num);
        }

        // Выводим инструкцию
        System.out.println("\nИнструкция по скачиванию:");
        System.out.println("1. Откройте следующие ссылки в браузере:");
        for (int i = 0; i < urls.size(); i++) {
            System.out.println("   " + (i + 1) + ". " + descriptions.get(i));
            System.out.println("      Ссылка: " + urls.get(i));
        }

        System.out.println("\n2. Для каждой ссылки:");
        System.out.println("   - Авторизуйтесь в МЭШ (если потребуется)");
        System.out.println("   - Скачайте файл Excel");
        System.out.println("   - Сохраните файл в папку: " + manualFolder);

        System.out.println("\n3. Требования к файлам:");
        System.out.println("   - Формат: Excel (.xlsx или .xls)");
        System.out.println("   - Можно сохранять с любыми именами");
        System.out.println("   - Главное - все файлы должны быть в указанной папке");

        System.out.println("\n4. После скачивания всех файлов вернитесь в программу");

        waitForUserConfirmation(manualFolder);
    }

    private static void waitForUserConfirmation(String folderPath) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n" + "-".repeat(80));
            System.out.println("Выберите действие:");
            System.out.println("1. Я скачал все файлы, продолжить");
            System.out.println("2. Показать файлы в папке сейчас");
            System.out.println("3. Открыть папку в проводнике");
            System.out.println("4. Показать ссылки еще раз");
            System.out.print("Ваш выбор (1-4): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    System.out.println("Продолжаем работу...");
                    return;

                case "2":
                    showFilesInFolder(folderPath);
                    break;

                case "3":
                    try {
                        Desktop.getDesktop().open(new File(folderPath));
                        System.out.println("Папка открыта в проводнике");
                    } catch (Exception e) {
                        System.out.println("Не удалось открыть папку: " + e.getMessage());
                    }
                    break;

                case "4":
                    // Можно повторно показать инструкцию
                    System.out.println("\nОсновные требования:");
                    System.out.println("- Скачайте все необходимые Excel-файлы");
                    System.out.println("- Сохраните в папку: " + folderPath);
                    System.out.println("- Имена файлов могут быть любыми");
                    break;

                default:
                    System.out.println("Неверный выбор. Введите 1, 2, 3 или 4");
            }
        }
    }

    private static void showFilesInFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            System.out.println("Папка не существует: " + folderPath);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("Папка пуста: " + folderPath);
            return;
        }

        System.out.println("\nСодержимое папки '" + folderPath + "':");
        System.out.println("-".repeat(80));

        int excelCount = 0;
        for (File file : files) {
            String type = file.isDirectory() ? "[Папка]" : "[Файл]";
            String excelMark = "";

            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                    excelMark = " ✓ Excel";
                    excelCount++;
                }
            }

            System.out.printf("%s %-40s %,.0f байт%s%n",
                    type, file.getName(), file.length(), excelMark);
        }

        System.out.println("-".repeat(80));
        System.out.println("Всего файлов: " + files.length);
        System.out.println("Excel-файлов: " + excelCount);
    }
    // УПРОЩЕННЫЙ МЕТОД ПОЛУЧЕНИЯ COOKIE
    private static String getCookie() {
        System.out.println("=== ПОЛУЧЕНИЕ COOKIE ДЛЯ МЭШ ===");

        // 1. Пробуем прочитать сохраненный cookie
        try {
            if (Files.exists(Path.of(JournalConfig.COOKIE_FILE_PATH))) {
                String savedCookie = Files.readString(Path.of(JournalConfig.COOKIE_FILE_PATH)).trim();
                System.out.println("Найден сохраненный cookie, длина: " + savedCookie.length() + " символов");
                return savedCookie;
            }
        } catch (IOException e) {
            System.out.println("⚠️ Ошибка чтения cookie файла: " + e.getMessage());
        }

        // 2. Запрашиваем ручной ввод
        System.out.println("\n📋 ДЛЯ РАБОТЫ ПРОГРАММЫ НУЖЕН COOKIE ОТ МЭШ:");
        System.out.println("1. Откройте Chrome и перейдите на: " + JournalConfig.MESH_BASE_URL);
        System.out.println("2. Убедитесь, что вы авторизованы (должен открыться электронный дневник)");
        System.out.println("3. Нажмите F12 → вкладка Console (Консоль)");
        System.out.println("4. Введите команду: document.cookie");
        System.out.println("5. Скопируйте ВСЮ строку, которая появится");
        System.out.println("6. Вставьте её ниже и нажмите Enter\n");

        Scanner scanner = new Scanner(System.in);
        String cookie = null;

        while (cookie == null || cookie.trim().isEmpty()) {
            System.out.print("📝 Введите cookie: ");
            cookie = scanner.nextLine().trim();

            if (cookie.isEmpty()) {
                System.out.println("❌ Cookie не может быть пустым. Попробуйте еще раз.");
            }
        }

        // Сохраняем cookie для будущих запусков
        saveCookieToFile(cookie);
        return cookie;
    }

    // ПРОБУЕМ СКАЧАТЬ ФАЙЛ С COOKIE
    private static String tryDownloadFile(String fileUrl, String savePath, String cookie) throws IOException {
        for (int attempt = 1; attempt <= JournalConfig.MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            System.out.println("Попытка скачивания " + attempt + " из " + JournalConfig.MAX_DOWNLOAD_ATTEMPTS + "...");

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
                connection.setRequestProperty("Cookie", cookie);
                connection.setRequestProperty("User-Agent", JournalConfig.USER_AGENT);
                connection.setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                connection.setConnectTimeout(JournalConfig.CONNECTION_TIMEOUT);
                connection.setReadTimeout(JournalConfig.READ_TIMEOUT);
                connection.setInstanceFollowRedirects(true);

                System.out.println("Отправляю запрос к: " + fileUrl);
                int responseCode = connection.getResponseCode();
                System.out.println("Код ответа: " + responseCode);

                if (responseCode == 200) {
                    try (InputStream in = connection.getInputStream()) {
                        long fileSize = Files.copy(in, Path.of(savePath), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("✅ Файл успешно скачан: " + savePath + " (" + fileSize + " байт)");
                        return savePath;
                    }
                } else if (responseCode == 403 || responseCode == 401) {
                    System.out.println("❌ Доступ запрещен. Cookie невалиден.");
                    // Удаляем невалидный cookie файл
                    Files.deleteIfExists(Path.of(JournalConfig.COOKIE_FILE_PATH));
                    throw new IOException("Cookie невалиден. Удалите файл cookie и попробуйте снова.");
                } else {
                    System.out.println("⚠️ Неожиданный код ответа: " + responseCode);
                    if (attempt < JournalConfig.MAX_DOWNLOAD_ATTEMPTS) {
                        Thread.sleep(JournalConfig.RETRY_DELAY_MS);
                    }
                }

                connection.disconnect();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано ожидание", e);
            } catch (IOException e) {
                System.out.println("Ошибка при попытке " + attempt + ": " + e.getMessage());
                if (attempt == JournalConfig.MAX_DOWNLOAD_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(JournalConfig.RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Прервано ожидание", ie);
                }
            }
        }

        throw new IOException("Не удалось скачать файл после " + JournalConfig.MAX_DOWNLOAD_ATTEMPTS + " попыток");
    }

    // СОХРАНЕНИЕ COOKIE В ФАЙЛ
    private static void saveCookieToFile(String cookie) {
        try {
            Files.writeString(Path.of(JournalConfig.COOKIE_FILE_PATH), cookie);
            System.out.println("✅ Cookie сохранен в файл: " + JournalConfig.COOKIE_FILE_PATH);
        } catch (IOException e) {
            System.out.println("⚠️ Не удалось сохранить cookie: " + e.getMessage());
        }
    }

    // ОБРАБОТКА НЕСКОЛЬКИХ ФАЙЛОВ
    public static void processExcelFiles(List<String> practicumPaths, String mainPath, String outputPath) throws IOException {
        // 1. Читаем данные из всех файлов практикума
        List<Student> practicumStudents = new ArrayList<>();
        for (String filePath : practicumPaths) {
            try {
                List<Student> studentsFromFile = readPracticumData(filePath);
                practicumStudents.addAll(studentsFromFile);
                System.out.println("Обработан файл: " + filePath + " (" + studentsFromFile.size() + " студентов)");
            } catch (IOException e) {
                System.out.println("⚠️ Ошибка при обработке файла " + filePath + ": " + e.getMessage());
            }
        }

        System.out.println("Всего студентов из практикумов: " + practicumStudents.size());

        // 2. Читаем данные из основного файла
        List<MainStudent> mainStudents = readMainData(mainPath);
        System.out.println("Студентов в основном файле: " + mainStudents.size());

        // 3. Создаем карту для быстрого поиска по ФИО и группе
        Map<String, Boolean> mainStudentsMap = createMainStudentsMap(mainStudents);

        // 4. Создаем карту для поиска класса по ФИО
        Map<String, String> classByLastNameMap = createClassByLastNameMap(mainStudents);

        // 5. Сравниваем данные и создаем результат
        compareAndCreateOutput(practicumStudents, mainStudents, mainStudentsMap, classByLastNameMap, outputPath);
    }

    private static List<Student> readPracticumData(String filePath) throws IOException {
        List<Student> students = new ArrayList<>();
        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = filePath.endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis);
        }

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String groupName = getCellValue(sheet, 40, 20, evaluator);
            String processedGroupName = processGroupName(groupName);
            System.out.println("Лист " + (i + 1) + ": " + sheet.getSheetName() + ", группа: " + processedGroupName);
            processColumnB(sheet, processedGroupName, students, evaluator);
        }
        workbook.close();
        return students;
    }

    private static List<MainStudent> readMainData(String filePath) throws IOException {
        List<MainStudent> students = new ArrayList<>();
        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = filePath.endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis);
        }

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Sheet sheet = workbook.getSheet("Свод вертикальный");
        if (sheet == null) sheet = workbook.getSheetAt(0);

        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            String lastName = getCellValue(row, 0, evaluator);
            String practicumGroup = getCellValue(row, 6, evaluator);
            String studentClass = getCellValue(row, 1, evaluator);

            // Пропускаем строки с пустыми, нулевыми значениями
            if (practicumGroup == null ||
                    practicumGroup.trim().isEmpty() ||
                    "0".equals(practicumGroup.trim()) ||
                    "группа практикума".equals(practicumGroup.trim()) ) {
                continue;
            }


            String processedGroup = processGroupName(practicumGroup);
            if (lastName != null && !lastName.trim().isEmpty()) {
                students.add(new MainStudent(lastName, studentClass, processedGroup));
            }
        }
        workbook.close();
        return students;
    }

    private static Map<String, Boolean> createMainStudentsMap(List<MainStudent> mainStudents) {
        Map<String, Boolean> map = new HashMap<>();
        for (MainStudent student : mainStudents) {
            String key = student.getLastName().trim().toLowerCase() + "|" + student.getPracticumGroup().toLowerCase();
            map.put(key, true);
        }
        return map;
    }

    private static Map<String, String> createClassByLastNameMap(List<MainStudent> mainStudents) {
        Map<String, String> map = new HashMap<>();
        for (MainStudent student : mainStudents) {
            String key = student.getLastName().trim().toLowerCase();
            map.put(key, student.getStudentClass());
        }
        return map;
    }

    private static void compareAndCreateOutput(List<Student> practicumStudents,
                                               List<MainStudent> mainStudents,
                                               Map<String, Boolean> mainStudentsMap,
                                               Map<String, String> classByLastNameMap,
                                               String outputPath) throws IOException {
        Workbook outputWorkbook = new XSSFWorkbook();
        Sheet comparisonSheet = outputWorkbook.createSheet("выгрузка МЭШ");
        createComparisonSheet(comparisonSheet, practicumStudents, mainStudentsMap, classByLastNameMap);

        Sheet mainDataSheet = outputWorkbook.createSheet("Выгрузка таблицы ЕГЭ 2026");
        createMainDataSheet(mainDataSheet, mainStudents, practicumStudents);

        // Добавляем лист с информацией о файлах
        Sheet infoSheet = outputWorkbook.createSheet("Информация");
        createInfoSheet(infoSheet, practicumStudents.size(), mainStudents.size());

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            outputWorkbook.write(fos);
        }
        outputWorkbook.close();
    }

    private static void createInfoSheet(Sheet sheet, int practicumCount, int mainCount) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Параметр");
        headerRow.createCell(1).setCellValue("Значение");

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Студентов из практикумов");
        row1.createCell(1).setCellValue(practicumCount);

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("Студентов в основном файле");
        row2.createCell(1).setCellValue(mainCount);

        for (int i = 0; i < 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createComparisonSheet(Sheet sheet, List<Student> practicumStudents,
                                              Map<String, Boolean> mainStudentsMap,
                                              Map<String, String> classByLastNameMap) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ФИО");
        headerRow.createCell(1).setCellValue("Класс");
        headerRow.createCell(2).setCellValue("Группа практикума");
        headerRow.createCell(3).setCellValue("Наличие в таблице ЕГЭ");

        CellStyle errorStyle = sheet.getWorkbook().createCellStyle();
        errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (Student student : practicumStudents) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getName());

            String studentClass = classByLastNameMap.getOrDefault(student.getName().trim().toLowerCase(), "Не найден");
            row.createCell(1).setCellValue(studentClass);
            row.createCell(2).setCellValue(student.getGroup());

            String key = student.getName().trim().toLowerCase() + "|" + student.getGroup().toLowerCase();
            boolean existsInMain = mainStudentsMap.containsKey(key);

            Cell statusCell = row.createCell(3);
            statusCell.setCellValue(existsInMain ? "Есть" : "ОШИБКА");

            if (!existsInMain) statusCell.setCellStyle(errorStyle);
        }

        // Включаем автофильтр - пользователь сможет легко отфильтровать вручную
        sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, 3));

        // Сортируем так, чтобы ошибки были вверху (опционально)
        sheet.createFreezePane(0, 1); // Замораживаем заголовок

        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
    }

    private static void createMainDataSheet(Sheet sheet, List<MainStudent> mainStudents, List<Student> practicumStudents) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Класс");
        headerRow.createCell(1).setCellValue("ФИО");
        headerRow.createCell(2).setCellValue("Группа практикума");
        headerRow.createCell(3).setCellValue("Совпадение с наличием в МЭШ");

        Map<String, Boolean> practicumMap = new HashMap<>();
        for (Student student : practicumStudents) {
            String key = student.getName().trim().toLowerCase() + "|" + student.getGroup().toLowerCase();
            practicumMap.put(key, true);
        }

        CellStyle warningStyle = sheet.getWorkbook().createCellStyle();
        warningStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (MainStudent student : mainStudents) {
            if ("0".equals(student.getPracticumGroup())) continue;

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getStudentClass());
            row.createCell(1).setCellValue(student.getLastName());
            row.createCell(2).setCellValue(student.getPracticumGroup());

            String key = student.getLastName().trim().toLowerCase() + "|" + student.getPracticumGroup().toLowerCase();
            boolean hasMatch = practicumMap.containsKey(key);

            Cell matchCell = row.createCell(3);
            matchCell.setCellValue(hasMatch ? "Есть" : "Нет");

            if (!hasMatch) matchCell.setCellStyle(warningStyle);
        }

        // Включаем автофильтр для всей таблицы
        sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, 3));

        // Замораживаем заголовок для удобства прокрутки
        sheet.createFreezePane(0, 1);

        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
    }

    private static String processGroupName(String originalGroupName) {
        if (originalGroupName == null || originalGroupName.trim().isEmpty()) return "Неизвестная группа";
        String result = originalGroupName.split(" ")[0];
        return result.startsWith("11") ? result.substring(2) : result;
    }

    private static String getCellValue(Sheet sheet, int rowNum, int colNum, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowNum);
        return row == null ? "" : getCellValue(row, colNum, evaluator);
    }

    private static String getCellValue(Row row, int colNum, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colNum);
        if (cell == null) return "";

        CellValue cellValue = evaluator.evaluate(cell);
        if (cellValue == null) return "";

        switch (cellValue.getCellType()) {
            case STRING: return cellValue.getStringValue().trim();
            case NUMERIC: return String.valueOf((int) cellValue.getNumberValue());
            case BOOLEAN: return String.valueOf(cellValue.getBooleanValue());
            default: return "";
        }
    }

    private static void processColumnB(Sheet sheet, String groupName, List<Student> students, FormulaEvaluator evaluator) {
        for (int rowNum = 1; rowNum <= 45; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            String studentName = getCellValue(row, 1, evaluator);
            if (studentName != null && !studentName.trim().isEmpty() && !studentName.equals("#ОШИБКА")) {
                students.add(new Student(studentName, groupName));
            }
        }
    }

    static class Student {
        private String name, group;
        public Student(String name, String group) { this.name = name; this.group = group; }
        public String getName() { return name; }
        public String getGroup() { return group; }
    }

    static class MainStudent {
        private String lastName, studentClass, practicumGroup;
        public MainStudent(String lastName, String studentClass, String practicumGroup) {
            this.lastName = lastName; this.studentClass = studentClass; this.practicumGroup = practicumGroup;
        }
        public String getLastName() { return lastName; }
        public String getStudentClass() { return studentClass; }
        public String getPracticumGroup() { return practicumGroup; }
    }
}