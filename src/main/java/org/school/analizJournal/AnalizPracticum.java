package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalizPracticum {

    public static void main(String[] args) {
        try {
            // Пути к файлам
            String practicumFilePath = "C:\\Users\\dimah\\Downloads\\journals - 2025-09-01T162544.753.xlsx";
            String mainFilePath = "C:\\Users\\dimah\\Downloads\\ЕГЭ 2026.xlsx"; // замените на путь к основному файлу
            String outputFilePath = "C:\\Users\\dimah\\Desktop\\output.xlsx";

            // Обработка файлов
            processExcelFiles(practicumFilePath, mainFilePath, outputFilePath);

            System.out.println("Файлы успешно обработаны. Результат сохранен в: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processExcelFiles(String practicumPath, String mainPath, String outputPath) throws IOException {
        // 1. Читаем данные из практикума (первый файл)
        List<Student> practicumStudents = readPracticumData(practicumPath);

        // 2. Читаем данные из основного файла
        List<MainStudent> mainStudents = readMainData(mainPath);

        // 3. Создаем карту для быстрого поиска по ФИО и группе
        Map<String, Boolean> mainStudentsMap = createMainStudentsMap(mainStudents);

        // 4. Сравниваем данные и создаем результат
        compareAndCreateOutput(practicumStudents, mainStudents, mainStudentsMap, outputPath);
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
            throw new IOException("Лист 'Свод вертикальный' не найден");
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

            // Пропускаем если нет группы практикума
            if (practicumGroup == null || practicumGroup.trim().isEmpty()) {
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

    private static void compareAndCreateOutput(List<Student> practicumStudents,
                                               List<MainStudent> mainStudents,
                                               Map<String, Boolean> mainStudentsMap,
                                               String outputPath) throws IOException {

        Workbook outputWorkbook = new XSSFWorkbook();

        // 1. Лист с результатами сравнения
        Sheet comparisonSheet = outputWorkbook.createSheet("Сравнение");
        createComparisonSheet(comparisonSheet, practicumStudents, mainStudentsMap);

        // 2. Лист с основными данными + маркер совпадения
        Sheet mainDataSheet = outputWorkbook.createSheet("Основные данные");
        createMainDataSheet(mainDataSheet, mainStudents, practicumStudents);

        // Сохраняем файл
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            outputWorkbook.write(fos);
        }

        outputWorkbook.close();
    }

    private static void createComparisonSheet(Sheet sheet, List<Student> practicumStudents, Map<String, Boolean> mainStudentsMap) {
        // Заголовки
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ФИО");
        headerRow.createCell(1).setCellValue("Группа практикума");
        headerRow.createCell(2).setCellValue("Наличие в основном списке");

        // Стиль для ошибок
        CellStyle errorStyle = sheet.getWorkbook().createCellStyle();
        errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (Student student : practicumStudents) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getName());
            row.createCell(1).setCellValue(student.getGroup());

            // Проверяем наличие в основном списке
            String key = student.getName().trim().toLowerCase() + "|" + student.getGroup().toLowerCase();
            boolean existsInMain = mainStudentsMap.containsKey(key);

            Cell statusCell = row.createCell(2);
            statusCell.setCellValue(existsInMain ? "Есть" : "ОШИБКА");

            // Подсвечиваем ошибки
            if (!existsInMain) {
                statusCell.setCellStyle(errorStyle);
            }
        }

        // Автоподбор ширины колонок
        for (int i = 0; i < 3; i++) {
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