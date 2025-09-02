package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.school.personalLoad.service.DownloadService;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

public class AnalizPracticumNewOnlineMESH {

    private static final String COOKIE_FILE_PATH = "C:\\Users\\dimah\\Downloads\\mos_ru_cookie.txt";

    public static void main(String[] args) {
        try {
            String outputFilePath = "C:\\Users\\dimah\\Desktop\\Ошибки в практикумах.xlsx";

            // Скачиваем файл из МЭШ с авторизацией
            String practicumFilePath = downloadMosRuFile();
            System.out.println("Файл из МЭШ успешно скачан: " + practicumFilePath);

            // Скачиваем основной файл из Google Sheets через DownloadService
            String googleSheetsUrl = "https://docs.google.com/spreadsheets/d/1CgxahrURqJw79TtINoEsgfyZoVMO4NKuQxhk0NwDOHg/export?format=xlsx";
            String nameFileDownload = "ЕГЭ 2026 автоскачанный";

            DownloadService downloadService = new DownloadService(googleSheetsUrl, nameFileDownload);
            String mainFilePath = downloadService.downloadFile();
            System.out.println("Файл из Google Sheets успешно скачан: " + mainFilePath);

            // Обработка файлов
            processExcelFiles(practicumFilePath, mainFilePath, outputFilePath);

            System.out.println("Файлы успешно обработаны. Результат сохранен в: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String downloadMosRuFile() throws IOException {
        String mosRuUrl = "https://dnevnik.mos.ru/export/journal.xlsx?group_ids=12017593,12017583,12017606,12017081,12018737,12015950,12015994,12018248,12016533,12016475,12012769,12012808,12012792,12013410,12013560,12013562,12013338,12013454,12020896,12021067,12021052,12014448,12014599,12014626,12014640,12014708,12014713,12014731,12094222,12015276,12015231,12015489";
        String downloadPath = "C:\\Users\\dimah\\Downloads\\mos_ru_journal.xlsx";

        System.out.println("Начинаю скачивание файла из МЭШ...");

        // Получаем куки для авторизации
        String cookie = getMosRuAuthCookie();
        if (cookie == null || cookie.isEmpty()) {
            throw new IOException("Не удалось получить авторизацию в МЭШ");
        }

        // Скачиваем файл с авторизацией
        HttpURLConnection connection = (HttpURLConnection) new URL(mosRuUrl).openConnection();
        connection.setRequestProperty("Cookie", cookie);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, Path.of(downloadPath), StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Файл из МЭШ успешно скачан: " + downloadPath);
        return downloadPath;
    }

    private static String getMosRuAuthCookie() throws IOException {
        // Попробуем прочитать куки из файла (если уже авторизованы)
        try {
            if (Files.exists(Path.of(COOKIE_FILE_PATH))) {
                String savedCookie = Files.readString(Path.of(COOKIE_FILE_PATH));
                if (isCookieValid(savedCookie)) {
                    return savedCookie;
                }
            }
        } catch (IOException e) {
            System.out.println("Не удалось прочитать сохраненные куки: " + e.getMessage());
        }

        // Запрашиваем cookie у пользователя
        System.out.println("\n=== ИНСТРУКЦИЯ ДЛЯ РУЧНОЙ АВТОРИЗАЦИИ ===");
        System.out.println("1. Откройте браузер и войдите в МЭШ: https://dnevnik.mos.ru");
        System.out.println("2. После входа откройте инструменты разработчика (F12)");
        System.out.println("3. Перейдите во вкладку Network (Сеть)");
        System.out.println("4. Обновите страницу (F5)");
        System.out.println("5. Найдите любой запрос и скопируйте значение Cookie");
        System.out.println("6. Вставьте значение Cookie ниже:");

        Scanner scanner = new Scanner(System.in);
        System.out.print("\nВведите значение Cookie: ");
        String cookie = scanner.nextLine();

        // Сохраняем куки для будущего использования
        try {
            Files.writeString(Path.of(COOKIE_FILE_PATH), cookie);
        } catch (IOException e) {
            System.out.println("Не удалось сохранить куки: " + e.getMessage());
        }

        return cookie;
    }

    private static boolean isCookieValid(String cookie) {
        return cookie != null && cookie.contains("sessionid") && cookie.contains("csrftoken");
    }

    // Остальные методы без изменений
    public static void processExcelFiles(String practicumPath, String mainPath, String outputPath) throws IOException {
        // 1. Читаем данные из практикума (первый файл)
        List<Student> practicumStudents = readPracticumData(practicumPath);

        // 2. Читаем данные из основного файла
        List<MainStudent> mainStudents = readMainData(mainPath);

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
            if (filePath.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (filePath.endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IOException("Неподдерживаемый формат файла");
            }
        }

        // Создаем evaluator для вычисления формул
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        // Обрабатываем каждый лист
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);

            // Получаем название группы из ячейки U41
            String groupName = getCellValue(sheet, 40, 20, evaluator);
            String processedGroupName = processGroupName(groupName);

            // Обрабатываем столбец B, начиная с 3 строки
            processColumnB(sheet, processedGroupName, students, evaluator);
        }

        workbook.close();
        return students;
    }

