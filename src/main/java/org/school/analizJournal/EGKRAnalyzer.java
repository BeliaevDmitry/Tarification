package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EGKRAnalyzer {

    // Список возможных предметов (в порядке проверки)
    private static final String[] SUBJECTS = {
            "Русский язык",
            "Математика профильная",
            "Математика базовая",
            "Физика",
            "Химия",
            "Информатика и ИКТ (КЕГЭ)",
            "Биология",
            "История",
            "География",
            "Английский язык",
            "Немецкий язык",
            "Французский язык",
            "Обществознание",
            "Испанский язык",
            "Китайский язык",
            "Литература"
    };

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Анализ журналов МЭШ ===");
        System.out.println("Выберите школу:");
        System.out.println("1 - Школа 1811");
        System.out.println("2 - Школа 7");
        System.out.print("Введите номер (1 или 2): ");

        int schoolChoice = scanner.nextInt();
        scanner.nextLine(); // очистка буфера

        String folderPath = "";

        String outputPath = "";

        switch (schoolChoice) {
            case 1:
                folderPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\ЕГЭ 2026\\ЕГКР декабрь";
                outputPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\ЕГЭ 2026\\ЕГКР_сводка.xlsx";
                System.out.println("Выбрана школа 1811");
                break;
            case 2:

                folderPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\ЕГЭ 2026\\ЕГКР декабрь";
                outputPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\ЕГЭ 2026\\ЕГКР_сводка.xlsx";
                System.out.println("Выбрана школа 7");
                break;
        }

        try {
            List<StudentResult> results = analyzeFiles(folderPath);
            createOutputExcel(results, outputPath);
            System.out.println("Обработка завершена. Создан файл: " + outputPath);
            System.out.println("Обработано записей: " + results.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<StudentResult> analyzeFiles(String folderPath) throws Exception {
        List<StudentResult> results = new ArrayList<>();
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new FileNotFoundException("Папка не найдена: " + folderPath);
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (files == null || files.length == 0) {
            throw new FileNotFoundException("В папке нет Excel-файлов");
        }

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);

                // Определяем предмет, дату и месяц из ячейки A5
                Row row5 = sheet.getRow(4);
                if (row5 == null) continue;

                Cell cellA5 = row5.getCell(0);
                if (cellA5 == null) continue;

                String subjectLine = cellA5.getStringCellValue();
                System.out.println("Обрабатываем: " + file.getName() + " - " + subjectLine);

                // Извлекаем предмет (простая проверка по списку)
                String subject = extractSubjectSimple(subjectLine);
                if (subject.equals("Неизвестный")) {
                    System.out.println("  ВНИМАНИЕ: не удалось определить предмет!");
                }

                // Извлекаем дату (последние символы формата ГГГГ.ММ.ДД)
                String dateStr = extractDateFromLine(subjectLine);
                String month = extractMonthFromDate(dateStr);

                System.out.println("  Предмет: " + subject + ", Дата: " + dateStr + ", Месяц: " + month);

                // Находим строку с заголовками таблицы
                int dataStartRow = findDataStartRow(sheet);
                if (dataStartRow == -1) continue;

                // Определяем индексы колонок
                Map<String, Integer> columnIndexes = findColumnIndexes(sheet, dataStartRow);
                if (columnIndexes.isEmpty()) continue;

                // Обрабатываем строки с данными
                processDataRows(sheet, dataStartRow, columnIndexes, month, subject, results);

            } catch (Exception e) {
                System.err.println("Ошибка в файле " + file.getName() + ": " + e.getMessage());
            }
        }

        return results;
    }

    private static String extractSubjectSimple(String line) {
        if (line == null || line.isEmpty()) {
            return "Неизвестный";
        }

        // Просто проверяем наличие каждого предмета в строке
        for (String subject : SUBJECTS) {
            if (line.contains(subject)) {
                return subject;
            }
        }

        return "Неизвестный";
    }

    private static String extractDateFromLine(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }

        // Ищем дату в формате ГГГГ.ММ.ДД (последние символы)
        Pattern pattern = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2}");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group();
        }

        return "";
    }

    private static String extractMonthFromDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || !dateStr.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) {
            return "Неизвестно";
        }

        try {
            String[] parts = dateStr.split("\\.");
            if (parts.length >= 2) {
                int monthNum = Integer.parseInt(parts[1]);
                if (monthNum >= 1 && monthNum <= 12) {
                    String[] months = {
                            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
                    };
                    return months[monthNum - 1];
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибку
        }

        return "Неизвестно";
    }

    private static int findDataStartRow(Sheet sheet) {
        // Ищем строку с заголовком "№ п/п"
        for (int i = 0; i <= 20; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(0);
                if (cell != null && "№ п/п".equals(cell.toString().trim())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Map<String, Integer> findColumnIndexes(Sheet sheet, int headerRow) {
        Map<String, Integer> indexes = new HashMap<>();
        Row header = sheet.getRow(headerRow);

        if (header == null) return indexes;

        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String headerText = cell.toString().trim();
                if (!headerText.isEmpty()) {
                    indexes.put(headerText, i);
                }
            }
        }

        return indexes;
    }

    private static void processDataRows(Sheet sheet, int startRow,
                                        Map<String, Integer> indexes,
                                        String month, String subject,
                                        List<StudentResult> results) {

        // Определяем индексы нужных колонок
        Integer classIndex = indexes.get("Класс");
        Integer lastNameIndex = indexes.get("Фамилия");
        Integer firstNameIndex = indexes.get("Имя");
        Integer middleNameIndex = indexes.get("Отчество");
        Integer primaryScoreIndex = findPrimaryScoreIndex(indexes);

        // Если нет стандартных названий ФИО, пробуем найти объединенную колонку
        if (lastNameIndex == null) {
            lastNameIndex = indexes.get("Фамилия Имя Отчество");
        }

        // Проверяем, есть ли достаточно данных
        if (classIndex == null || primaryScoreIndex == null ||
                (lastNameIndex == null && (indexes.get("Фамилия") == null ||
                        indexes.get("Имя") == null))) {
            System.out.println("  Не найдены необходимые колонки: Класс=" + classIndex +
                    ", Первичный балл=" + primaryScoreIndex +
                    ", Фамилия=" + lastNameIndex);
            return;
        }

        // Обрабатываем строки данных
        for (int i = startRow + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Пропускаем строки с итогами
            Cell firstCell = row.getCell(0);
            if (firstCell != null) {
                String cellValue = firstCell.toString().toLowerCase();
                if (cellValue.contains("всего") || cellValue.contains("итог")) {
                    continue;
                }
            }

            // Получаем класс
            String className = getCellValue(row.getCell(classIndex));
            if (className == null || className.isEmpty()) continue;

            // Получаем ФИО
            String lastName = "";
            String firstName = "";
            String middleName = "";

            if (lastNameIndex != null && indexes.get("Фамилия") != null &&
                    indexes.get("Имя") != null && indexes.get("Отчество") != null) {
                // Стандартный формат с отдельными колонками
                lastName = getCellValue(row.getCell(indexes.get("Фамилия")));
                firstName = getCellValue(row.getCell(indexes.get("Имя")));
                middleName = getCellValue(row.getCell(indexes.get("Отчество")));
            } else if (lastNameIndex != null) {
                // Альтернативный формат: ФИО в одной колонке
                String fullName = getCellValue(row.getCell(lastNameIndex));
                if (fullName != null && !fullName.isEmpty()) {
                    String[] nameParts = fullName.split("\\s+", 3);
                    if (nameParts.length >= 1) lastName = nameParts[0];
                    if (nameParts.length >= 2) firstName = nameParts[1];
                    if (nameParts.length >= 3) middleName = nameParts[2];
                }
            }

            if (lastName.isEmpty() || firstName.isEmpty()) continue;

            // Получаем первичный балл
            String primaryScoreStr = getCellValue(row.getCell(primaryScoreIndex));
            double primaryScore = 0;

            try {
                if (primaryScoreStr != null && !primaryScoreStr.isEmpty()) {
                    primaryScore = Double.parseDouble(primaryScoreStr.replace(",", "."));
                }
            } catch (NumberFormatException e) {
                // Пробуем получить из ячейки как число
                Cell scoreCell = row.getCell(primaryScoreIndex);
                if (scoreCell != null && scoreCell.getCellType() == CellType.NUMERIC) {
                    primaryScore = scoreCell.getNumericCellValue();
                }
            }

            // Получаем процент выполнения (если есть)
            double percent = 0;
            Integer percentIndex = indexes.get("% выполнения");
            if (percentIndex != null) {
                String percentStr = getCellValue(row.getCell(percentIndex));
                try {
                    if (percentStr != null && !percentStr.isEmpty()) {
                        percent = Double.parseDouble(percentStr.replace(",", "."));
                    }
                } catch (NumberFormatException e) {
                    // Игнорируем ошибку преобразования
                }
            }

            // Создаем объект результата
            StudentResult result = new StudentResult(
                    month,
                    className,
                    lastName,
                    firstName,
                    middleName,
                    subject,
                    primaryScore,
                    percent
            );

            results.add(result);
        }
    }

    private static Integer findPrimaryScoreIndex(Map<String, Integer> indexes) {
        // Ищем колонку с первичным баллом по различным возможным названиям
        String[] possibleNames = {
                "Первичный балл",
                "Первичный балл письменной части",
                "Первичный балл устной части",
                "Первичный балл письменной части ",
                "Первичный балл устной части "
        };

        for (String name : possibleNames) {
            if (indexes.containsKey(name)) {
                return indexes.get(name);
            }
        }

        // Если не нашли по названию, ищем по позиции (обычно предпоследняя или последняя колонка)
        for (String key : indexes.keySet()) {
            if (key.contains("Первичный") || key.contains("балл")) {
                return indexes.get(key);
            }
        }

        return null;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy.MM.dd").format(cell.getDateCellValue());
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.valueOf((int) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            default:
                return "";
        }
    }

    private static void createOutputExcel(List<StudentResult> results, String outputPath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Результаты ЕГКР");

        // Создаем стили
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Создаем заголовки (добавляем столбец "ФИО")
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Месяц", "Класс", "Фамилия", "Имя", "Отчество", "ФИО", "Предмет", "Первичный балл", "% выполнения"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Заполняем данные
        int rowNum = 1;
        for (StudentResult result : results) {
            Row row = sheet.createRow(rowNum++);

            // Создаем полное ФИО
            String fullName = result.getLastName() + " " + result.getFirstName();
            if (result.getMiddleName() != null && !result.getMiddleName().isEmpty()) {
                fullName += " " + result.getMiddleName();
            }

            row.createCell(0).setCellValue(result.getMonth());
            row.createCell(1).setCellValue(result.getClassName());
            row.createCell(2).setCellValue(result.getLastName());
            row.createCell(3).setCellValue(result.getFirstName());
            row.createCell(4).setCellValue(result.getMiddleName());
            row.createCell(5).setCellValue(fullName); // Новый столбец ФИО
            row.createCell(6).setCellValue(result.getSubject());
            row.createCell(7).setCellValue(result.getPrimaryScore());
            row.createCell(8).setCellValue(result.getPercent());
        }

        // Автоподбор ширины колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Сохраняем файл
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }

        workbook.close();
    }

    static class StudentResult {
        private String month;
        private String className;
        private String lastName;
        private String firstName;
        private String middleName;
        private String subject;
        private double primaryScore;
        private double percent;

        public StudentResult(String month, String className, String lastName,
                             String firstName, String middleName, String subject,
                             double primaryScore, double percent) {
            this.month = month;
            this.className = className;
            this.lastName = lastName;
            this.firstName = firstName;
            this.middleName = middleName;
            this.subject = subject;
            this.primaryScore = primaryScore;
            this.percent = percent;
        }

        // Геттеры
        public String getMonth() { return month; }
        public String getClassName() { return className; }
        public String getLastName() { return lastName; }
        public String getFirstName() { return firstName; }
        public String getMiddleName() { return middleName; }
        public String getSubject() { return subject; }
        public double getPrimaryScore() { return primaryScore; }
        public double getPercent() { return percent; }
    }
}