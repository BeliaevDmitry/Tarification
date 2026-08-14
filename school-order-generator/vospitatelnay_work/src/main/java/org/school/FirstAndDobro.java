package org.school;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class FirstAndDobro {

    // ----- Константы с путями -----
    private static final String PATH_STRUCTURE = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\БД основные\\структура корпусов.xlsx";
    private static final String PATH_CONTINGENT = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\БД основные\\Реестр контингента.xlsx";
    private static final String BASE_OUTPUT_DIR = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\Воспитательная работа\\Сбор id первых и добро";
    private static final String REPORTS_INPUT_DIR = BASE_OUTPUT_DIR + "\\Отчёты";
    private static final String SUMMARY_FILE = REPORTS_INPUT_DIR + "\\Сводный_отчет.xlsx";

    // Имена листов и колонок
    private static final String SHEET_STRUCTURE = "класс корпус";
    private static final String SHEET_CONTINGENT = "Контингент ГБОУ Школа № 7";
    private static final String COL_CLASS_STRUCT = "Класс";
    private static final String COL_TEACHER_FULL = "ФИО классного полное";
    private static final String COL_CORPS = "Корпус";

    // Цвет заливки для ячеек ID
    private static final short FILL_COLOR = IndexedColors.YELLOW.getIndex();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            printMenu();
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1":
                    try {
                        generateTemplates();
                    } catch (Exception e) {
                        System.err.println("Ошибка при генерации шаблонов: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "2":
                    try {
                        processReports();
                    } catch (Exception e) {
                        System.err.println("Ошибка при обработке отчётов: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case "3":
                    System.out.println("Выход из программы.");
                    return;
                default:
                    System.out.println("Неверный ввод. Пожалуйста, введите 1, 2 или 3.");
            }
            System.out.println(); // пустая строка для разделения
        }
    }

    private static void printMenu() {
        System.out.println("=== Меню ===");
        System.out.println("1. Сгенерировать шаблоны");
        System.out.println("2. Собрать отчёты");
        System.out.println("3. Выход");
        System.out.print("Выберите действие (1-3): ");
    }

    // -----------------------------------------------------------------------
    // 1. Генерация шаблонов
    // -----------------------------------------------------------------------
    private static void generateTemplates() throws IOException {
        List<ClassInfo> classInfos = readClassStructure();
        Map<String, List<Student>> studentsByClass = readContingent();

        for (ClassInfo ci : classInfos) {
            createTemplateForClass(ci, studentsByClass.getOrDefault(normalizeClassName(ci.className), Collections.emptyList()));
        }
        System.out.println("Шаблоны созданы.");
    }

    private static List<ClassInfo> readClassStructure() throws IOException {
        List<ClassInfo> list = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new File(PATH_STRUCTURE))) {
            Sheet sheet = wb.getSheet(SHEET_STRUCTURE);
            if (sheet == null) {
                throw new RuntimeException("Лист '" + SHEET_STRUCTURE + "' не найден в файле структуры.");
            }

            Row header = sheet.getRow(0);
            int idxClass = findColumnIndex(header, COL_CLASS_STRUCT);
            int idxTeacher = findColumnIndex(header, COL_TEACHER_FULL);
            int idxCorps = findColumnIndex(header, COL_CORPS);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String className = getCellString(row.getCell(idxClass));
                String teacherFull = getCellString(row.getCell(idxTeacher));
                String corps = getCellString(row.getCell(idxCorps));

                if (className.isEmpty() || teacherFull.isEmpty() || corps.isEmpty()) {
                    continue;
                }

                list.add(new ClassInfo(className, teacherFull, corps));
            }
        }
        return list;
    }

    private static Map<String, List<Student>> readContingent() throws IOException {
        Map<String, List<Student>> map = new HashMap<>();
        try (Workbook wb = WorkbookFactory.create(new File(PATH_CONTINGENT))) {
            Sheet sheet = wb.getSheet(SHEET_CONTINGENT);
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
                System.out.println("Лист '" + SHEET_CONTINGENT + "' не найден, используется первый лист: " + sheet.getSheetName());
            }

            // Заголовки в 3-й строке (индекс 2)
            Row headerRow = sheet.getRow(2);
            if (headerRow == null) {
                throw new RuntimeException("В файле контингента нет 3-й строки с заголовками.");
            }

            int idxName = findColumnIndex(headerRow, "ФИО", "Ф.И.О.", "Ученик", "Имя");
            int idxClass = findColumnIndex(headerRow, "Номер и буква класса", "Класс", "Класс обучения");

            if (idxName == -1 || idxClass == -1) {
                System.err.println("Не найдены необходимые колонки в 3-й строке. Фактические заголовки:");
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String val = getCellString(headerRow.getCell(i));
                    System.err.println("Колонка " + i + ": '" + val + "'");
                }
                throw new RuntimeException("Проверьте названия колонок в файле контингента (3-я строка).");
            }

            System.out.println("Найдены колонки: ФИО (индекс " + idxName + "), Класс (индекс " + idxClass + ")");

            // Данные начиная с 4-й строки (индекс 3)
            for (int r = 3; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String studentName = getCellString(row.getCell(idxName));
                String className = getCellString(row.getCell(idxClass));

                if (studentName.isEmpty() || className.isEmpty()) {
                    continue;
                }

                String normClass = normalizeClassName(className);
                map.computeIfAbsent(normClass, k -> new ArrayList<>())
                        .add(new Student(studentName, className));
            }
        }
        return map;
    }

    /** Создание одного файла-шаблона для класса */
    private static void createTemplateForClass(ClassInfo classInfo, List<Student> students) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Класс");

        // Стиль для жёлтой заливки ячеек ID
        CellStyle yellowStyle = wb.createCellStyle();
        yellowStyle.setFillForegroundColor(FILL_COLOR);
        yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Стиль для границ (применяем ко всем ячейкам)
        CellStyle borderedStyle = wb.createCellStyle();
        borderedStyle.setBorderTop(BorderStyle.THIN);
        borderedStyle.setBorderBottom(BorderStyle.THIN);
        borderedStyle.setBorderLeft(BorderStyle.THIN);
        borderedStyle.setBorderRight(BorderStyle.THIN);

        // Комбинированный стиль для ID: жёлтый + границы + текстовый формат
        CellStyle idStyle = wb.createCellStyle();
        idStyle.cloneStyleFrom(borderedStyle);
        idStyle.setFillForegroundColor(FILL_COLOR);
        idStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        // Формат текста (чтобы сохранялись ведущие нули)
        DataFormat format = wb.createDataFormat();
        idStyle.setDataFormat(format.getFormat("@")); // текстовый формат

        // Стиль для заголовков (жирный + границы)
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.cloneStyleFrom(borderedStyle);
        headerStyle.setFont(headerFont);

        // Заголовок
        String[] headers = {"№", "Класс", "ФИО", "ID движения 1", "ID добро РФ"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Данные учеников
        int rowNum = 1;
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            Row row = sheet.createRow(rowNum++);

            // №
            Cell numCell = row.createCell(0);
            numCell.setCellValue(i + 1);
            numCell.setCellStyle(borderedStyle);

            // Класс
            Cell classCell = row.createCell(1);
            classCell.setCellValue(s.originalClass);
            classCell.setCellStyle(borderedStyle);

            // ФИО
            Cell nameCell = row.createCell(2);
            nameCell.setCellValue(s.name);
            nameCell.setCellStyle(borderedStyle);

            // ID движения 1
            Cell id1Cell = row.createCell(3);
            id1Cell.setCellStyle(idStyle); // жёлтый + границы + текст

            // ID добро РФ
            Cell id2Cell = row.createCell(4);
            id2Cell.setCellStyle(idStyle);
        }

        // Установка проверки данных (только 8 цифр) для колонок ID (индексы 3 и 4)
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper((XSSFSheet) sheet);
        // Формула проверки: длина 8 и число (можно использовать ЕЧИСЛО + ДЛСТР)
        // Для русской версии Excel может потребоваться другой синтаксис, но POI генерирует формулу на английском
        String formula = "AND(ISNUMBER(--A1),LEN(A1)=8)"; // заглушка, заменим на правильную ссылку
        // Создадим проверку для диапазона ячеек ID (начиная со строки 2 до последней)
        int lastRow = students.size(); // если students пусто, то строки данных нет, но диапазон можно создать пустой
        if (lastRow > 0) {
            for (int col : new int[]{3, 4}) {
                CellRangeAddressList addressList = new CellRangeAddressList(1, lastRow, col, col);
                DataValidationConstraint constraint = dvHelper.createCustomConstraint("AND(ISNUMBER(--INDIRECT(\"RC\",0)),LEN(INDIRECT(\"RC\",0))=8)");
                DataValidation validation = dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Ошибка ввода", "Необходимо ввести ровно 8 цифр.");
                sheet.addValidationData(validation);
            }
        }

        // Авторазмер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Формируем имя файла: класс_ФИО классного руководителя
        String fileName = sanitizeFileName(classInfo.className + "_" + classInfo.teacherFull) + ".xlsx";
        String corpsDir = BASE_OUTPUT_DIR + "\\" + sanitizeFileName(classInfo.corps);
        Files.createDirectories(Paths.get(corpsDir));

        String fullPath = corpsDir + "\\" + fileName;
        try (FileOutputStream out = new FileOutputStream(fullPath)) {
            wb.write(out);
        }
        System.out.println("Создан: " + fullPath);
    }

    // -----------------------------------------------------------------------
    // 2. Обработка отчётов
    // -----------------------------------------------------------------------
    private static void processReports() throws IOException {
        File reportsDir = new File(REPORTS_INPUT_DIR);
        if (!reportsDir.exists() || !reportsDir.isDirectory()) {
            System.out.println("Папка с отчётами не найдена: " + REPORTS_INPUT_DIR);
            return;
        }

        File[] reportFiles = reportsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".xlsx") && !name.equals(new File(SUMMARY_FILE).getName()));
        if (reportFiles == null || reportFiles.length == 0) {
            System.out.println("Нет файлов отчётов для обработки.");
            return;
        }

        List<MergedRow> allRows = new ArrayList<>();
        Map<String, ClassStats> statsMap = new HashMap<>();

        for (File file : reportFiles) {
            System.out.println("Обработка: " + file.getName());
            processReportFile(file, allRows, statsMap);
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet dataSheet = wb.createSheet("Данные");
            createDataSheet(dataSheet, allRows);

            Sheet summarySheet = wb.createSheet("Сводка");
            createSummarySheet(summarySheet, statsMap);

            try (FileOutputStream out = new FileOutputStream(SUMMARY_FILE)) {
                wb.write(out);
            }
            System.out.println("Сводный отчёт сохранён: " + SUMMARY_FILE);
        }
    }

    private static void processReportFile(File file, List<MergedRow> allRows, Map<String, ClassStats> statsMap) {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return;

            int idxNum = findColumnIndex(header, "№");
            int idxClass = findColumnIndex(header, "Класс");
            int idxName = findColumnIndex(header, "ФИО");
            int idxId1 = findColumnIndex(header, "ID движения 1");
            int idxId2 = findColumnIndex(header, "ID добро РФ");

            if (idxNum == -1 || idxClass == -1 || idxName == -1 || idxId1 == -1 || idxId2 == -1) {
                System.out.println("  Пропущен (неверный заголовок): " + file.getName());
                return;
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String className = getCellString(row.getCell(idxClass));
                if (className.isEmpty()) continue;

                String studentName = getCellString(row.getCell(idxName));
                String numStr = getCellString(row.getCell(idxNum));
                String id1 = getCellString(row.getCell(idxId1));
                String id2 = getCellString(row.getCell(idxId2));

                boolean id1Valid = isValidId(id1);
                boolean id2Valid = isValidId(id2);
                boolean id1Filled = !id1.isEmpty();
                boolean id2Filled = !id2.isEmpty();

                ClassStats stats = statsMap.computeIfAbsent(className, k -> new ClassStats(className));
                if (id1Filled && !id1Valid) {
                    stats.addError(file.getName(), r+1, "ID движения 1");
                }
                if (id2Filled && !id2Valid) {
                    stats.addError(file.getName(), r+1, "ID добро РФ");
                }
                if (id1Filled && id1Valid) stats.incId1();
                if (id2Filled && id2Valid) stats.incId2();

                allRows.add(new MergedRow(file.getName(), numStr, className, studentName, id1, id2));
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обработке файла " + file.getName() + ": " + e.getMessage());
        }
    }

    private static boolean isValidId(String value) {
        if (value == null || value.isEmpty()) return true;
        return Pattern.matches("\\d{8}", value);
    }

    private static void createDataSheet(Sheet sheet, List<MergedRow> rows) {
        String[] headers = {"Источник", "№", "Класс", "ФИО", "ID движения 1", "ID добро РФ"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        int rowNum = 1;
        for (MergedRow r : rows) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(r.source);
            row.createCell(1).setCellValue(r.num);
            row.createCell(2).setCellValue(r.className);
            row.createCell(3).setCellValue(r.studentName);
            row.createCell(4).setCellValue(r.id1);
            row.createCell(5).setCellValue(r.id2);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createSummarySheet(Sheet sheet, Map<String, ClassStats> statsMap) {
        String[] headers = {"Класс", "Число ID движения 1", "Число ID добро РФ", "Ошибка при обработке"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        int rowNum = 1;
        for (ClassStats stats : statsMap.values()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(stats.className);
            row.createCell(1).setCellValue(stats.countId1);
            row.createCell(2).setCellValue(stats.countId2);
            String errorMsg = stats.getErrorsAsString();
            if (!errorMsg.isEmpty()) {
                row.createCell(3).setCellValue(errorMsg);
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы
    // -----------------------------------------------------------------------

    private static int findColumnIndex(Row headerRow, String... possibleNames) {
        if (headerRow == null) return -1;
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String cellValue = getCellString(headerRow.getCell(i))
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase();
            if (cellValue.isEmpty()) continue;
            for (String name : possibleNames) {
                if (cellValue.contains(name.trim().toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) {
                    return Long.toString((long) d);
                } else {
                    return Double.toString(d);
                }
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return Double.toString(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    private static String normalizeClassName(String className) {
        return className.replaceAll("[\\s-]", "").toUpperCase();
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // -----------------------------------------------------------------------
    // Внутренние классы
    // -----------------------------------------------------------------------

    static class ClassInfo {
        String className;
        String teacherFull;
        String corps;

        ClassInfo(String className, String teacherFull, String corps) {
            this.className = className;
            this.teacherFull = teacherFull;
            this.corps = corps;
        }
    }

    static class Student {
        String name;
        String originalClass;

        Student(String name, String originalClass) {
            this.name = name;
            this.originalClass = originalClass;
        }
    }

    static class MergedRow {
        String source;
        String num;
        String className;
        String studentName;
        String id1;
        String id2;

        MergedRow(String source, String num, String className, String studentName, String id1, String id2) {
            this.source = source;
            this.num = num;
            this.className = className;
            this.studentName = studentName;
            this.id1 = id1;
            this.id2 = id2;
        }
    }

    static class ClassStats {
        String className;
        int countId1;
        int countId2;
        List<String> errors = new ArrayList<>();

        ClassStats(String className) {
            this.className = className;
        }

        void incId1() { countId1++; }
        void incId2() { countId2++; }
        void addError(String fileName, int rowNum, String fieldName) {
            errors.add(String.format("%s: стр.%d, %s", fileName, rowNum, fieldName));
        }
        String getErrorsAsString() {
            return String.join("; ", errors);
        }
    }
}