package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.nio.file.*;

public class JournalAnalyzerBall {

    public static void main(String[] args) {
        String folderPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Предпрофили\\11 классы";
        String outputPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Предпрофили\\результат_анализа.xlsx";

        try {
            analyzeJournals(folderPath, outputPath);
            System.out.println("Анализ завершен! Результат сохранен в: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void analyzeJournals(String folderPath, String outputPath) throws Exception {
        Map<String, Map<String, SubjectStats>> studentData = new TreeMap<>();
        Set<String> allSubjects = new TreeSet<>();

        // Проходим по всем файлам в папке
        Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".xlsx"))
                .forEach(filePath -> {
                    try {
                        processExcelFile(filePath.toString(), studentData, allSubjects);
                    } catch (Exception e) {
                        System.err.println("Ошибка при обработке файла: " + filePath);
                        e.printStackTrace();
                    }
                });

        // Создаем итоговый Excel файл
        createResultExcel(studentData, allSubjects, outputPath);
    }

    private static void processExcelFile(String filePath,
                                         Map<String, Map<String, SubjectStats>> studentData,
                                         Set<String> allSubjects) throws Exception {

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Обрабатываем все вкладки (листы) в файле
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String subject = extractSubject(sheet);
                allSubjects.add(subject);

                System.out.println("Обрабатываем предмет: " + subject + " из файла: " + filePath);

                // Собираем уникальные ФИО из первого блока (2-50)
                Set<String> uniqueStudents = extractUniqueStudents(sheet);

                // Обрабатываем все блоки с данными для уникальных студентов
                processAllBlocksForStudents(sheet, studentData, subject, uniqueStudents);
            }
        }
    }

    private static Set<String> extractUniqueStudents(Sheet sheet) {
        Set<String> uniqueStudents = new TreeSet<>();

        // Извлекаем уникальные ФИО только из первого блока (строки 2-50)
        for (int rowNum = 2; rowNum <= 50; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            Cell nameCell = row.getCell(1); // Столбец B
            if (nameCell == null) continue;

            String studentName = getCellValueAsString(nameCell);
            if (studentName != null && !studentName.trim().isEmpty()) {
                uniqueStudents.add(studentName.trim());
            }
        }

        return uniqueStudents;
    }

    private static void processAllBlocksForStudents(Sheet sheet,
                                                    Map<String, Map<String, SubjectStats>> studentData,
                                                    String subject,
                                                    Set<String> uniqueStudents) {

        // Определяем все блоки для обработки
        int[][] blocks = {
                {2, 50},    // Первый блок
                {53, 100},   // Второй блок
                {103, 150},  // Третий блок
                {153, 200}   // Четвертый блок
        };

        // Для каждого уникального студента собираем все оценки из всех блоков
        for (String studentName : uniqueStudents) {
            List<Integer> allGrades = new ArrayList<>();
            int totalAbsences = 0;

            // Обрабатываем все блоки для этого студента
            for (int[] block : blocks) {
                int startRow = block[0];
                int endRow = block[1];

                for (int rowNum = startRow; rowNum <= endRow; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null) continue;

                    // Проверяем, что это строка нужного студента
                    Cell nameCell = row.getCell(1); // Столбец B
                    if (nameCell == null) continue;

                    String currentStudentName = getCellValueAsString(nameCell);
                    if (currentStudentName == null || !currentStudentName.trim().equals(studentName)) {
                        continue;
                    }

                    // Обрабатываем оценки в этой строке и получаем количество пропусков
                    int absencesInRow = processGradesInRow(row, allGrades);
                    totalAbsences += absencesInRow;
                }
            }

            // Сохраняем собранные данные
            if (!allGrades.isEmpty() || totalAbsences > 0) {
                studentData.putIfAbsent(studentName, new HashMap<>());
                Map<String, SubjectStats> studentSubjects = studentData.get(studentName);

                double average = allGrades.isEmpty() ? 0.0 :
                        allGrades.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                studentSubjects.put(subject, new SubjectStats(average, totalAbsences, allGrades.size()));
            }
        }
    }

    private static int processGradesInRow(Row row, List<Integer> grades) {
        int absences = 0;

        // Обрабатываем оценки с столбцов C до S (индексы 2-18)
        for (int colNum = 2; colNum <= 18; colNum++) {
            Cell gradeCell = row.getCell(colNum);
            if (gradeCell == null) continue;

            switch (gradeCell.getCellType()) {
                case NUMERIC:
                    double gradeValue = gradeCell.getNumericCellValue();
                    if (gradeValue >= 1 && gradeValue <= 5) {
                        grades.add((int) gradeValue);
                    }
                    break;

                case STRING:
                    String cellValue = gradeCell.getStringCellValue().trim();
                    if ("н".equalsIgnoreCase(cellValue)) {
                        absences++;
                    } else {
                        try {
                            int grade = Integer.parseInt(cellValue);
                            if (grade >= 1 && grade <= 5) {
                                grades.add(grade);
                            }
                        } catch (NumberFormatException e) {
                            // Игнорируем нечисловые значения кроме "н"
                        }
                    }
                    break;

                case FORMULA:
                    try {
                        double formulaValue = gradeCell.getNumericCellValue();
                        if (formulaValue >= 1 && formulaValue <= 5) {
                            grades.add((int) formulaValue);
                        }
                    } catch (Exception e) {
                        try {
                            String formulaString = gradeCell.getStringCellValue();
                            if ("н".equalsIgnoreCase(formulaString)) {
                                absences++;
                            }
                        } catch (Exception ex) {
                            // Игнорируем некорректные формулы
                        }
                    }
                    break;
            }
        }

        return absences;
    }

    private static String extractSubject(Sheet sheet) {
        // Пробуем несколько возможных расположений названия предмета
        String subject = "Неизвестный предмет";

        // Вариант 1: ячейка U41 (строка 40, столбец 20)
        subject = extractSubjectFromCell(sheet, 40, 20, subject);

        // Если не нашли, пробуем другие возможные расположения
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 0, 0, subject); // A1
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 1, 0, subject); // A2
        }

        return subject;
    }

    private static String extractSubjectFromCell(Sheet sheet, int rowNum, int colNum, String defaultSubject) {
        Row row = sheet.getRow(rowNum);
        if (row != null) {
            Cell cell = row.getCell(colNum);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null && !cellValue.trim().isEmpty()) {
                    // Берем часть после запятой если есть запятая
                    if (cellValue.contains(",")) {
                        String subject = cellValue.split(",")[1].trim();
                        return subject.replaceAll("\\s+", " ");
                    }
                    // Или возвращаем все значение если нет запятой
                    return cellValue.replaceAll("\\s+", " ").trim();
                }
            }
        }
        return defaultSubject;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Убираем .0 у целых чисел
                    double value = cell.getNumericCellValue();
                    if (value == (int) value) {
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
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return "";
        }
    }

    private static void createResultExcel(Map<String, Map<String, SubjectStats>> studentData,
                                          Set<String> allSubjects, String outputPath) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Результаты анализа");

            // Создаем стили для ячеек
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            // Создаем заголовки - ФИО + для каждого предмета 3 колонки
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(25);
            headerRow.createCell(0).setCellValue("ФИО студента");
            headerRow.getCell(0).setCellStyle(headerStyle);

            int colIndex = 1;
            Map<String, Integer> subjectColumns = new HashMap<>();

            // Создаем колонки для каждого предмета
            for (String subject : allSubjects) {
                // Каждый предмет занимает 3 колонки подряд
                headerRow.createCell(colIndex).setCellValue(subject + " - Средний балл");
                headerRow.getCell(colIndex).setCellStyle(headerStyle);

                headerRow.createCell(colIndex + 1).setCellValue(subject + " - Пропуски");
                headerRow.getCell(colIndex + 1).setCellStyle(headerStyle);

                headerRow.createCell(colIndex + 2).setCellValue(subject + " - Кол-во оценок");
                headerRow.getCell(colIndex + 2).setCellStyle(headerStyle);

                subjectColumns.put(subject, colIndex);
                colIndex += 3;
            }

            // Заполняем данные студентов
            int rowIndex = 1;
            for (Map.Entry<String, Map<String, SubjectStats>> studentEntry : studentData.entrySet()) {
                Row row = sheet.createRow(rowIndex++);

                // ФИО студента
                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(studentEntry.getKey());
                nameCell.setCellStyle(textStyle);

                Map<String, SubjectStats> subjects = studentEntry.getValue();

                // Заполняем данные по каждому предмету
                for (Map.Entry<String, Integer> subjectCol : subjectColumns.entrySet()) {
                    String subject = subjectCol.getKey();
                    int startCol = subjectCol.getValue();

                    SubjectStats stats = subjects.get(subject);
                    if (stats != null) {
                        // Средний балл
                        Cell avgCell = row.createCell(startCol);
                        avgCell.setCellValue(Math.round(stats.average * 100.0) / 100.0);
                        avgCell.setCellStyle(numberStyle);

                        // Пропуски
                        Cell absCell = row.createCell(startCol + 1);
                        absCell.setCellValue(stats.absences);
                        absCell.setCellStyle(numberStyle);

                        // Количество оценок
                        Cell countCell = row.createCell(startCol + 2);
                        countCell.setCellValue(stats.gradeCount);
                        countCell.setCellStyle(numberStyle);
                    } else {
                        // Нет данных по этому предмету
                        Cell avgCell = row.createCell(startCol);
                        avgCell.setCellValue("-");
                        avgCell.setCellStyle(textStyle);

                        Cell absCell = row.createCell(startCol + 1);
                        absCell.setCellValue(0);
                        absCell.setCellStyle(numberStyle);

                        Cell countCell = row.createCell(startCol + 2);
                        countCell.setCellValue(0);
                        countCell.setCellStyle(numberStyle);
                    }
                }
            }

            // Автоподбор ширины колонок
            for (int i = 0; i < colIndex; i++) {
                sheet.autoSizeColumn(i);
            }

            // Замораживаем область с заголовками
            sheet.createFreezePane(1, 1);

            // Сохраняем файл
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // Вспомогательный класс для хранения статистики по предмету
    static class SubjectStats {
        double average;
        int absences;
        int gradeCount;

        SubjectStats(double average, int absences, int gradeCount) {
            this.average = average;
            this.absences = absences;
            this.gradeCount = gradeCount;
        }

        @Override
        public String toString() {
            return String.format("Средний: %.2f, Пропуски: %d, Оценок: %d", average, absences, gradeCount);
        }
    }
}