    private static List<MainStudent> readMainData(String filePath) throws IOException {
        List<MainStudent> students = new ArrayList<>();

        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            if (filePath.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (filePath.endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IOException("Неподдерживаемый формат файла");
            }
        }

        // Создаем evaluator для вычисления формул
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        // Получаем лист "Свод вертикальный"
        Sheet sheet = workbook.getSheet("Свод вертикальный");
        if (sheet == null) {
            // Если лист не найден, попробуем найти другой возможный вариант названия
            sheet = workbook.getSheetAt(0); // берем первый лист
            System.out.println("Лист 'Свод вертикальный' не найден, используем первый лист: " + sheet.getSheetName());
        }

        // Обрабатываем данные, начиная со 2 строки (предполагаем, что 1 строка - заголовок)
        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }

            // Столбец A - Фамилия (получаем вычисленное значение)
            String lastName = getCellValue(row, 0, evaluator);
            // Столбец F - Группа практикума (получаем вычисленное значение)
            String practicumGroup = getCellValue(row, 5, evaluator);
            // Столбец B - Класс (получаем вычисленное значение)
            String studentClass = getCellValue(row, 1, evaluator);

            // Пропускаем если нет группы практикума или группа равна "0"
            if (practicumGroup == null || practicumGroup.trim().isEmpty() || "0".equals(practicumGroup.trim())) {
                continue;
            }

