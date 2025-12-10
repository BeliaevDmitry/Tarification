package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.regex.*;

public class JournalAnalizItogMesh {

    public static void main(String[] args) {
        String folderPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\20251206";
        String excludedStudentsFile = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\Учащиеся. Только отчисленные. (5).xlsx";
        String outputPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\результат_анализа_МЭШ.xlsx";

        try {
            // Сначала загружаем список отчисленных учащихся
            Set<String> excludedStudents = loadExcludedStudents(excludedStudentsFile);
            System.out.println("Загружено отчисленных учащихся: " + excludedStudents.size());

            // Анализируем журналы с учетом фильтрации
            analyzeJournals(folderPath, excludedStudents, outputPath);
            System.out.println("Анализ завершен! Результат сохранен в: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Set<String> loadExcludedStudents(String filePath) throws Exception {
        Set<String> excludedStudents = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // Первый лист

            // Читаем все строки, начиная со второй (первая может быть заголовком)
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Cell nameCell = row.getCell(0); // Столбец A
                if (nameCell == null) continue;

                String studentName = getCellValueAsString(nameCell).trim();
                if (!studentName.isEmpty()) {
                    // Нормализуем имя: удаляем лишние пробелы
                    String normalizedName = studentName.replaceAll("\\s+", " ").trim();
                    excludedStudents.add(normalizedName);
                }
            }
        }

        return excludedStudents;
    }

