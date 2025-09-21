package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.*;
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
                             Map<String, List<String>> disabledStudentsGroups,
                             Map<String, GroupOrClassInfo> classInfo,
                             List<TarifficationChangesMesh> meshChanges,
                             List<String> listGroup) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            createTarifficationSheet(workbook, tarifficationList); //вывод тарификации
            createChangesSheet(workbook, changes); // вывод изменений тарификации
            createNamingMeshChangesSheet(workbook, meshChanges); //вывод изменений названий в МЭШ
            createUniqueNamesSheet(workbook, listGroup, classInfo); // вывод всех названий классов из МЭШ и их численность
            createDisabledStudentsSheet(workbook, disabledStudentsGroups); // вывод по каждому ИНВ/ОВЗ список его классов

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    /**
     * Создание листа для изменений связей УП-МЭШ (обновленная версия для TarifficationChangesMesh)
     */
    private void createNamingMeshChangesSheet(Workbook workbook, List<TarifficationChangesMesh> meshChanges) {
        if (meshChanges == null || meshChanges.isEmpty()) {
            System.out.println("ℹ️ Изменений связей УП-МЭШ не найдено, лист не создается");
            return;
        }

        Sheet sheet = workbook.createSheet("Изменения связей УП-МЭШ");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "ID изменения", "ФИО педагога", "Предмет", "Класс",
                "Группа УП (старая)", "Группа УП (новая)",
                "Группа МЭШ (старая)", "Группа МЭШ (новая)",
                "Нагрузка группы", "Тип изменения", "Дата изменения", "Краткое описание"
        };

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE);

        int rowNum = 1;
        int nameAddedCount = 0;
        int nameRemovedCount = 0;
        int nameModifiedCount = 0;
        int mappingAddedCount = 0;
        int mappingRemovedCount = 0;
        int mappingModifiedCount = 0;

        for (TarifficationChangesMesh change : meshChanges) {
            Row row = sheet.createRow(rowNum++);

            // Подсчет типов изменений
            switch (change.getMeshChangeType()) {
                case MESH_NAME_ADDED -> nameAddedCount++;
                case MESH_NAME_REMOVED -> nameRemovedCount++;
                case MESH_NAME_MODIFIED -> nameModifiedCount++;
                case MESH_MAPPING_ADDED -> mappingAddedCount++;
                case MESH_MAPPING_REMOVED -> mappingRemovedCount++;
                case MESH_MAPPING_MODIFIED -> mappingModifiedCount++;
            }

            row.createCell(0).setCellValue(change.getId() != null ? change.getId() : 0);
            row.createCell(1).setCellValue(change.getFioTeacher() != null ? change.getFioTeacher() : "");
            row.createCell(2).setCellValue(change.getSubjectName() != null ? change.getSubjectName() : "");
            row.createCell(3).setCellValue(change.getClassName() != null ? change.getClassName() : "");
            row.createCell(4).setCellValue(change.getOldGroupNameEducationalPlan() != null ? change.getOldGroupNameEducationalPlan() : "");
            row.createCell(5).setCellValue(change.getNewGroupNameEducationalPlan() != null ? change.getNewGroupNameEducationalPlan() : "");
            row.createCell(6).setCellValue(change.getOldGroupNameMesh() != null ? change.getOldGroupNameMesh() : "");
            row.createCell(7).setCellValue(change.getNewGroupNameMesh() != null ? change.getNewGroupNameMesh() : "");
            row.createCell(8).setCellValue(change.getGroupLoad() != null ? change.getGroupLoad() : 0);
            row.createCell(9).setCellValue(change.getMeshChangeTypeRussian());
            row.createCell(10).setCellValue(change.getChangeDate() != null ?
                    change.getChangeDate().format(dateFormatter) : "");
            row.createCell(11).setCellValue(change.getChangeSummary());
        }

        // Добавляем строки с итогами
        addSummaryRows(sheet, rowNum, nameAddedCount, nameRemovedCount, nameModifiedCount,
                mappingAddedCount, mappingRemovedCount, mappingModifiedCount, meshChanges.size(), workbook);

        autoSizeColumns(sheet, headers.length);

        System.out.println("📊 Создан лист изменений связей УП-МЭШ: " +
                nameAddedCount + " названий добавлено, " +
                nameRemovedCount + " названий удалено, " +
                nameModifiedCount + " названий изменено, " +
                mappingAddedCount + " связей добавлено, " +
                mappingRemovedCount + " связей удалено, " +
                mappingModifiedCount + " связей изменено");
    }

    /**
     * Добавление строк с итогами
     */
    private void addSummaryRows(Sheet sheet, int startRow, int nameAdded, int nameRemoved, int nameModified,
                                int mappingAdded, int mappingRemoved, int mappingModified, int total, Workbook workbook) {

        CellStyle summaryStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        summaryStyle.setFont(font);
        summaryStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle totalStyle = workbook.createCellStyle();
        totalStyle.setFont(font);
        totalStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Итоги по названиям
        Row nameSummaryRow = sheet.createRow(startRow++);
        nameSummaryRow.createCell(0).setCellValue("ИТОГО по названиям МЭШ:");
        nameSummaryRow.createCell(1).setCellValue("Добавлено: " + nameAdded);
        nameSummaryRow.createCell(2).setCellValue("Удалено: " + nameRemoved);
        nameSummaryRow.createCell(3).setCellValue("Изменено: " + nameModified);
        applyStyleToRow(nameSummaryRow, summaryStyle, 4);

        // Итоги по связям
        Row mappingSummaryRow = sheet.createRow(startRow++);
        mappingSummaryRow.createCell(0).setCellValue("ИТОГО по связям УП-МЭШ:");
        mappingSummaryRow.createCell(1).setCellValue("Добавлено: " + mappingAdded);
        mappingSummaryRow.createCell(2).setCellValue("Удалено: " + mappingRemoved);
        mappingSummaryRow.createCell(3).setCellValue("Изменено: " + mappingModified);
        applyStyleToRow(mappingSummaryRow, summaryStyle, 4);

        // Общий итог
        Row totalRow = sheet.createRow(startRow);
        totalRow.createCell(0).setCellValue("ВСЕГО ИЗМЕНЕНИЙ:");
        totalRow.createCell(1).setCellValue(total);
        applyStyleToRow(totalRow, totalStyle, 2);
    }

    /**
     * Применение стиля к ячейкам строки
     */
    private void applyStyleToRow(Row row, CellStyle style, int cellCount) {
        for (int i = 0; i < cellCount; i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                cell.setCellStyle(style);
            }
        }
    }

    /**
     * Извлекает класс МЭШ из описания или использует группу МЭШ
     */
    private String getClassNameMeshFromDescription(TarifficationChanges change) {
        if (change.getFioTeacher() != null && change.getFioTeacher().contains("Класс МЭШ")) {
            // Пытаемся извлечь из описания
            String description = change.getFioTeacher();
            java.util.regex.Matcher matcher = Pattern.compile("Класс МЭШ '([^']+)'").matcher(description);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        // Если не нашли в описании, используем группу МЭШ (обычно они совпадают)
        return change.getGroupNameMesh() != null ? change.getGroupNameMesh() : "";
    }

    private void createChangesSheet(Workbook workbook, List<TarifficationChanges> changes) {
        if (changes == null || changes.isEmpty()) {
            System.out.println("ℹ️ Изменений тарификации не найдено, лист не создается");
            return;
        }

        Sheet sheet = workbook.createSheet("Изменения тарификации");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "Группа",
                "Количество часов", "Часов в группе", "Тип изменения", "Дата изменения"};

        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_ORANGE);

        int rowNum = 1;
        int addedCount = 0;
        int removedCount = 0;
        int modifiedCount = 0;

        for (TarifficationChanges change : changes) {
            Row row = sheet.createRow(rowNum++);

            // Добавляем проверку на null
            Integer load = change.getLoad();
            Integer groupLoad = change.getGroupLoad();

            // Подсчет типов изменений
            switch (change.getChangeType()) {
                case ADDED -> addedCount++;
                case REMOVED -> removedCount++;
                case MODIFIED -> modifiedCount++;
            }

            row.createCell(0).setCellValue(change.getFioTeacher());
            row.createCell(1).setCellValue(change.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(change.getSubjectName());
            row.createCell(3).setCellValue(change.getClassName());
            row.createCell(4).setCellValue(change.getGroupNameEducationalPlan() != null ? change.getGroupNameEducationalPlan() : "");
            // Используем проверку на null
            row.createCell(5).setCellValue(load != null ? load : 0);
            row.createCell(6).setCellValue(groupLoad != null ? groupLoad : 0);

            row.createCell(7).setCellValue(change.getChangeTypeRussian());
            row.createCell(8).setCellValue(change.getChangeDate().format(dateFormatter));
        }

        // Добавляем строку с итогами
        Row summaryRow = sheet.createRow(rowNum++);
        summaryRow.createCell(0).setCellValue("ИТОГО:");
        summaryRow.createCell(1).setCellValue("Добавлено: " + addedCount);
        summaryRow.createCell(2).setCellValue("Удалено: " + removedCount);
        summaryRow.createCell(3).setCellValue("Изменено: " + modifiedCount);
        summaryRow.createCell(4).setCellValue("Всего: " + changes.size());

        // Стиль для итоговой строки
        CellStyle summaryStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        summaryStyle.setFont(font);
        summaryStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < 5; i++) {
            summaryRow.getCell(i).setCellStyle(summaryStyle);
        }

        autoSizeColumns(sheet, headers.length);

        System.out.println("📊 Создан лист изменений тарификации: " +
                addedCount + " добавлено, " +
                removedCount + " удалено, " +
                modifiedCount + " изменено");
    }

    private void createTarifficationSheet(Workbook workbook,
                                          List<TarifficationPerson> tarifficationList) {
        Sheet sheet = workbook.createSheet("Тарификация");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс по УП", "Группа по УП",
                 "Класс по МЭШ", "Группа по МЭШ", "Количество часов в группе", };

        createHeaderRow(headerRow, headers, workbook, IndexedColors.GREY_25_PERCENT);

        int rowNum = 1;
        for (TarifficationPerson record : tarifficationList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getFioTeacher());
            row.createCell(1).setCellValue(record.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(record.getSubjectName());
            row.createCell(3).setCellValue(record.getClassName());
            row.createCell(4).setCellValue(record.getGroupNameEducationalPlan());

            row.createCell(7).setCellValue(record.getGroupLoad());

            // Добавляем поля МЭШ
            row.createCell(5).setCellValue(record.getClassNameMesh() != null ? record.getClassNameMesh() : "");
            row.createCell(6).setCellValue(record.getGroupNameMesh() != null ? record.getGroupNameMesh() : "");
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("✅ Создан лист тарификации: " + (rowNum - 1) + " записей");
    }

    private void createGroupsSheet(Workbook workbook, List<SubjectWithGroup> subjectWithGroupList) {
        if (subjectWithGroupList == null || subjectWithGroupList.isEmpty()) {
            System.out.println("ℹ️ Групп не найдено, лист не создается");
            return;
        }

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
        System.out.println("✅ Создан лист групп: " + (rowNum - 1) + " записей");
    }

    private void createHeaderRow(Row headerRow, String[] headers, Workbook workbook, IndexedColors color) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(color.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setWrapText(true);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createUniqueNamesSheet(Workbook workbook, List<String> listGroup,
                                        Map<String, GroupOrClassInfo> classInfo) {

        // ЛИСТ 1: Классы из журналов МЭШ
        createMESHClassesSheet(workbook, classInfo);

        // ЛИСТ 2: Группы из УП
        //createUPGroupsSheet(workbook, listGroup);
    }

    /**
     * Лист с классами из журналов МЭШ
     */
    private void createMESHClassesSheet(Workbook workbook, Map<String, GroupOrClassInfo> classInfo) {
        if (classInfo == null || classInfo.isEmpty()) {
            System.out.println("ℹ️ Классов МЭШ не найдено, лист не создается");
            return;
        }

        Sheet sheet = workbook.createSheet("Названия групп и классов по МЭШ");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Классы из журналов МЭШ (оригинал)", "ФИО преподавателя",
                "Численность", "Класс (очищенный)", "Предмет"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_GREEN);

        int rowNum = 1;
        int processedCount = 0;
        int skippedCount = 0;

        System.out.println("Обрабатываем " + classInfo.size() + " классов из журналов МЭШ...");

        List<GroupOrClassInfo> sortedClasses = new ArrayList<>(classInfo.values());
        sortedClasses.sort((c1, c2) -> {
            String clean1 = extractCleanClassName(c1.getClassNameMesh());
            String clean2 = extractCleanClassName(c2.getClassNameMesh());
            return clean1.compareTo(clean2);
        });

        for (GroupOrClassInfo classInfoItem : sortedClasses) {
            String cleanClassName = extractCleanClassName(classInfoItem.getClassNameMesh());
            String subject = extractSubject(classInfoItem.getClassNameMesh());

            if (!cleanClassName.isEmpty()) {
                Row row = sheet.createRow(rowNum++);
                processedCount++;

                row.createCell(0).setCellValue(classInfoItem.getClassNameMesh());
                row.createCell(1).setCellValue(classInfoItem.getTeacherNameMesh() != null ?
                        classInfoItem.getTeacherNameMesh() : "");
                row.createCell(2).setCellValue(classInfoItem.getStudentCountMesh());
                row.createCell(3).setCellValue(cleanClassName);
                row.createCell(4).setCellValue(subject);
            } else {
                skippedCount++;
            }
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("✅ Создан лист классов МЭШ: " + processedCount + " обработано, " + skippedCount + " пропущено");
    }

    /**
     * Лист с группами из УП
     */
    private void createUPGroupsSheet(Workbook workbook, List<String> listGroup) {
        if (listGroup == null || listGroup.isEmpty()) {
            System.out.println("ℹ️ Групп УП не найдено, лист не создается");
            return;
        }

        Sheet sheet = workbook.createSheet("Названия групп и классов по УП");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Предмет", "Полное название группы/класса"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_BLUE);

        int rowNum = 1;

        System.out.println("Добавляем " + listGroup.size() + " групп из УП...");

        List<String> sortedGroups = new ArrayList<>(listGroup);
        sortedGroups.sort(String::compareToIgnoreCase);

        for (String group : sortedGroups) {
            Row row = sheet.createRow(rowNum++);

            String subject = extractSubject(group);
            row.createCell(0).setCellValue(subject);
            row.createCell(1).setCellValue(group);
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("✅ Создан лист групп УП: " + (rowNum - 1) + " записей");
    }

    private void createDisabledStudentsSheet(Workbook workbook, Map<String, List<String>> disabledStudentsGroups) {
        if (disabledStudentsGroups == null || disabledStudentsGroups.isEmpty()) {
            System.out.println("ℹ️ Инвалидов не найдено, лист не создается");
            return;
        }

        Sheet sheet = workbook.createSheet("Инвалиды и группы");
        sheet.createFreezePane(0, 1, 0, 1);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО Ребенка", "Группы"};
        createHeaderRow(headerRow, headers, workbook, IndexedColors.LIGHT_YELLOW);

        List<String> sortedStudents = new ArrayList<>(disabledStudentsGroups.keySet());
        sortedStudents.sort(String::compareToIgnoreCase);

        int rowNum = 1;
        for (String student : sortedStudents) {
            List<String> groups = disabledStudentsGroups.get(student);
            if (groups != null && !groups.isEmpty()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(student);
                String groupsString = String.join(", ", groups);
                row.createCell(1).setCellValue(groupsString);
            }
        }

        autoSizeColumns(sheet, headers.length);
        System.out.println("✅ Создан лист инвалидов: " + (rowNum - 1) + " записей");
    }

    // Вспомогательные методы (без изменений)
    private String extractSubject(String text) {
        if (text == null || text.isEmpty()) return "";
        java.util.regex.Matcher matcher = Pattern.compile("^([^0-9]+)").matcher(text);
        if (matcher.find()) {
            String subject = matcher.group(1).trim();
            subject = subject.replaceAll("\\s*(группа|класс|,|;|:|\\.)\\s*$", "");
            return subject;
        }
        String[] words = text.split("\\s+");
        return words.length > 0 ? words[0] : "";
    }

    private String extractCleanClassName(String className) {
        if (className == null || className.isEmpty()) return "";
        String[] patterns = {
                "\\b\\d{1,2}-[А-ЯA-Z]\\b", "\\b\\d{1,2}[А-ЯA-Z]\\b",
                "\\b\\d{1,2}-[а-яa-z]\\b", "\\b\\d{1,2}[а-яa-z]\\b",
                "\\b\\d{1,2}-[А-ЯA-Z][А-ЯA-Z]\\b", "\\b\\d{1,2}[А-ЯA-Z][А-ЯA-Z]\\b"
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = Pattern.compile(pattern).matcher(className);
            if (matcher.find()) {
                String found = matcher.group();
                if (found.contains("-")) {
                    String[] parts = found.split("-");
                    if (parts.length == 2) return parts[0] + "-" + parts[1].toUpperCase();
                } else {
                    String digits = found.replaceAll("[^0-9]", "");
                    String letters = found.replaceAll("[^А-ЯA-Zа-яa-z]", "").toUpperCase();
                    if (!digits.isEmpty() && !letters.isEmpty()) return digits + "-" + letters;
                }
                return found.toUpperCase();
            }
        }
        String[] words = className.split(" ");
        for (String word : words) {
            if (word.matches(".*\\d.*") && word.matches(".*[А-ЯA-Zа-яa-z].*")) {
                String digits = word.replaceAll("[^0-9]", "");
                String letters = word.replaceAll("[^А-ЯA-Zа-яa-z]", "").toUpperCase();
                if (!digits.isEmpty() && !letters.isEmpty()) return digits + "-" + letters;
            }
        }
        return "";
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            // Устанавливаем минимальную ширину для колонок
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000);
            }
        }
    }
}