            // Обрабатываем название группы
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
            // Ключ: ФИО + группа
            String key = student.getLastName().trim().toLowerCase() + "|" + student.getPracticumGroup().toLowerCase();
            map.put(key, true);
        }
        return map;
    }

    private static Map<String, String> createClassByLastNameMap(List<MainStudent> mainStudents) {
        Map<String, String> map = new HashMap<>();
        for (MainStudent student : mainStudents) {
            // Ключ: ФИО в нижнем регистре, значение: класс
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

        // 1. Лист с результатами сравнения
        Sheet comparisonSheet = outputWorkbook.createSheet("Сравнение");
        createComparisonSheet(comparisonSheet, practicumStudents, mainStudentsMap, classByLastNameMap);

        // 2. Лист с основными данными + маркер совпадения (фильтруем группы с "0")
        Sheet mainDataSheet = outputWorkbook.createSheet("Основные данные");
        createMainDataSheet(mainDataSheet, mainStudents, practicumStudents);

        // Сохраняем файл
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            outputWorkbook.write(fos);
        }

        outputWorkbook.close();
    }

    private static void createComparisonSheet(Sheet sheet, List<Student> practicumStudents,
                                              Map<String, Boolean> mainStudentsMap,
                                              Map<String, String> classByLastNameMap) {
        // Заголовки
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ФИО");
        headerRow.createCell(1).setCellValue("Класс");
        headerRow.createCell(2).setCellValue("Группа практикума");
        headerRow.createCell(3).setCellValue("Наличие в основном списке");

        // Стиль для ошибок
        CellStyle errorStyle = sheet.getWorkbook().createCellStyle();
        errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (Student student : practicumStudents) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getName());

            // Получаем класс из основной таблиции по ФИО
            String studentClass = classByLastNameMap.get(student.getName().trim().toLowerCase());
            if (studentClass == null) {
                studentClass = "Не найден";
            }
            row.createCell(1).setCellValue(studentClass);

            row.createCell(2).setCellValue(student.getGroup());

            // Проверяем наличие в основном списке
            String key = student.getName().trim().toLowerCase() + "|" + student.getGroup().toLowerCase();
            boolean existsInMain = mainStudentsMap.containsKey(key);

            Cell statusCell = row.createCell(3);
            statusCell.setCellValue(existsInMain ? "Есть" : "ОШИБКА");

            // Подсвечиваем ошибки
            if (!existsInMain) {
                statusCell.setCellStyle(errorStyle);
            }
        }

        // Автоподбор ширины колонок
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createMainDataSheet(Sheet sheet, List<MainStudent> mainStudents, List<Student> practicumStudents) {
        // Заголовки
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Класс");
        headerRow.createCell(1).setCellValue("ФИО");
        headerRow.createCell(2).setCellValue("Группа практикума");
        headerRow.createCell(3).setCellValue("Совпадение с практикумом");

        // Создаем карту для быстрого поиска студентов из практикума
        Map<String, Boolean> practicumMap = new HashMap<>();
        for (Student student : practicumStudents) {
            String key = student.getName().trim().toLowerCase() + "|" + student.getGroup().toLowerCase();
            practicumMap.put(key, true);
        }

        // Стиль для предупреждений
        CellStyle warningStyle = sheet.getWorkbook().createCellStyle();
        warningStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (MainStudent student : mainStudents) {
            // Пропускаем строки где группа практикума равна "0"
            if ("0".equals(student.getPracticumGroup())) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getStudentClass());
            row.createCell(1).setCellValue(student.getLastName());
            row.createCell(2).setCellValue(student.getPracticumGroup());

            // Проверяем совпадение с практикумом
            String key = student.getLastName().trim().toLowerCase() + "|" + student.getPracticumGroup().toLowerCase();
            boolean hasMatch = practicumMap.containsKey(key);

            Cell matchCell = row.createCell(3);
            matchCell.setCellValue(hasMatch ? "Есть" : "Нет");

            // Подсвечиваем отсутствие совпадения
            if (!hasMatch) {
                matchCell.setCellStyle(warningStyle);
            }
        }

        // Автоподбор ширины колонок
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String processGroupName(String originalGroupName) {
        if (originalGroupName == null || originalGroupName.trim().isEmpty()) {
            return "Неизвестная группа";
        }

        // Удаляем все после первого пробела
        String result = originalGroupName.split(" ")[0];

        // Удаляем "11" в начале, если есть
        if (result.startsWith("11")) {
            result = result.substring(2);
        }

        return result;
    }

    private static String getCellValue(Sheet sheet, int rowNum, int colNum, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            return "";
        }
        return getCellValue(row, colNum, evaluator);
    }

    private static String getCellValue(Row row, int colNum, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colNum);
        if (cell == null) {
            return "";
        }

        // Вычисляем значение ячейки, включая формулы
        CellValue cellValue = evaluator.evaluate(cell);

        if (cellValue == null) {
            return "";
        }

        switch (cellValue.getCellType()) {
            case STRING:
                return cellValue.getStringValue().trim();
            case NUMERIC:
                return String.valueOf((int) cellValue.getNumberValue());
            case BOOLEAN:
                return String.valueOf(cellValue.getBooleanValue());
            case BLANK:
                return "";
            case ERROR:
                return "#ОШИБКА";
            default:
                return "";
        }
    }

    private static void processColumnB(Sheet sheet, String groupName, List<Student> students, FormulaEvaluator evaluator) {
        for (int rowNum = 2; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }

            Cell cell = row.getCell(1);
            if (cell == null) {
                continue;
            }

            // Получаем вычисленное значение ячейки
            String studentName = getCellValue(row, 1, evaluator);

            if (studentName != null && !studentName.trim().isEmpty() && !studentName.equals("#ОШИБКА")) {
                students.add(new Student(studentName, groupName));
            }
        }
    }

    // Классы для хранения данных
    static class Student {
        private String name;
        private String group;

        public Student(String name, String group) {
            this.name = name;
            this.group = group;
        }

        public String getName() { return name; }
        public String getGroup() { return group; }
    }

    static class MainStudent {
        private String lastName;
        private String studentClass;
        private String practicumGroup;

        public MainStudent(String lastName, String studentClass, String practicumGroup) {
            this.lastName = lastName;
            this.studentClass = studentClass;
            this.practicumGroup = practicumGroup;
        }

        public String getLastName() { return lastName; }
        public String getStudentClass() { return studentClass; }
        public String getPracticumGroup() { return practicumGroup; }
    }
}