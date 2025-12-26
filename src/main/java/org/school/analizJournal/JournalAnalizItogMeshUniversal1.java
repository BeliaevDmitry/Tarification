package org.school.analizJournal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class JournalAnalizItogMeshUniversal1 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Анализ журналов МЭШ ===");
        System.out.println("Выберите школу:");
        System.out.println("1 - Школа 1811");
        System.out.println("2 - Школа 7");
        System.out.print("Введите номер (1 или 2): ");

        int schoolChoice = scanner.nextInt();
        scanner.nextLine(); // очистка буфера

        String folderPath;
        String excludedStudentsFile;
        String outputPath;
        String averageScoresFolder;
        String contingentFilePath;

        switch (schoolChoice) {
            case 1:
                folderPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\20251206";
                excludedStudentsFile = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\Учащиеся. Только отчисленные. (5).xlsx";
                outputPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\результат_анализа_МЭШ.xlsx";
                averageScoresFolder = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\средние баллы";
                contingentFilePath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\Реестр контингента.xlsx";
                System.out.println("Выбрана школа 1811");
                break;

            case 2:
                folderPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Анализ журнала";
                excludedStudentsFile = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Анализ журнала\\Учащиеся. Только отчисленные. (5).xlsx";
                outputPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Анализ журнала\\результат_анализа_МЭШ.xlsx";
                averageScoresFolder = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Анализ журнала\\средние баллы";
                contingentFilePath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ 7\\Реестр контингента.xlsx";
                System.out.println("Выбрана школа 7");
                break;

            default:
                System.out.println("Некорректный выбор. По умолчанию выбрана школа 1811");
                folderPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\20251206";
                excludedStudentsFile = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\Учащиеся. Только отчисленные. (5).xlsx";
                outputPath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\результат_анализа_МЭШ.xlsx";
                averageScoresFolder = "C:\\Users\\dimah\\Yandex.Disk\\1811\\для программ\\Анализ журнала\\средние баллы";
                contingentFilePath = "C:\\Users\\dimah\\Yandex.Disk\\1811\\Реестр контингента.xlsx";
        }

        scanner.close();

        try {
            System.out.println("Запуск анализа...");
            System.out.println("Папка с журналами: " + folderPath);
            System.out.println("Папка со средними баллами: " + averageScoresFolder);
            System.out.println("Файл отчисленных: " + excludedStudentsFile);
            System.out.println("Файл контингента: " + contingentFilePath);

            Set<String> excludedStudents = loadExcludedStudents(excludedStudentsFile);
            System.out.println("Загружено отчисленных учащихся: " + excludedStudents.size());

            Map<String, Map<String, Double>> averageScoresData = loadAverageScoresData(averageScoresFolder);
            System.out.println("Загружено данных о средних баллах для " + averageScoresData.size() + " студентов");

            // Загружаем эталонные данные из контингента
            Map<String, String> contingentMap = loadContingentData(contingentFilePath);
            System.out.println("Загружено эталонных записей из контингента: " + contingentMap.size());

            analyzeJournals(folderPath, excludedStudents, averageScoresData, outputPath, contingentMap);
            System.out.println("Анализ завершен! Результат сохранен в: " + outputPath);
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении анализа:");
            e.printStackTrace();
        }
    }

    // ===== 1. Загрузка эталонных данных из контингента =====
    public static Map<String, String> loadContingentData(String filePath) throws Exception {
        Map<String, String> contingentMap = new HashMap<>();

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Внимание: Файл контингента не найден: " + filePath);
            return contingentMap;
        }

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println("Обработка файла контингента: " + filePath);
            System.out.println("Всего строк в файле: " + sheet.getLastRowNum());

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Cell nameCell = row.getCell(2);  // Колонка C (индекс 2) - ФИО
                Cell classCell = row.getCell(15); // Колонка P (индекс 15) - Класс

                if (nameCell != null && classCell != null) {
                    String fullName = getCellValueAsString(nameCell).trim();
                    String className = getCellValueAsString(classCell).trim();

                    if (!fullName.isEmpty() && !className.isEmpty()) {
                        // Нормализуем ФИО и класс
                        String normalizedName = normalizeContingentName(fullName);
                        String normalizedClass = normalizeClassName(className);

                        if (!normalizedName.isEmpty() && !normalizedClass.equals("Неизвестный класс")) {
                            // Ключ: Фамилия Имя Класс (всегда в одном формате)
                            String key = normalizedName + " (" + normalizedClass + ")";
                            // Значение: оригинальное полное ФИО
                            contingentMap.put(key, fullName);

                            if (rowNum <= 5) { // Выводим первые 5 записей для проверки
                                System.out.println("  Загружено: " + key + " -> " + fullName);
                            }
                        }
                    }
                }

                if (rowNum % 100 == 0) {
                    System.out.println("  Обработано строк: " + rowNum);
                }
            }

            System.out.println("Итого загружено записей из контингента: " + contingentMap.size());
        }
        return contingentMap;
    }

    private static String normalizeContingentName(String fullName) {
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");

        if (parts.length >= 2) {
            // Берем только фамилию и имя (первые две части)
            String lastName = capitalizeWord(parts[0]);
            String firstName = capitalizeWord(parts[1]);
            return lastName + " " + firstName;
        }

        // Если только одно слово, возвращаем как есть
        return capitalizeWord(normalized);
    }

    // ===== 2. Загрузка отчисленных студентов =====
    public static Set<String> loadExcludedStudents(String filePath) throws Exception {
        Set<String> excludedStudents = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Cell nameCell = row.getCell(0);
                if (nameCell == null) continue;

                String studentName = getCellValueAsString(nameCell).trim();
                if (!studentName.isEmpty()) {
                    String normalizedName = normalizeToFirstNameLastName(studentName);
                    excludedStudents.add(normalizedName);
                }
            }
        }

        return excludedStudents;
    }

    // ===== 3. Загрузка данных о средних баллах =====
    public static Map<String, Map<String, Double>> loadAverageScoresData(String folderPath) throws Exception {
        Map<String, Map<String, Double>> averageScoresData = new HashMap<>();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Внимание: Папка со средними баллами не найдена: " + folderPath);
            return averageScoresData;
        }

        Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.toString().toLowerCase();
                    return fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
                })
                .forEach(filePath -> {
                    try {
                        System.out.println("Обрабатываем файл средних баллов: " + filePath.getFileName());
                        processAverageScoresFile(filePath.toString(), averageScoresData);
                    } catch (Exception e) {
                        System.err.println("Ошибка при обработке файла средних баллов: " + filePath);
                        e.printStackTrace();
                    }
                });

        return averageScoresData;
    }

    private static void processAverageScoresFile(String filePath, Map<String, Map<String, Double>> averageScoresData) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            String className = "Неизвестный класс";
            Row classRow = sheet.getRow(2);
            if (classRow != null) {
                Cell classCell = classRow.getCell(0);
                if (classCell != null) {
                    String cellValue = getCellValueAsString(classCell).trim();
                    if (cellValue.startsWith("Класс:")) {
                        className = cellValue.replace("Класс:", "").trim();
                        className = normalizeClassName(className);
                    }
                }
            }

            if (className.equals("Неизвестный класс")) {
                className = extractClassNameFromFileName(new File(filePath).getName());
                System.out.println("Используем класс из имени файла: " + className);
            }

            System.out.println("Обрабатываем средние баллы для класса: " + className);

            int subjectRowIndex = 5;
            Row subjectRow = sheet.getRow(subjectRowIndex);
            if (subjectRow == null) {
                System.out.println("Не найдена строка с предметами (строка 6) в файле: " + new File(filePath).getName());
                return;
            }

            Map<Integer, String> subjectMap = new HashMap<>();
            for (int col = 1; col <= subjectRow.getLastCellNum(); col++) {
                Cell cell = subjectRow.getCell(col);
                if (cell != null) {
                    String subjectName = getCellValueAsString(cell).trim();
                    if (!subjectName.isEmpty() && !subjectName.equals("0.00")) {
                        subjectMap.put(col, normalizeSubjectName(subjectName));
                    }
                }
            }

            System.out.println("Найдено предметов: " + subjectMap.size());

            for (int rowNum = 6; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row studentRow = sheet.getRow(rowNum);
                if (studentRow == null) continue;

                Cell nameCell = studentRow.getCell(0);
                if (nameCell == null) continue;

                String studentName = getCellValueAsString(nameCell).trim();
                if (studentName.isEmpty() || studentName.equals("0.00") || studentName.equals("Обучающийся")) {
                    continue;
                }

                String normalizedStudentName = normalizeStudentNameFromExport(studentName);
                // Ключ для средних баллов: Фамилия Имя (Класс)
                String studentKeyForMatching = normalizedStudentName + " (" + className + ")";

                Map<String, Double> studentAverages = new HashMap<>();
                for (Map.Entry<Integer, String> entry : subjectMap.entrySet()) {
                    int colNum = entry.getKey();
                    String subject = entry.getValue();

                    Cell scoreCell = studentRow.getCell(colNum);
                    if (scoreCell != null) {
                        String scoreStr = getCellValueAsString(scoreCell).trim();
                        if (!scoreStr.isEmpty() && !scoreStr.equals("0.00") && !scoreStr.equals("0")) {
                            try {
                                String normalizedScore = scoreStr.replace(',', '.');
                                double score = Double.parseDouble(normalizedScore);
                                score = Math.round(score * 100.0) / 100.0;
                                if (score > 0) {
                                    studentAverages.put(subject, score);
                                }
                            } catch (NumberFormatException e) {
                                // Игнорируем некорректные значения
                            }
                        }
                    }
                }

                if (!studentAverages.isEmpty()) {
                    averageScoresData.put(studentKeyForMatching, studentAverages);
                    // System.out.println("Загружены средние баллы для: " + normalizedStudentName);
                }
            }

            System.out.println("Успешно обработан файл: " + new File(filePath).getName());
        }
    }

    // ===== 4. Основной метод анализа журналов =====
    public static void analyzeJournals(String folderPath,
                                       Set<String> excludedStudents,
                                       Map<String, Map<String, Double>> averageScoresData,
                                       String outputPath,
                                       Map<String, String> contingentMap) throws Exception {

        Map<String, StudentFullInfo> studentFullInfoMap = new TreeMap<>();
        Set<String> allSubjects = new TreeSet<>();
        Set<String> allClasses = new TreeSet<>();

        System.out.println("\nНачинаем обработку журналов из папки: " + folderPath);

        Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".xlsx"))
                .forEach(filePath -> {
                    try {
                        processExcelFile(filePath.toString(), studentFullInfoMap,
                                allSubjects, allClasses, excludedStudents,
                                averageScoresData, contingentMap);
                    } catch (Exception e) {
                        System.err.println("Ошибка при обработке файла: " + filePath);
                        e.printStackTrace();
                    }
                });

        System.out.println("\nОбработка завершена. Создание итогового файла...");
        createResultExcel(studentFullInfoMap, allSubjects, allClasses, outputPath);
    }

    // ===== 5. Метод поиска совпадения в контингенте =====
    private static String findMatchingKey(String studentNameFromJournal,
                                          String classNameFromJournal,
                                          Map<String, String> contingentMap) {
        // Нормализуем данные из журнала
        String normalizedName = normalizeToFirstNameLastName(studentNameFromJournal);
        String normalizedClass = normalizeClassName(classNameFromJournal);

        // Удаляем лишние пробелы
        normalizedName = normalizedName.replaceAll("\\s+", " ").trim();
        normalizedClass = normalizedClass.replaceAll("\\s+", " ").trim();

        // Пробуем точное совпадение
        String exactKey = normalizedName + " (" + normalizedClass + ")";
        if (contingentMap.containsKey(exactKey)) {
            // System.out.println("Точное совпадение: " + exactKey);
            return exactKey;
        }

        // Если точного совпадения нет, ищем по частичному совпадению
        // Сначала ищем по комбинации имени и класса
        for (String contingentKey : contingentMap.keySet()) {
            // Проверяем, содержит ли ключ контингента и имя и класс
            if (contingentKey.contains(normalizedName) &&
                    contingentKey.contains(normalizedClass)) {
                System.out.println("Частичное совпадение найдено: " +
                        studentNameFromJournal + " класс " + classNameFromJournal +
                        " -> " + contingentKey);
                return contingentKey;
            }
        }

        // Пробуем искать только по имени (без класса)
        for (String contingentKey : contingentMap.keySet()) {
            if (contingentKey.startsWith(normalizedName + " (")) {
                System.out.println("Совпадение по имени (разные классы): " +
                        studentNameFromJournal + " -> " + contingentKey);
                return contingentKey;
            }
        }

        // Если совпадений нет, создаем ключ из данных журнала
        System.out.println("ВНИМАНИЕ: Нет совпадения в контингенте для: " +
                normalizedName + " класс " + normalizedClass);
        return exactKey;
    }

    // ===== 6. Обработка файла Excel =====
    private static void processExcelFile(String filePath,
                                         Map<String, StudentFullInfo> studentFullInfoMap,
                                         Set<String> allSubjects,
                                         Set<String> allClasses,
                                         Set<String> excludedStudents,
                                         Map<String, Map<String, Double>> averageScoresData,
                                         Map<String, String> contingentMap) throws Exception {

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            String className = extractClassNameFromSheet(workbook);
            allClasses.add(className);

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();

                if (sheetName.contains("Данные") || sheetName.contains("Лист")) {
                    continue;
                }

                String subject = extractSubject(sheet);
                allSubjects.add(subject);

                // System.out.println("Обрабатываем предмет: " + subject + " из класса: " + className +
                //         " из файла: " + new File(filePath).getName());

                processAllBlocks(sheet, studentFullInfoMap, subject, className,
                        excludedStudents, averageScoresData, contingentMap);
            }
        }
    }

    // ===== 7. Обработка всех блоков в листе =====
    private static void processAllBlocks(Sheet sheet,
                                         Map<String, StudentFullInfo> studentFullInfoMap,
                                         String subject,
                                         String className,
                                         Set<String> excludedStudents,
                                         Map<String, Map<String, Double>> averageScoresData,
                                         Map<String, String> contingentMap) {

        Map<String, StudentTempData> tempDataMap = new HashMap<>();
        Map<String, String> fullNameMap = new HashMap<>();

        for (int blockStart = 1; blockStart < 1000; blockStart += 50) {
            int dateRowNum = blockStart;
            int dataStartRow = blockStart + 1;
            int dataEndRow = blockStart + 48;

            Row dateRow = sheet.getRow(dateRowNum);
            if (dateRow == null) {
                continue;
            }

            int trimester1Col = findTrimesterColumnInRow(dateRow);
            boolean hasTrimesterGrade = (trimester1Col != -1);

            for (int rowNum = dataStartRow; rowNum <= dataEndRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Cell nameCell = row.getCell(1);
                if (nameCell == null) continue;

                String studentName = getCellValueAsString(nameCell);
                if (studentName == null || studentName.trim().isEmpty() ||
                        isHeaderRow(studentName)) {
                    continue;
                }

                String fullName = studentName.trim().replaceAll("\\s+", " ");

                String shortName = normalizeToFirstNameLastName(fullName);
                if (isExcludedStudent(shortName, excludedStudents)) {
                    // System.out.println("Пропускаем отчисленного студента: " + fullName + " из класса: " + className);
                    continue;
                }

                // Ищем ключ в контингенте
                String studentKeyForMatching = findMatchingKey(fullName, className, contingentMap);

                // Получаем полное ФИО из контингента или используем из журнала
                String fullNameFromContingent = contingentMap.getOrDefault(studentKeyForMatching, fullName);
                fullNameMap.put(studentKeyForMatching, fullNameFromContingent);

                StudentTempData tempData = tempDataMap.get(studentKeyForMatching);
                if (tempData == null) {
                    tempData = new StudentTempData();
                    tempDataMap.put(studentKeyForMatching, tempData);
                }

                if (hasTrimesterGrade && trimester1Col != -1) {
                    Cell trimesterCell = row.getCell(trimester1Col);
                    if (trimesterCell != null) {
                        String trimesterGrade = getTrimesterGradeValue(trimesterCell);
                        if (tempData.trimester1Grade.isEmpty()) {
                            tempData.trimester1Grade = trimesterGrade;
                        }
                    }
                }

                int endColForGrades = hasTrimesterGrade ? trimester1Col - 1 : 18;
                int absencesInRow = processGradesInRow(row, tempData.grades, endColForGrades);
                tempData.absences += absencesInRow;
            }
        }

        for (Map.Entry<String, StudentTempData> entry : tempDataMap.entrySet()) {
            String studentKeyForMatching = entry.getKey();
            StudentTempData tempData = entry.getValue();

            if (!tempData.grades.isEmpty() || !tempData.trimester1Grade.isEmpty() || tempData.absences > 0) {
                // Получаем полное имя из map
                String fullNameFromContingent = fullNameMap.get(studentKeyForMatching);
                if (fullNameFromContingent == null) {
                    // Извлекаем имя из ключа
                    int parenIndex = studentKeyForMatching.indexOf('(');
                    String namePart = parenIndex > 0 ?
                            studentKeyForMatching.substring(0, parenIndex).trim() :
                            studentKeyForMatching;
                    fullNameFromContingent = namePart;
                }

                StudentFullInfo studentInfo = studentFullInfoMap.get(studentKeyForMatching);
                if (studentInfo == null) {
                    studentInfo = new StudentFullInfo(fullNameFromContingent, studentKeyForMatching, className);
                    studentFullInfoMap.put(studentKeyForMatching, studentInfo);
                }

                double average = tempData.grades.isEmpty() ? 0.0 :
                        tempData.grades.stream().mapToInt(Integer::intValue).average().orElse(0.0);

                double averageFromExport = 0.0;
                if (averageScoresData.containsKey(studentKeyForMatching)) {
                    Map<String, Double> studentAverages = averageScoresData.get(studentKeyForMatching);
                    String normalizedSubject = normalizeSubjectNameForMatching(subject);
                    if (studentAverages.containsKey(normalizedSubject)) {
                        averageFromExport = studentAverages.get(normalizedSubject);
                        // System.out.println("Найдены данные выгрузки для " + fullNameFromContingent + " по предмету " + subject + ": " + averageFromExport);
                    } else {
                        for (Map.Entry<String, Double> avgEntry : studentAverages.entrySet()) {
                            String exportSubject = avgEntry.getKey();
                            if (exportSubject.contains(normalizedSubject) ||
                                    normalizedSubject.contains(exportSubject) ||
                                    subjectsMatch(exportSubject, normalizedSubject)) {
                                averageFromExport = avgEntry.getValue();
                                // System.out.println("Найдены данные выгрузки (частичное совпадение) для " + fullNameFromContingent +
                                //         ": " + exportSubject + " -> " + normalizedSubject + ": " + averageFromExport);
                                break;
                            }
                        }
                    }
                }

                String expectedGrade = calculateExpectedGrade(average);
                String error = checkGradeError(tempData.trimester1Grade, expectedGrade, average, averageFromExport);

                studentInfo.subjects.put(subject, new StudentSubjectData(
                        className, subject, average, tempData.grades.size(),
                        tempData.trimester1Grade, expectedGrade, error, tempData.absences,
                        averageFromExport
                ));
            }
        }
    }

    // ===== 8. Создание итогового Excel файла =====
    private static void createResultExcel(Map<String, StudentFullInfo> studentFullInfoMap,
                                          Set<String> allSubjects,
                                          Set<String> allClasses,
                                          String outputPath) throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Результаты анализа");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle errorStyle = createErrorStyle(workbook);
            CellStyle azStyle = createAZStyle(workbook);
            CellStyle warningStyle = createWarningStyle(workbook);
            CellStyle exportStyle = createExportStyle(workbook);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(25);

            String[] headers = {"Класс", "ФИО студента", "Предмет",
                    "Средний балл (расчет)", "Средний балл (выгрузка)", "Количество оценок",
                    "Оценка за 1Т", "Ожидаемая оценка", "Ошибка", "Пропуски"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            int totalRecords = 0;
            for (Map.Entry<String, StudentFullInfo> studentEntry : studentFullInfoMap.entrySet()) {
                StudentFullInfo studentInfo = studentEntry.getValue();
                Map<String, StudentSubjectData> subjects = studentInfo.subjects;

                // Используем полное ФИО из контингента
                String studentName = studentInfo.fullNameFromContingent;
                String className = studentInfo.className;

                for (Map.Entry<String, StudentSubjectData> subjectEntry : subjects.entrySet()) {
                    String subject = subjectEntry.getKey();
                    StudentSubjectData data = subjectEntry.getValue();

                    Row row = sheet.createRow(rowIndex++);
                    totalRecords++;

                    // Класс
                    Cell classCell = row.createCell(0);
                    classCell.setCellValue(className);
                    classCell.setCellStyle(textStyle);

                    // ФИО студента (полное из контингента)
                    Cell nameCell = row.createCell(1);
                    nameCell.setCellValue(studentName);
                    nameCell.setCellStyle(textStyle);

                    // Предмет
                    Cell subjectCell = row.createCell(2);
                    subjectCell.setCellValue(subject);
                    subjectCell.setCellStyle(textStyle);

                    // Средний балл (расчет)
                    Cell avgCalcCell = row.createCell(3);
                    if (data.average > 0) {
                        avgCalcCell.setCellValue(Math.round(data.average * 100.0) / 100.0);
                    } else {
                        avgCalcCell.setCellValue(0);
                    }
                    avgCalcCell.setCellStyle(numberStyle);

                    // Средний балл (выгрузка)
                    Cell avgExportCell = row.createCell(4);
                    if (data.averageFromExport > 0) {
                        avgExportCell.setCellValue(data.averageFromExport);
                        avgExportCell.setCellStyle(exportStyle);
                    } else {
                        avgExportCell.setCellValue("");
                        avgExportCell.setCellStyle(textStyle);
                    }

                    // Количество оценок
                    Cell countCell = row.createCell(5);
                    countCell.setCellValue(data.gradeCount);
                    countCell.setCellStyle(numberStyle);

                    // Оценка за 1Т
                    Cell grade1TCell = row.createCell(6);
                    grade1TCell.setCellValue(data.trimester1Grade);

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
                    Cell expectedCell = row.createCell(7);
                    expectedCell.setCellValue(data.expectedGrade);
                    expectedCell.setCellStyle(textStyle);

                    // Ошибка
                    Cell errorCell = row.createCell(8);
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
                    Cell absencesCell = row.createCell(9);
                    absencesCell.setCellValue(data.absences);
                    absencesCell.setCellStyle(numberStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex-1, 0, headers.length-1));

            System.out.println("Создан итоговый файл с " + totalRecords + " записями");

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    // ===== Вспомогательные методы (без изменений) =====

    private static String normalizeToFirstNameLastName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return fullName;
        }

        String normalized = fullName.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");

        if (parts.length >= 2) {
            String lastName = capitalizeWord(parts[0]);
            String firstName = capitalizeWord(parts[1]);
            return lastName + " " + firstName;
        }

        return normalized;
    }

    private static String normalizeStudentNameFromExport(String studentName) {
        if (studentName == null || studentName.isEmpty()) {
            return studentName;
        }

        String normalized = studentName.trim().replaceAll("\\s+", " ");

        String[] parts = normalized.split(" ");
        if (parts.length >= 2) {
            String lastName = capitalizeWord(parts[0]);
            String firstName = capitalizeWord(parts[1]);
            return lastName + " " + firstName;
        }

        return normalized;
    }

    private static String normalizeClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "Неизвестный класс";
        }

        String normalized = className.trim().replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[-–—]", "-");

        String[] parts = normalized.split("-");
        if (parts.length == 2) {
            String number = parts[0].trim();
            String letter = parts[1].trim().toUpperCase();
            return number + "-" + letter;
        }

        return normalized;
    }

    private static String extractClassNameFromFileName(String fileName) {
        Pattern pattern = Pattern.compile("(\\d+[-–—][А-Яа-я])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            String foundClass = matcher.group(1);
            foundClass = foundClass.replaceAll("[-–—]", "-");

            String[] parts = foundClass.split("-");
            if (parts.length == 2) {
                String number = parts[0];
                String letter = parts[1].toUpperCase();
                return number + "-" + letter;
            }
            return foundClass;
        }

        return "Неизвестный класс";
    }

    private static String normalizeSubjectName(String subjectName) {
        if (subjectName == null || subjectName.isEmpty()) {
            return subjectName;
        }

        String normalized = subjectName.trim();
        normalized = normalized.replaceAll("\\s+", " ");

        normalized = normalized.replace("Иностранный (английский) язык", "Английский язык");
        normalized = normalized.replace("Основы безопасности и защиты Родины", "ОБЖ");
        normalized = normalized.replace("Практикум по русскому языку", "Практикум русский язык");
        normalized = normalized.replace("Вероятность и статистика", "Вероятность и стат.");
        normalized = normalized.replace("Журналистика и медиа", "Журналистика");
        normalized = normalized.replace("Технологии медиапроизводства", "Технологии медиа");

        return normalized;
    }

    private static String normalizeSubjectNameForMatching(String subject) {
        if (subject == null || subject.isEmpty()) {
            return subject;
        }

        String normalized = subject.trim().toLowerCase();

        if (normalized.contains("алгебра") && normalized.contains("математического")) {
            return "Алгебра и начала математического анализа";
        }
        if (normalized.contains("английский")) {
            return "Английский язык";
        }
        if (normalized.contains("обж") || normalized.contains("безопасности")) {
            return "ОБЖ";
        }
        if (normalized.contains("русский") && normalized.contains("практикум")) {
            return "Практикум русский язык";
        }
        if (normalized.contains("вероятность")) {
            return "Вероятность и стат.";
        }
        if (normalized.contains("геометрия")) {
            return "Геометрия";
        }
        if (normalized.contains("биология")) {
            return "Биология";
        }
        if (normalized.contains("история")) {
            return "История";
        }
        if (normalized.contains("литература")) {
            return "Литература";
        }
        if (normalized.contains("физика")) {
            return "Физика";
        }
        if (normalized.contains("химия")) {
            return "Химия";
        }
        if (normalized.contains("география")) {
            return "География";
        }
        if (normalized.contains("обществознание")) {
            return "Обществознание";
        }
        if (normalized.contains("экономика")) {
            return "Экономика";
        }
        if (normalized.contains("журналистика")) {
            return "Журналистика";
        }
        if (normalized.contains("физическая культура")) {
            return "Физическая культура";
        }
        if (normalized.contains("технологии медиа")) {
            return "Технологии медиа";
        }

        return subject;
    }

    private static boolean subjectsMatch(String subject1, String subject2) {
        if (subject1 == null || subject2 == null) return false;

        String s1 = subject1.toLowerCase().trim();
        String s2 = subject2.toLowerCase().trim();

        if (s1.equals(s2)) return true;

        if (s1.contains(s2) || s2.contains(s1)) return true;

        Map<String, String> aliases = new HashMap<>();
        aliases.put("английский язык", "иностранный (английский) язык");
        aliases.put("иностранный (английский) язык", "английский язык");
        aliases.put("обж", "основы безопасности и защиты родины");
        aliases.put("основы безопасности и защиты родины", "обж");
        aliases.put("практикум русский язык", "практикум по русскому языку");
        aliases.put("практикум по русскому языку", "практикум русский язык");
        aliases.put("вероятность и стат.", "вероятность и статистика");
        aliases.put("вероятность и статистика", "вероятность и стат.");
        aliases.put("журналистика", "журналистика и медиа");
        aliases.put("журналистика и медиа", "журналистика");
        aliases.put("технологии медиа", "технологии медиапроизводства");
        aliases.put("технологии медиапроизводства", "технологии медиа");

        if (aliases.containsKey(s1) && aliases.get(s1).equals(s2)) return true;
        if (aliases.containsKey(s2) && aliases.get(s2).equals(s1)) return true;

        return false;
    }

    private static String extractClassNameFromSheet(Workbook workbook) {
        String className = "Неизвестный класс";

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);

            String cellValue = getCellValueFromPosition(sheet, 40, 20);
            if (cellValue != null && !cellValue.trim().isEmpty()) {
                className = extractClassNameFromCellValue(cellValue);
                if (!className.equals("Неизвестный класс")) {
                    return className;
                }
            }
        }

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);

            String cellValue = getCellValueFromPosition(sheet, 0, 0);
            if (cellValue != null && !cellValue.trim().isEmpty()) {
                String extracted = extractClassNameFromCellValue(cellValue);
                if (!extracted.equals("Неизвестный класс")) {
                    return extracted;
                }
            }

            cellValue = getCellValueFromPosition(sheet, 1, 0);
            if (cellValue != null && !cellValue.trim().isEmpty()) {
                String extracted = extractClassNameFromCellValue(cellValue);
                if (!extracted.equals("Неизвестный класс")) {
                    return extracted;
                }
            }
        }

        return className;
    }

    private static String getCellValueFromPosition(Sheet sheet, int rowNum, int colNum) {
        Row row = sheet.getRow(rowNum);
        if (row != null) {
            Cell cell = row.getCell(colNum);
            if (cell != null) {
                return getCellValueAsString(cell);
            }
        }
        return null;
    }

    private static String extractClassNameFromCellValue(String cellValue) {
        if (cellValue == null || cellValue.trim().isEmpty()) {
            return "Неизвестный класс";
        }

        String value = cellValue.trim();
        Pattern pattern = Pattern.compile("([0-9]+[-–—][А-Яа-яA-Za-z])");
        Matcher matcher = pattern.matcher(value);

        if (matcher.find()) {
            String foundClass = matcher.group(1);
            foundClass = foundClass.replaceAll("[-–—]", "-");

            String[] parts = foundClass.split("-");
            if (parts.length == 2) {
                String letter = parts[1];
                if (letter.length() == 1) {
                    letter = letter.toUpperCase();
                }
                return parts[0] + "-" + letter;
            }

            return foundClass;
        }

        return "Неизвестный класс";
    }

    private static String extractSubject(Sheet sheet) {
        String cellValue = getCellValueFromPosition(sheet, 40, 20);

        if (cellValue == null || cellValue.trim().isEmpty()) {
            return "Неизвестный предмет";
        }

        String value = cellValue.trim();

        String[] parts = value.split(", ");
        if (parts.length >= 2) {
            String subject = parts[parts.length - 1].trim();
            subject = cleanSubjectName(subject);

            return subject.isEmpty() ? "Неизвестный предмет" : subject;
        }

        return extractSubjectAlternative(sheet, value);
    }

    private static String extractSubjectAlternative(Sheet sheet, String cellValue) {
        String subject = "Неизвестный предмет";

        subject = extractSubjectFromCell(sheet, 0, 0, subject);
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 1, 0, subject);
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 0, 20, subject);
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = extractSubjectFromCell(sheet, 39, 0, subject);
        }
        if (subject.equals("Неизвестный предмет")) {
            subject = sheet.getSheetName();
        }

        subject = cleanSubjectName(subject);
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

    private static String cleanSubjectName(String subject) {
        if (subject == null || subject.isEmpty()) {
            return subject;
        }

        subject = subject.replaceAll("[0-9]+[-–—][А-Яа-яA-Za-z]\\s*[0-9]*[А-Яа-яA-Za-z]*\\s*группа", "");
        subject = subject.replaceAll(",\\s*[0-9]+[-–—][А-Яа-яA-Za-z]", "");
        subject = subject.replaceAll("\\s*[0-9]+[-–—][А-Яа-яA-Za-z]\\s*", " ");

        subject = subject.trim().replaceAll("\\s*,\\s*", ", ").replaceAll("\\s+", " ");

        if (subject.startsWith(",")) {
            subject = subject.substring(1).trim();
        }
        if (subject.endsWith(",")) {
            subject = subject.substring(0, subject.length() - 1).trim();
        }

        return subject.isEmpty() ? "Неизвестный предмет" : subject;
    }

    private static boolean isExcludedStudent(String studentName, Set<String> excludedStudents) {
        if (excludedStudents.isEmpty()) {
            return false;
        }

        String normalizedName = studentName.replaceAll("\\s+", " ").trim();

        if (excludedStudents.contains(normalizedName)) {
            return true;
        }

        for (String excludedName : excludedStudents) {
            if (normalizedName.contains(excludedName) || excludedName.contains(normalizedName)) {
                return true;
            }
        }

        return false;
    }

    private static int findTrimesterColumnInRow(Row dateRow) {
        if (dateRow == null) return -1;

        for (int colNum = 2; colNum <= 18; colNum++) {
            Cell cell = dateRow.getCell(colNum);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell).trim();
                if (cellValue.contains("1Т") || cellValue.contains("1П")) {
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
                "".equals(value) ||
                value.matches("^[\\d\\s]*$");
    }

    private static int processGradesInRow(Row row, List<Integer> grades, int endCol) {
        int absences = 0;
        int startCol = 2;

        if (endCol < startCol) {
            endCol = startCol;
        }

        for (int colNum = startCol; colNum <= endCol; colNum++) {
            Cell gradeCell = row.getCell(colNum);
            if (gradeCell == null) continue;

            switch (gradeCell.getCellType()) {
                case NUMERIC:
                    double gradeValue = gradeCell.getNumericCellValue();
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

    private static String checkGradeError(String actualGrade, String expectedGrade, double calculatedAverage, double exportedAverage) {
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
            int actual = Integer.parseInt(actualGrade);
            int expected = Integer.parseInt(expectedGrade);

            if (exportedAverage > 0) {
                String expectedFromExport = calculateExpectedGrade(exportedAverage);
                try {
                    int expectedExport = Integer.parseInt(expectedFromExport);
                    if (actual != expectedExport) {
                        return "ожидалось (из выгрузки): " + expectedFromExport +
                                ", выставлено: " + actualGrade +
                                " (расч.: " + expectedGrade + ")";
                    } else if (actual != expected) {
                        return "расч.: " + expectedGrade +
                                ", выгрузка: " + expectedFromExport +
                                ", выставлено: " + actualGrade;
                    }
                } catch (NumberFormatException e) {
                    return "расч.: " + expectedGrade +
                            ", выгрузка: " + expectedFromExport +
                            ", выставлено: " + actualGrade;
                }
            }

            if (actual != expected) {
                return "ожидалось: " + expectedGrade + ", выставлено: " + actualGrade;
            }
        } catch (NumberFormatException e) {
            if (!actualGrade.matches("\\d+")) {
                return "текстовая отметка: " + actualGrade;
            }
        }

        return "";
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

    private static CellStyle createExportStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static String capitalizeWord(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

    // ===== Классы данных =====

    static class StudentFullInfo {
        String fullNameFromContingent; // Эталонное ФИО из контингента
        String studentKeyForMatching;  // Ключ для сопоставления (Фамилия Имя (Класс))
        String className;
        Map<String, StudentSubjectData> subjects = new HashMap<>();

        StudentFullInfo(String fullNameFromContingent,
                        String studentKeyForMatching,
                        String className) {
            this.fullNameFromContingent = fullNameFromContingent;
            this.studentKeyForMatching = studentKeyForMatching;
            this.className = className;
        }
    }

    static class StudentTempData {
        List<Integer> grades = new ArrayList<>();
        int absences = 0;
        String trimester1Grade = "";
    }

    static class StudentSubjectData {
        String className;
        String subject;
        double average;
        int gradeCount;
        String trimester1Grade;
        String expectedGrade;
        String error;
        int absences;
        double averageFromExport;

        StudentSubjectData(String className, String subject, double average, int gradeCount,
                           String trimester1Grade, String expectedGrade, String error, int absences,
                           double averageFromExport) {
            this.className = className;
            this.subject = subject;
            this.average = average;
            this.gradeCount = gradeCount;
            this.trimester1Grade = trimester1Grade;
            this.expectedGrade = expectedGrade;
            this.error = error;
            this.absences = absences;
            this.averageFromExport = averageFromExport;
        }
    }
}