    public static void analyzeJournals(String folderPath, Set<String> excludedStudents, String outputPath) throws Exception {
        Map<String, Map<String, StudentSubjectData>> studentData = new TreeMap<>();
        Set<String> allSubjects = new TreeSet<>();
        Set<String> allClasses = new TreeSet<>();

        // Проходим по всем файлам в папке
        Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".xlsx"))
                .forEach(filePath -> {
                    try {
                        processExcelFile(filePath.toString(), studentData, allSubjects, allClasses, excludedStudents);
                    } catch (Exception e) {
                        System.err.println("Ошибка при обработке файла: " + filePath);
                        e.printStackTrace();
                    }
                });

        // Создаем итоговый Excel файл
        createResultExcel(studentData, allSubjects, allClasses, outputPath);
    }

    private static void processExcelFile(String filePath,
                                         Map<String, Map<String, StudentSubjectData>> studentData,
                                         Set<String> allSubjects,
                                         Set<String> allClasses,
                                         Set<String> excludedStudents) throws Exception {

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Получаем название класса из имени файла и содержимого
            String fileName = new File(filePath).getName();
            String className = extractClassNameFromFile(fileName, workbook);
            allClasses.add(className);

            // Обрабатываем все вкладки (листы) в файле
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();

                // Пропускаем технические листы
                if (sheetName.contains("Данные") || sheetName.contains("Лист")) {
                    continue;
                }

                String subject = extractSubject(sheet);
                allSubjects.add(subject);

                System.out.println("Обрабатываем предмет: " + subject + " из класса: " + className +
                        " из файла: " + fileName);

                // Обрабатываем все блоки с данными
                processAllBlocks(sheet, studentData, subject, className, excludedStudents);
            }
        }
    }

    private static String extractClassNameFromFile(String fileName, Workbook workbook) {
        // Сначала пытаемся извлечь из содержимого файла
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String subject = extractSubject(sheet);

            // Ищем класс в строке предмета (формат: "Предмет 1-А 1А группа" или "Предмет, 1-А")
            Pattern pattern = Pattern.compile("([0-9]+[-–—][А-Яа-яA-Za-z])");
            Matcher matcher = pattern.matcher(subject);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // Если не нашли в содержимом, пытаемся из имени файла
        // Ищем паттерн: цифры-буква (например, "1-А", "11-Б")
        Pattern pattern = Pattern.compile("([0-9]+[-–—][А-Яа-яA-Za-z])");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Если не нашли, возвращаем часть до первого пробела
        String nameWithoutExt = fileName.replace(".xlsx", "").replace(".XLSX", "");
        String[] parts = nameWithoutExt.split("[ _\\-]");
        return parts.length > 0 ? parts[0] : "Неизвестный класс";
    }

    private static void processAllBlocks(Sheet sheet,
                                         Map<String, Map<String, StudentSubjectData>> studentData,
                                         String subject,
                                         String className,
                                         Set<String> excludedStudents) {

        // Карта для временного хранения данных по студентам
        Map<String, StudentTempData> tempDataMap = new HashMap<>();

        // Обрабатываем все блоки до 1000 строки
        for (int blockStart = 1; blockStart < 1000; blockStart += 50) {
            int dateRowNum = blockStart; // Строка с датами (0-based: строка 2)
            int dataStartRow = blockStart + 1; // Начало данных студентов
            int dataEndRow = blockStart + 48; // Конец данных студентов (49 строк всего)

            // Проверяем строку с датами
            Row dateRow = sheet.getRow(dateRowNum);
            if (dateRow == null) {
                // Если нет строки с датами, возможно блок закончился
                continue;
            }

            // Ищем столбец с "1Т" в этом блоке
            int trimester1Col = findTrimesterColumnInRow(dateRow);
            boolean hasTrimesterGrade = (trimester1Col != -1);

            // Обрабатываем блок данных
            for (int rowNum = dataStartRow; rowNum <= dataEndRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Cell nameCell = row.getCell(1); // Столбец B
                if (nameCell == null) continue;

                String studentName = getCellValueAsString(nameCell);
                if (studentName == null || studentName.trim().isEmpty() ||
                        isHeaderRow(studentName)) {
                    continue;
                }

                String cleanedName = studentName.trim().replaceAll("\\s+", " ");

                // Проверяем, не является ли студент отчисленным
                if (isExcludedStudent(cleanedName, excludedStudents)) {
                    System.out.println("Пропускаем отчисленного студента: " + cleanedName + " из класса: " + className);
                    continue;
                }

                String studentKey = cleanedName + " (" + className + ")";

                StudentTempData tempData = tempDataMap.get(studentKey);
                if (tempData == null) {
                    tempData = new StudentTempData();
                    tempDataMap.put(studentKey, tempData);
                }

                // Если в этом блоке есть колонка с 1Т, получаем оценку за 1 триместр
                if (hasTrimesterGrade && trimester1Col != -1) {
                    Cell trimesterCell = row.getCell(trimester1Col);
                    if (trimesterCell != null) {
                        String trimesterGrade = getTrimesterGradeValue(trimesterCell);
                        // Сохраняем только если еще не было оценки
                        if (tempData.trimester1Grade.isEmpty()) {
                            tempData.trimester1Grade = trimesterGrade;
                        }
                    }
                }

                // Обрабатываем оценки в строке
                // Если есть колонка с 1Т, обрабатываем оценки только до нее
                // Если нет - обрабатываем все оценки до столбца S
                int endColForGrades = hasTrimesterGrade ? trimester1Col - 1 : 18;
                int absencesInRow = processGradesInRow(row, tempData.grades, endColForGrades);
                tempData.absences += absencesInRow;
            }
        }

        // Сохраняем собранные данные
        for (Map.Entry<String, StudentTempData> entry : tempDataMap.entrySet()) {
            String studentKey = entry.getKey();
            StudentTempData tempData = entry.getValue();

            if (!tempData.grades.isEmpty() || !tempData.trimester1Grade.isEmpty() || tempData.absences > 0) {
                studentData.putIfAbsent(studentKey, new HashMap<>());
                Map<String, StudentSubjectData> studentSubjects = studentData.get(studentKey);

                double average = tempData.grades.isEmpty() ? 0.0 :
                        tempData.grades.stream().mapToInt(Integer::intValue).average().orElse(0.0);

                // Вычисляем ожидаемую оценку по правилам округления
                String expectedGrade = calculateExpectedGrade(average);

                // Проверяем ошибку
                String error = checkGradeError(tempData.trimester1Grade, expectedGrade);

                studentSubjects.put(subject, new StudentSubjectData(
                        className, subject, average, tempData.grades.size(),
                        tempData.trimester1Grade, expectedGrade, error, tempData.absences
                ));
            }
        }
    }

    private static boolean isExcludedStudent(String studentName, Set<String> excludedStudents) {
        if (excludedStudents.isEmpty()) {
            return false;
        }

        // Нормализуем имя для сравнения
        String normalizedName = studentName.replaceAll("\\s+", " ").trim();

        // Прямое сравнение
        if (excludedStudents.contains(normalizedName)) {
            return true;
        }

        // Также проверяем частичное совпадение (на случай небольших расхождений в написании)
        for (String excludedName : excludedStudents) {
            if (normalizedName.contains(excludedName) || excludedName.contains(normalizedName)) {
                return true;
            }
        }

        return false;
    }

    private static int findTrimesterColumnInRow(Row dateRow) {
        if (dateRow == null) return -1;

        // Проверяем столбцы C-S (индексы 2-18) на наличие "1Т" или "I триместр"
        for (int colNum = 2; colNum <= 18; colNum++) {
            Cell cell = dateRow.getCell(colNum);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell).trim();
                if (cellValue.contains("1Т") ||
                        cellValue.contains("I триместр") ||
                        cellValue.contains("1 триместр")) {
                    return colNum;
                }
            }
        }
        return -1;
    }

    private static boolean isHeaderRow(String cellValue) {
        String value = cellValue.trim().toLowerCase();
        return value.contains("фамилия") ||
                value.contains("имя") ||
                value.contains("ученик") ||
                value.contains("фио") ||
                value.equals("") ||
                value.matches("^[\\d\\s]*$"); // Только цифры и пробелы
    }

    private static int processGradesInRow(Row row, List<Integer> grades, int endCol) {
        int absences = 0;
        int startCol = 2; // Столбец C

        // Проверяем, что endCol корректный
        if (endCol < startCol) {
            endCol = startCol;
        }

        for (int colNum = startCol; colNum <= endCol; colNum++) {
            Cell gradeCell = row.getCell(colNum);
            if (gradeCell == null) continue;

            switch (gradeCell.getCellType()) {
                case NUMERIC:
                    double gradeValue = gradeCell.getNumericCellValue();
                    // В МЭШ могут быть оценки с десятичными дробями
                    if (gradeValue >= 1 && gradeValue <= 5) {
                        grades.add((int) Math.round(gradeValue));
                    }
                    break;

                case STRING:
                    String cellValue = gradeCell.getStringCellValue().trim();
                    if ("н".equalsIgnoreCase(cellValue) || "Н".equalsIgnoreCase(cellValue) ||
                            "б".equalsIgnoreCase(cellValue) || "Б".equalsIgnoreCase(cellValue) ||
                            "п".equalsIgnoreCase(cellValue) || "П".equalsIgnoreCase(cellValue)) {
                        absences++;
                    } else {
                        try {
                            // Пробуем извлечь число из строки
                            String numericPart = cellValue.replaceAll("[^0-9.,]", "");
                            if (!numericPart.isEmpty()) {
                                double grade = Double.parseDouble(numericPart.replace(',', '.'));
                                if (grade >= 1 && grade <= 5) {
                                    grades.add((int) Math.round(grade));
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Игнорируем нечисловые значения
                        }
                    }
                    break;

                case FORMULA:
                    try {
                        double formulaValue = gradeCell.getNumericCellValue();
                        if (formulaValue >= 1 && formulaValue <= 5) {
                            grades.add((int) Math.round(formulaValue));
                        }
                    } catch (Exception e) {
                        try {
                            String formulaString = gradeCell.getStringCellValue();
                            if ("н".equalsIgnoreCase(formulaString) || "Н".equalsIgnoreCase(formulaString) ||
                                    "б".equalsIgnoreCase(formulaString) || "Б".equalsIgnoreCase(formulaString) ||
                                    "п".equalsIgnoreCase(formulaString) || "П".equalsIgnoreCase(formulaString)) {
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

    private static String getTrimesterGradeValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case NUMERIC:
                double value = cell.getNumericCellValue();
                if (value == (int) value) {
                    return String.valueOf((int) value);
                } else {
                    // Округляем до целого
                    return String.valueOf(Math.round(value));
                }
            case STRING:
                String strValue = cell.getStringCellValue().trim();
                if (strValue.equalsIgnoreCase("АЗ") || strValue.equalsIgnoreCase("Н/А") ||
                        strValue.equalsIgnoreCase("н/а")) {
                    return "АЗ";
                }
                if (strValue.isEmpty()) {
                    return "";
                }
                // Пробуем извлечь число
                try {
                    String numericPart = strValue.replaceAll("[^0-9.,]", "");
                    if (!numericPart.isEmpty()) {
                        double num = Double.parseDouble(numericPart.replace(',', '.'));
                        return String.valueOf(Math.round(num));
                    }
                } catch (NumberFormatException e) {
                    // Оставляем как есть
                }
                return strValue;
            case FORMULA:
                try {
                    double formulaValue = cell.getNumericCellValue();
                    return String.valueOf(Math.round(formulaValue));
                } catch (Exception e) {
                    try {
                        String formulaString = cell.getStringCellValue();
                        if (formulaString.equalsIgnoreCase("АЗ") || formulaString.equalsIgnoreCase("Н/А")) {
                            return "АЗ";
                        }
                        return formulaString;
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return "";
        }
    }

    private static String calculateExpectedGrade(double average) {
        if (average == 0.0) return "нет оценок";

        if (average >= 4.65) {
            return "5";
        } else if (average >= 3.65) {
            return "4";
        } else if (average >= 2.65) {
            return "3";
        } else if (average >= 1.0) {
            return "2";
        } else {
            return "нет оценки";
        }
    }

    private static String checkGradeError(String actualGrade, String expectedGrade) {
        if (actualGrade.isEmpty()) {
            return "нет оценки";
        }

        if (actualGrade.equals("АЗ") || actualGrade.equalsIgnoreCase("Н/А") ||
                actualGrade.equalsIgnoreCase("н/а")) {
            return "АЗ";
        }

        if (expectedGrade.equals("нет оценок")) {
            return "нет оценок для расчета";
        }

        if (expectedGrade.equals("нет оценки")) {
            return "средний балл ниже 2.65";
        }

        try {
            // Пробуем преобразовать в число для сравнения
            int actual = Integer.parseInt(actualGrade);
            int expected = Integer.parseInt(expectedGrade);

            if (actual != expected) {
                return "ожидалось: " + expectedGrade + ", выставлено: " + actualGrade;
            }
        } catch (NumberFormatException e) {
            // Если не число, значит это текстовая отметка
            if (!actualGrade.matches("\\d+")) {
                return "текстовая отметка: " + actualGrade;
            }
        }

        return "";
    }

    private static String extractSubject(Sheet sheet) {
        String subject = "Неизвестный предмет";

        // В МЭШ предмет часто в ячейке U41 (0-based: row 40, col 20)
        subject = extractSubjectFromCell(sheet, 40, 20, subject);

        // Пробуем другие возможные расположения
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 0, 0, subject); // A1
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 1, 0, subject); // A2
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 0, 20, subject); // U1
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 39, 0, subject); // A40
        }

        // Если все еще не нашли, используем имя листа
        if (subject.equals("Неизвестный предмет")) {
            subject = sheet.getSheetName();
        }

        // Очищаем название предмета от информации о классе и группе
        subject = cleanSubjectName(subject);

        return subject;
    }

    private static String cleanSubjectName(String subject) {
        if (subject == null || subject.isEmpty()) {
            return subject;
        }

        // Удаляем информацию о классе и группе
        // Паттерны: "Предмет 1-А 1А группа" или "Предмет, 1-А"
        subject = subject.replaceAll("[0-9]+[-–—][А-Яа-яA-Za-z]\\s*[0-9]*[А-Яа-яA-Za-z]*\\s*группа", "");
        subject = subject.replaceAll(",\\s*[0-9]+[-–—][А-Яа-яA-Za-z]", "");
        subject = subject.replaceAll("\\s*[0-9]+[-–—][А-Яа-яA-Za-z]\\s*", " ");

        // Удаляем лишние пробелы и запятые
        subject = subject.trim().replaceAll("\\s*,\\s*", ", ").replaceAll("\\s+", " ");

        // Если осталась только запятая в начале или конце, удаляем её
        if (subject.startsWith(",")) {
            subject = subject.substring(1).trim();
        }
        if (subject.endsWith(",")) {
            subject = subject.substring(0, subject.length() - 1).trim();
        }

        return subject.isEmpty() ? "Неизвестный предмет" : subject;
    }

    private static String extractSubjectFromCell(Sheet sheet, int rowNum, int colNum, String defaultSubject) {
        Row row = sheet.getRow(rowNum);
        if (row != null) {
            Cell cell = row.getCell(colNum);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null && !cellValue.trim().isEmpty()) {
                    return cellValue.trim();
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

    private static void createResultExcel(Map<String, Map<String, StudentSubjectData>> studentData,
                                          Set<String> allSubjects,
                                          Set<String> allClasses,
                                          String outputPath) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Результаты анализа");

            // Создаем стили для ячеек
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle errorStyle = createErrorStyle(workbook);
            CellStyle azStyle = createAZStyle(workbook);
            CellStyle warningStyle = createWarningStyle(workbook);

            // Создаем заголовки
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(25);

            String[] headers = {"Класс", "ФИО студента", "Предмет",
                    "Средний балл", "Количество оценок",
                    "Оценка за 1Т", "Ожидаемая оценка", "Ошибка", "Пропуски"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Заполняем данные студентов
            int rowIndex = 1;
            for (Map.Entry<String, Map<String, StudentSubjectData>> studentEntry : studentData.entrySet()) {
                String studentKey = studentEntry.getKey();
                Map<String, StudentSubjectData> subjects = studentEntry.getValue();

                // Извлекаем класс и имя из ключа
                String studentName = studentKey.substring(0, studentKey.indexOf('(')).trim();
                String className = studentKey.substring(studentKey.indexOf('(') + 1, studentKey.indexOf(')')).trim();

                // Для каждого предмета создаем отдельную строку
                for (Map.Entry<String, StudentSubjectData> subjectEntry : subjects.entrySet()) {
                    String subject = subjectEntry.getKey();
                    StudentSubjectData data = subjectEntry.getValue();

                    Row row = sheet.createRow(rowIndex++);

                    // Класс
                    Cell classCell = row.createCell(0);
                    classCell.setCellValue(data.className);
                    classCell.setCellStyle(textStyle);

                    // ФИО студента
                    Cell nameCell = row.createCell(1);
                    nameCell.setCellValue(studentName);
                    nameCell.setCellStyle(textStyle);

                    // Предмет
                    Cell subjectCell = row.createCell(2);
                    subjectCell.setCellValue(subject);
                    subjectCell.setCellStyle(textStyle);

                    // Средний балл
                    Cell avgCell = row.createCell(3);
                    if (data.average > 0) {
                        avgCell.setCellValue(Math.round(data.average * 100.0) / 100.0);
                    } else {
                        avgCell.setCellValue(0);
                    }
                    avgCell.setCellStyle(numberStyle);

                    // Количество оценок
                    Cell countCell = row.createCell(4);
                    countCell.setCellValue(data.gradeCount);
                    countCell.setCellStyle(numberStyle);

                    // Оценка за 1Т
                    Cell grade1TCell = row.createCell(5);
                    grade1TCell.setCellValue(data.trimester1Grade);

                    // Применяем стиль в зависимости от содержания
                    if (data.trimester1Grade.equals("АЗ")) {
                        grade1TCell.setCellStyle(azStyle);
                    } else if (data.error.contains("ожидалось:") || data.error.contains("текстовая отметка:")) {
                        grade1TCell.setCellStyle(errorStyle);
                    } else if (data.error.contains("нет оценок для расчета") || data.error.contains("средний балл ниже")) {
                        grade1TCell.setCellStyle(warningStyle);
                    } else {
                        grade1TCell.setCellStyle(textStyle);
                    }

                    // Ожидаемая оценка
                    Cell expectedCell = row.createCell(6);
                    expectedCell.setCellValue(data.expectedGrade);
                    expectedCell.setCellStyle(textStyle);

                    // Ошибка
                    Cell errorCell = row.createCell(7);
                    errorCell.setCellValue(data.error);

                    if (data.error.contains("ожидалось:") || data.error.contains("текстовая отметка:")) {
                        errorCell.setCellStyle(errorStyle);
                    } else if (data.error.contains("нет оценок для расчета") || data.error.contains("средний балл ниже")) {
                        errorCell.setCellStyle(warningStyle);
                    } else if (data.error.equals("АЗ")) {
                        errorCell.setCellStyle(azStyle);
                    } else {
                        errorCell.setCellStyle(textStyle);
                    }

                    // Пропуски
                    Cell absencesCell = row.createCell(8);
                    absencesCell.setCellValue(data.absences);
                    absencesCell.setCellStyle(numberStyle);
                }
            }

            // Автоподбор ширины колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Замораживаем область с заголовками
            sheet.createFreezePane(0, 1);

            // Добавляем фильтр
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex-1, 0, headers.length-1));

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
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createErrorStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createAZStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createWarningStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_YELLOW.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // Вспомогательный класс для временного хранения данных
    static class StudentTempData {
        List<Integer> grades = new ArrayList<>();
        int absences = 0;
        String trimester1Grade = "";
    }

    // Вспомогательный класс для хранения всех данных по предмету
    static class StudentSubjectData {
        String className;
        String subject;
        double average;
        int gradeCount;
        String trimester1Grade;
        String expectedGrade;
        String error;
        int absences;

        StudentSubjectData(String className, String subject, double average, int gradeCount,
                           String trimester1Grade, String expectedGrade, String error, int absences) {
            this.className = className;
            this.subject = subject;
            this.average = average;
            this.gradeCount = gradeCount;
            this.trimester1Grade = trimester1Grade;
            this.expectedGrade = expectedGrade;
            this.error = error;
            this.absences = absences;
        }
    }
}