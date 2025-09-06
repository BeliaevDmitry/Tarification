package org.school.analizJournal;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public abstract  class GradeAnalyzer1 {

    static {
        System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.NullLogger");
    }

    public static void main(String[] args) {
        String inputPath = "C:\\Users\\dimah\\Desktop\\1.xlsx";
        String outputPath = "C:\\Users\\dimah\\Desktop\\report.xlsx";

        try (Workbook workbook = WorkbookFactory.create(new File(inputPath))) {
            List<ProblemRecord> allProblems = new ArrayList<>();

            // Перебираем все листы в книге
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Анализируем лист: " + sheet.getSheetName());

                List<ProblemRecord> sheetProblems = analyzeSheet(sheet);
                allProblems.addAll(sheetProblems);
            }

            createReport(allProblems, outputPath);
            System.out.println("Отчет успешно создан: " + outputPath);
            System.out.println("Всего проблемных записей: " + allProblems.size());

        } catch (IOException | EncryptedDocumentException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static List<ProblemRecord> analyzeSheet(Sheet sheet) throws IOException {
        List<ProblemRecord> problems = new ArrayList<>();

        // Находим все блоки с периодами
        List<Map<String, Integer>> periodBlocks = findAllPeriodBlocks(sheet, 1); // Столбец B

        // Обрабатываем каждый найденный блок
        for (Map<String, Integer> block : periodBlocks) {
            int headerRow = block.get("headerRow");
            int periodRow = block.get("periodRow");

            // Обрабатываем студентов после периода до следующего блока или конца листа
            int startStudentRow = periodRow + 1;

            for (int rowNumFIO = startStudentRow; rowNumFIO <= sheet.getLastRowNum(); rowNumFIO++) {
                Row row = sheet.getRow(rowNumFIO);
                if (row == null) break;  // Прерываем цикл при полностью пустой строке

                // Проверяем строки с учениками (ФИО в столбце A)
                Cell nameCell = row.getCell(0);
                if (nameCell == null || nameCell.getCellType() != CellType.STRING
                        || nameCell.getStringCellValue().trim().isEmpty()) {
                    break;  // Прерываем цикл при пустой ячейке с ФИО
                }

                String studentName = nameCell.getStringCellValue().trim();

                // Проверяем оценки
                for (int i = 1; i < row.getLastCellNum(); i++) {
                    Cell gradeCell = row.getCell(i);
                    String gradeValue = getCellValueAsString(gradeCell);

                    // Безопасное получение периода
                    String period = getPeriod(sheet, periodRow, i); // Вынесено в отдельный метод

                    if (period.equals("Не указан")) break; // Прерываем цикл при пустой ячейке с периодом

                    // Проверяем проблемные оценки
                    if (gradeValue.isEmpty() || gradeValue.equalsIgnoreCase("А/З") || gradeValue.equalsIgnoreCase("НПА")) {
                        String subject = getSubject(sheet, headerRow, i);
                        String numberClass = getClass(sheet, headerRow - 2, 1);

                        String problemType = switch (gradeValue) {
                            case "" -> "Пустая оценка";
                            case "А/З", "а/з" -> "А/З";
                            case "НПА", "нпа" -> "НПА";
                            default -> "Неизвестная проблема";
                        };

                        problems.add(new ProblemRecord(
                                numberClass,
                                studentName,
                                subject,
                                period,
                                problemType
                        ));
                    }
                }
            }
        }
        return problems;
    }

    static void createReport(List<ProblemRecord> problems, String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Проблемные оценки");

            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Класс", "ФИО ученика", "Предмет", "Период", "Проблема"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Данные
            int rowNum = 1;
            for (ProblemRecord record : problems) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.className);
                row.createCell(1).setCellValue(record.studentName);
                row.createCell(2).setCellValue(record.subject);
                row.createCell(3).setCellValue(record.term);
                row.createCell(4).setCellValue(record.problem);
            }

            // Авторазмер колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING:
                        return cell.getStringCellValue().trim();
                    case NUMERIC:
                        return String.valueOf((int) cell.getNumericCellValue());
                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    default:
                        return "";
                }
            default:
                return "";
        }
    }

    static class ProblemRecord {
        String className;
        String studentName;
        String subject;
        String term;
        String problem;

        public ProblemRecord(String className, String studentName,
                             String subject, String term, String problem) {
            this.className = className;
            this.studentName = studentName;
            this.subject = subject;
            this.term = term;
            this.problem = problem;
        }
    }

    /**
     * Получает название предмета из указанной ячейки или ближайшей непустой ячейки слева
     *
     * @param sheet       объект листа Excel
     * @param headerRow   номер строки с заголовком (начиная с 0)
     * @param columnIndex номер столбца (начиная с 0)
     * @return название предмета или "Неизвестный предмет" если не найдено
     */
    public static String getSubject(Sheet sheet, int headerRow, int columnIndex) {
        // Проверка минимально допустимых значений
        if (sheet == null || headerRow < 0 || columnIndex < 1) {  // columnIndex >= 1 (пропускаем столбец с ФИО)
            return "Неизвестный предмет";
        }

        Row subjectRow = sheet.getRow(headerRow);
        if (subjectRow == null) {
            return "Неизвестный предмет";
        }

        // Идем справа налево (от указанного столбца к первому)
        for (int j = columnIndex; j >= 1; j--) {
            Cell subjectCell = subjectRow.getCell(j, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (subjectCell == null) {
                continue;
            }

            // Обрабатываем только текстовые ячейки
            if (subjectCell.getCellType() == CellType.STRING) {
                String subjectName = subjectCell.getStringCellValue().trim();
                if (!subjectName.isEmpty()) {
                    //System.out.printf("Найден предмет '%s' в строке %d, столбце %d%n",
                    //subjectName, headerRow + 1, j + 1);
                    return subjectName;
                }
            }
        }

        return "Неизвестный предмет";
    }

    /**
     * Получает значение ячейки в безопасном режиме
     *
     * @param sheet   лист Excel
     * @param rowNum  номер строки (начиная с 0)
     * @param cellNum номер столбца (начиная с 0)
     * @return значение ячейки как String или пустую строку при ошибках
     */
    public static String getClass(Sheet sheet, int rowNum, int cellNum) {
        // Проверка базовых условий
        if (sheet == null || rowNum < 0 || cellNum < 0) {
            return "";
        }

        try {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                return "";
            }

            Cell cell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (cell == null) {
                return "";
            }

            // Обработка разных типов ячеек
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();

                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    }
                    return String.valueOf((int) cell.getNumericCellValue());

                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());

                case FORMULA:
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getStringCellValue().trim();
                        case NUMERIC:
                            return String.valueOf((int) cell.getNumericCellValue());
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return "";
                    }

                default:
                    return "";
            }
        } catch (Exception e) {
            System.err.println("Ошибка при чтении ячейки [" + rowNum + "," + cellNum + "]: " + e.getMessage());
            return "";
        }
    }

    private static String getPeriod(Sheet sheet, int periodRow, int column) {
        Row row = sheet.getRow(periodRow);
        if (row == null) return "Не указан";

        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return "Не указан";
        }

        String value = cell.getStringCellValue().trim();
        return value.isEmpty() ? "Не указан" : value;
    }

    /**
     * Находит строки с периодами обучения (триместры/год) в указанном столбце
     * @param sheet лист Excel для поиска
     * @param columnIndex индекс столбца для поиска (обычно 1 для столбца B)
     * @return Map с ключами: "headerRow" - строка заголовков, "periodRow" - строка периода
     */
    /**
     * Находит все строки с периодами обучения (триместры/год) в указанном столбце
     *
     * @param sheet       лист Excel для поиска
     * @param columnIndex индекс столбца для поиска (обычно 1 для столбца B)
     * @return List<Map> где каждый Map содержит:
     * "headerRow" - строка предмета,
     * "periodRow" - строка периода,
     * "classRow" - строка с названием класса (опционально)
     */
    private static List<Map<String, Integer>> findAllPeriodBlocks(Sheet sheet, int columnIndex) {
        List<Map<String, Integer>> blocks = new ArrayList<>();

        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            Cell periodCell = row.getCell(columnIndex);
            if (periodCell != null && periodCell.getCellType() == CellType.STRING) {
                String cellValue = periodCell.getStringCellValue().trim();

                if (cellValue.matches("(1|2|3) триместр|год|(1|2) полугодие")) {
                    Map<String, Integer> block = new HashMap<>();
                    block.put("headerRow", rowNum - 1); // Строка с предметами выше
                    block.put("periodRow", rowNum);      // Строка с периодом
                    blocks.add(block);
                }
            }
        }

        return blocks;
    }
}