package org.school.personalLoad.service;

import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GroupSearchService {

    private final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * Единый метод для поиска групп для инвалидов
     */
    public Map<String, List<String>> findGroupsForDisabledStudents(String onlineFilePath,
                                                                   String offlineFolderPath) throws Exception {

        // 1. Читаем список инвалидов
        List<String> disabledStudents = readDisabledStudents(onlineFilePath);
        if (disabledStudents.isEmpty()) {
            System.out.println("⚠️ Не найдено студентов-инвалидов в файле");
            return Map.of();
        }

        // 2. Ищем группы
        return findGroupsInOfflineFiles(disabledStudents, offlineFolderPath);
    }

    private List<String> readDisabledStudents(String onlineFilePath) throws Exception {
        List<String> students = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(new File(onlineFilePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("Контингент");
            if (sheet == null) return students;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(10); // Столбец K
                if (cell != null) {
                    String name = getCellValueAsString(cell).trim();
                    if (!name.isEmpty()) students.add(name);
                }
            }
        }
        return students;
    }

    private Map<String, List<String>> findGroupsInOfflineFiles(List<String> studentNames,
                                                               String offlineFolderPath) throws Exception {
        Map<String, List<String>> result = new ConcurrentHashMap<>();
        studentNames.forEach(student -> result.put(student, new ArrayList<>()));

        File folder = new File(offlineFolderPath);
        File[] excelFiles = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"));

        if (excelFiles == null) return result;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (File file : excelFiles) {
            executor.submit(() -> processExcelFile(file, studentNames, result));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);

        return result;
    }

    private void processExcelFile(File file, List<String> studentNames,
                                  Map<String, List<String>> result) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                processSheet(workbook.getSheetAt(i), studentNames, result);
            }
        } catch (Exception e) {
            System.err.println("Ошибка в файле " + file.getName() + ": " + e.getMessage());
        }
    }

    private void processSheet(Sheet sheet, List<String> studentNames,
                              Map<String, List<String>> result) {
        String groupName = getCellValueAsString(sheet.getRow(40) == null ? null : sheet.getRow(40).getCell(20));
        if (groupName == null || groupName.isEmpty()) return;

        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            Cell cell = row.getCell(1); // Столбец B
            String studentName = getCellValueAsString(cell);
            if (studentName.isEmpty()) continue;

            studentNames.stream()
                    .filter(target -> isMatchingStudent(studentName, target))
                    .findFirst()
                    .ifPresent(target -> {
                        synchronized (result) {
                            if (!result.get(target).contains(groupName)) {
                                result.get(target).add(groupName);
                            }
                        }
                    });
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue().trim();
                case NUMERIC: return String.valueOf((int) cell.getNumericCellValue());
                case FORMULA:
                    try {
                        return String.valueOf((int) cell.getNumericCellValue());
                    } catch (Exception e) {
                        return cell.getStringCellValue().trim();
                    }
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isMatchingStudent(String foundName, String targetName) {
        return foundName.equalsIgnoreCase(targetName) ||
                foundName.contains(targetName) ||
                targetName.contains(foundName);
    }
}