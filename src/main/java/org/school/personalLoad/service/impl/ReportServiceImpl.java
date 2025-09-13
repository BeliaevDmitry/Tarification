package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.GroupOrClassInfo;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.ReportService;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ReportServiceImpl implements ReportService {

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public void createReport(List<TarifficationPerson> tarifficationList,
                             List<SubjectWithGroup> subjectWithGroupList,
                             List<TarifficationChanges> changes,
                             String outputPath,
                             List<String> listGroup,
                             Map<String, List<String>> disabledStudentsGroups,
                             Map<String, GroupOrClassInfo> classInfo) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            createTarifficationSheet(workbook, tarifficationList);
            createGroupsSheet(workbook, subjectWithGroupList);
            createChangesSheet(workbook, changes);
            createUniqueNamesSheet(workbook, listGroup, classInfo);
            createDisabledStudentsSheet(workbook, disabledStudentsGroups);
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createChangesSheet(Workbook workbook, List<TarifficationChanges> changes) {
        if (changes == null || changes.isEmpty()) return;

        Sheet sheet = workbook.createSheet("Изменения тарификации");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "Группа",
                "Количество часов", "Часов в группе", "Тип изменения", "Дата изменения"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_ORANGE);

        int rowNum = 1;
        for (TarifficationChanges change : changes) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(change.getFioTeacher());
            row.createCell(1).setCellValue(change.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(change.getSubjectName());
            row.createCell(3).setCellValue(change.getClassName());
            row.createCell(4).setCellValue(change.getGroupName() != null ? change.getGroupName() : "");
            row.createCell(5).setCellValue(change.getLoad());
            row.createCell(6).setCellValue(change.getGroupLoad() != null ? change.getGroupLoad() : 0);
            row.createCell(7).setCellValue(change.getChangeTypeRussian());
            row.createCell(8).setCellValue(change.getChangeDate().format(dateFormatter));
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createTarifficationSheet(Workbook workbook, List<TarifficationPerson> tarifficationList) {
        Sheet sheet = workbook.createSheet("Тарификация");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "группа", "Количество часов", "Количество часов в группе"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.GREY_25_PERCENT);

        int rowNum = 1;
        for (TarifficationPerson record : tarifficationList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getFioTeacher());
            row.createCell(1).setCellValue(record.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(record.getSubjectName());
            row.createCell(3).setCellValue(record.getClassName());
            row.createCell(4).setCellValue(record.getGroupName());
            row.createCell(5).setCellValue(record.getLoad());
            row.createCell(6).setCellValue(record.getGroupLoad());
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createGroupsSheet(Workbook workbook, List<SubjectWithGroup> subjectWithGroupList) {
        Sheet sheet = workbook.createSheet("Группы");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"корпус", "Предмет", "Класс", "Количество групп"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_BLUE);

        int rowNum = 1;
        for (SubjectWithGroup group : subjectWithGroupList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(group.getNumberSchoolBuilding());
            row.createCell(1).setCellValue(group.getSubjectName());
            row.createCell(2).setCellValue(group.getClassName());
            row.createCell(3).setCellValue("Деление на группы");
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createHeaderRow(Row headerRow, String[] headers, Workbook workbook, IndexedColors color) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(color.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createUniqueNamesSheet(Workbook workbook, List<String> listGroup,
                                        Map<String, GroupOrClassInfo> classInfo) {

        // ЛИСТ 1: Классы из журналов МЭШ
        createMESHClassesSheet(workbook, classInfo);

        // ЛИСТ 2: Группы из УП
        createUPGroupsSheet(workbook, listGroup);
    }

    /**
     * Лист с классами из журналов МЭШ
     */
    private void createMESHClassesSheet(Workbook workbook, Map<String, GroupOrClassInfo> classInfo) {
        Sheet sheet = workbook.createSheet("Названия групп и классов по МЭШ");
        sheet.createFreezePane(0, 1, 0, 1);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Классы из журналов МЭШ (оригинал)", "ФИО преподавателя",
                "Численность", "Класс (очищенный)", "Предмет"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_GREEN);

        int rowNum = 1;
        int processedCount = 0;
        int skippedCount = 0;

        if (classInfo != null && !classInfo.isEmpty()) {
            System.out.println("Обрабатываем " + classInfo.size() + " классов из журналов МЭШ...");

            // Сортируем классы для удобства чтения
            List<GroupOrClassInfo> sortedClasses = new ArrayList<>(classInfo.values());
            sortedClasses.sort((c1, c2) -> {
                String clean1 = extractCleanClassName(c1.getClassName());
                String clean2 = extractCleanClassName(c2.getClassName());
                return clean1.compareTo(clean2);
            });

            for (GroupOrClassInfo classInfoItem : sortedClasses) {
                String cleanClassName = extractCleanClassName(classInfoItem.getClassName());
                String subject = extractSubject(classInfoItem.getClassName());

                if (!cleanClassName.isEmpty()) {
                    Row row = sheet.createRow(rowNum++);
                    processedCount++;

                    row.createCell(0).setCellValue(classInfoItem.getClassName());
                    row.createCell(1).setCellValue(classInfoItem.getTeacherName() != null ?
                            classInfoItem.getTeacherName() : "");
                    row.createCell(2).setCellValue(classInfoItem.getStudentCount());
                    row.createCell(3).setCellValue(cleanClassName);
                    row.createCell(4).setCellValue(subject);
                } else {
                    skippedCount++;
                    System.out.println("Не удалось извлечь класс из: " + classInfoItem.getClassName());
                }
            }
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("Обработано классов МЭШ: " + processedCount + ", пропущено: " + skippedCount);
    }

    /**
     * Лист с группами из УП
     */
    private void createUPGroupsSheet(Workbook workbook, List<String> listGroup) {
        Sheet sheet = workbook.createSheet("Названия групп и классов по УП");
        sheet.createFreezePane(0, 1, 0, 1);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Предмет", "Полное название группы/класса"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_BLUE);

        int rowNum = 1;

        if (listGroup != null && !listGroup.isEmpty()) {
            System.out.println("Добавляем " + listGroup.size() + " групп из УП...");

            // Сортируем группы для удобства чтения
            List<String> sortedGroups = new ArrayList<>(listGroup);
            sortedGroups.sort(String::compareToIgnoreCase);

            for (String group : sortedGroups) {
                Row row = sheet.createRow(rowNum++);

                String subject = extractSubject(group);

                row.createCell(0).setCellValue(subject);
                row.createCell(1).setCellValue(group);
            }
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("Добавлено групп УП: " + (rowNum - 1));
    }

    /**
     * Метод для извлечения предмета из названия (извлекает все слова до цифр)
     * Примеры:
     * "Биология 10-К 10К группа, Биология" -> "Биология"
     * "Обществознание 9-А 9А группа" -> "Обществознание"
     * "Основы безопасности и защиты Родины 8-М группа" -> "Основы безопасности и защиты Родины"
     */
    private String extractSubject(String text) {
        if (text == null || text.isEmpty()) return "";

        // Ищем все слова до первой цифры
        java.util.regex.Matcher matcher = Pattern.compile("^([^0-9]+)").matcher(text);
        if (matcher.find()) {
            String subject = matcher.group(1).trim();

            // Убираем лишние слова в конце (группа, класс и т.д.)
            subject = subject.replaceAll("\\s*(группа|класс|,|;|:|\\.)\\s*$", "");

            return subject;
        }

        // Если не нашли цифр, возвращаем первое слово
        String[] words = text.split("\\s+");
        if (words.length > 0) {
            return words[0];
        }

        return "";
    }

    /**
     * Метод для очистки названия класса из журнала - возвращает ТОЛЬКО номер класса в формате "цифра-буква"
     */
    /**
     * Метод для очистки названия класса из журнала - возвращает ТОЛЬКО номер класса в формате "цифра-буква"
     */
    private String extractCleanClassName(String className) {
        if (className == null || className.isEmpty()) return "";

        // 1. Сначала пытаемся найти самые распространенные паттерны
        String[] patterns = {
                "\\b\\d{1,2}-[А-ЯA-Z]\\b",      // 10-А, 9-Б, 11-В
                "\\b\\d{1,2}[А-ЯA-Z]\\b",       // 10А, 9Б, 11В
                "\\b\\d{1,2}-[а-яa-z]\\b",      // 10-а, 9-б (строчные)
                "\\b\\d{1,2}[а-яa-z]\\b",       // 10а, 9б (строчные)
                "\\b\\d{1,2}-[А-ЯA-Z][А-ЯA-Z]\\b", // 10-АБ, 9-МГ
                "\\b\\d{1,2}[А-ЯA-Z][А-ЯA-Z]\\b"   // 10АБ, 9МГ
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = Pattern.compile(pattern)
                    .matcher(className);
            if (matcher.find()) {
                String found = matcher.group();

                // Приводим к стандартному формату: цифра-заглавная_буква
                if (found.contains("-")) {
                    String[] parts = found.split("-");
                    if (parts.length == 2) {
                        return parts[0] + "-" + parts[1].toUpperCase();
                    }
                } else {
                    // Разделяем цифры и буквы
                    String digits = found.replaceAll("[^0-9]", "");
                    String letters = found.replaceAll("[^А-ЯA-Zа-яa-z]", "").toUpperCase();
                    if (!digits.isEmpty() && !letters.isEmpty()) {
                        return digits + "-" + letters;
                    }
                }
                return found.toUpperCase();
            }
        }

        // 2. Если не нашли по паттернам, ищем вручную в строке
        String[] words = className.split(" ");
        for (String word : words) {
            if (word.matches(".*\\d.*") && word.matches(".*[А-ЯA-Zа-яa-z].*")) {
                // Извлекаем цифры и буквы
                String digits = word.replaceAll("[^0-9]", "");
                String letters = word.replaceAll("[^А-ЯA-Zа-яa-z]", "").toUpperCase();

                if (!digits.isEmpty() && !letters.isEmpty()) {
                    return digits + "-" + letters;
                }
            }
        }

        // 3. Если ничего не нашли, возвращаем пустую строку
        return "";
    }

    /**
     * Метод для извлечения класса из названия группы УП
     * Примеры:
     * "Иностранный (английский) язык 9-Ф 9Ф 1 гр" -> "9-Ф"
     * "Информатика 10-Б 10Б 2 гр" -> "10-Б"
     * "Математика 5А" -> "5А"
     */
    private String extractClassName(String fullGroupName) {
        if (fullGroupName == null || fullGroupName.isEmpty()) {
            return "";
        }

        // 1. Ищем паттерны с дефисом: 9-А, 10-Б, 11-В и т.д.
        java.util.regex.Matcher matcher = Pattern.compile("\\b\\d{1,2}-[А-ЯA-Z]\\b")
                .matcher(fullGroupName);
        if (matcher.find()) {
            String found = matcher.group();
            String[] parts = found.split("-");
            if (parts.length == 2) {
                return parts[0] + "-" + parts[1].toUpperCase();
            }
            return found;
        }

        // 2. Ищем паттерны без дефиса: 9А, 10Б, 11В и преобразуем в 9-А, 10-Б
        matcher = Pattern.compile("\\b\\d{1,2}[А-ЯA-Z]\\b")
                .matcher(fullGroupName);
        if (matcher.find()) {
            String found = matcher.group();
            String digits = found.replaceAll("[^0-9]", "");
            String letter = found.replaceAll("[^А-ЯA-Z]", "").toUpperCase();
            if (!digits.isEmpty() && !letter.isEmpty()) {
                return digits + "-" + letter;
            }
            return found;
        }

        // 3. Если не нашли, возвращаем пустую строку
        return "";
    }

    private void createDisabledStudentsSheet(Workbook workbook, Map<String, List<String>> disabledStudentsGroups) {
        if (disabledStudentsGroups == null || disabledStudentsGroups.isEmpty()) {
            return;
        }

        Sheet sheet = workbook.createSheet("Инвалиды и группы");
        sheet.createFreezePane(0, 1, 0, 1);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО Ребенка", "Группы"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_YELLOW);

        // Сортируем студентов по ФИО для удобства чтения
        List<String> sortedStudents = new ArrayList<>(disabledStudentsGroups.keySet());
        sortedStudents.sort(String::compareToIgnoreCase);

        int rowNum = 1;
        for (String student : sortedStudents) {
            List<String> groups = disabledStudentsGroups.get(student);
            if (groups != null && !groups.isEmpty()) {
                Row row = sheet.createRow(rowNum++);

                // Колонка 1: ФИО ребенка
                row.createCell(0).setCellValue(student);

                // Колонка 2: Все группы через запятую в одну ячейку
                String groupsString = String.join(", ", groups);
                row.createCell(1).setCellValue(groupsString);
            }
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}