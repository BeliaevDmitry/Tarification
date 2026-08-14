package org.school.ordergen.service;


import org.school.ordergen.config.AppConfig;
import org.school.ordergen.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Slf4j
@Service
public class ExcelReaderService {

    public List<Student> loadStudents() {
        String filePath = AppConfig.DATA_PATH + "/" + AppConfig.STUDENTS_FILE;
        log.info("Loading students from: {}", filePath);
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("File not found: {}", file.getAbsolutePath());
            return Collections.emptyList();
        }
        List<Student> students = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String fio = getCellString(row.getCell(0));
                String className = getCellString(row.getCell(1));
                String phone = getCellString(row.getCell(2));
                String parent = getCellString(row.getCell(3));
                if (fio.isEmpty() || className.isEmpty()) continue;
                students.add(Student.builder()
                        .fullName(fio.trim())
                        .className(className.trim())
                        .phone(phone)
                        .parentName(parent)
                        .build());
            }
            log.info("Loaded {} students", students.size());
        } catch (Exception e) {
            log.error("Ошибка чтения файла учеников", e);
        }
        return students;
    }

    public Map<String, ClassTeacher> loadClassTeachers() {
        log.info("Loading class teachers");
        Map<String, ClassTeacher> map = new HashMap<>();
        String filePath = AppConfig.DATA_PATH + "/" + AppConfig.TEACHERS_FILE;
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("File not found: {}", file.getAbsolutePath());
            return map;
        }
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheet("класс корпус");
            if (sheet == null) {
                log.error("Лист 'класс корпус' не найден");
                return map;
            }
            for (Row row : sheet) {
                if (row.getRowNum() < 0) continue; // пропускаем шапку
                String className = normalizeClassName(getCellString(row.getCell(0)));
                String fullName = getCellString(row.getCell(3));          // полное ФИО (именительный)
                String address = getCellString(row.getCell(4));
                String nominative = getCellString(row.getCell(5));        // именительный (если отдельно)
                String accusative = getCellString(row.getCell(6));        // винительный
                String dative = getCellString(row.getCell(7));            // дательный
                String teacherPhone = getCellString(row.getCell(8));

                if (className.isEmpty() || fullName.isEmpty()) continue;
                map.put(className, ClassTeacher.builder()
                        .className(className)
                        .fullName(fullName)
                        .buildingAddress(address)
                        .nominative(nominative.isEmpty() ? fullName : nominative)
                        .accusative(accusative.isEmpty() ? fullName : accusative)
                        .dative(dative.isEmpty() ? fullName : dative)
                        .teacherPhone(teacherPhone)
                        .build());
            }
            log.info("Loaded {} class teachers", map.size());
        } catch (Exception e) {
            log.error("Ошибка чтения классных руководителей", e);
        }
        return map;
    }

    private String normalizeClassName(String raw) {
        if (raw == null) return "";
        // Удаляем всё, кроме цифр и букв (русских и латинских)
        return raw.replaceAll("[^0-9а-яА-Яa-zA-Z]", "");
    }
    public Map<String, SchoolBuilding> loadBuildings() {
        log.info("Loading buildings");
        Map<String, SchoolBuilding> map = new HashMap<>();
        String filePath = AppConfig.DATA_PATH + "/" + AppConfig.TEACHERS_FILE;
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("File not found: {}", file.getAbsolutePath());
            return map;
        }
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheet("корпуса");
            if (sheet == null) {
                log.error("Лист 'корпуса' не найден");
                return map;
            }
            for (Row row : sheet) {
                if (row.getRowNum() < 1) continue;
                String curator = getCellString(row.getCell(3));
                String address = getCellString(row.getCell(1));
                if (address.isEmpty() || curator.isEmpty()) continue;
                map.put(address, SchoolBuilding.builder()
                        .address(address)
                        .curatorName(curator)
                        .build());
            }
            log.info("Loaded {} buildings", map.size());
        } catch (Exception e) {
            log.error("Ошибка чтения корпусов", e);
        }
        return map;
    }

    public List<Event> loadEvents() {
        log.info("Загрузка мероприятий из всех файлов в папке: {}", AppConfig.EVENTS_FOLDER);
        List<Event> allEvents = new ArrayList<>();
        List<File> files = getExcelFiles(AppConfig.EVENTS_FOLDER);
        for (File file : files) {
            try (Workbook wb = WorkbookFactory.create(file)) {
                Sheet sheet = wb.getSheet("Мероприятия");
                if (sheet == null) {
                    log.warn("Лист 'Мероприятия' не найден в файле {}", file.getName());
                    continue;
                }
                int eventsInFile = 0;
                for (Row row : sheet) {
                    if (row.getRowNum() < 1) continue; // пропускаем заголовок
                    String id = getCellString(row.getCell(0));
                    String name = getCellString(row.getCell(2));
                    String date = getCellString(row.getCell(4));
                    String timeRange = getCellString(row.getCell(5));
                    String organizer = getCellString(row.getCell(8));
                    String partner = getCellString(row.getCell(9));
                    String address = getCellString(row.getCell(10));
                    if (id.isEmpty() || date.isEmpty()) continue;
                    allEvents.add(Event.builder()
                            .id(id)
                            .name(name)
                            .date(date)
                            .timeRange(timeRange)
                            .organizer(organizer)
                            .partner(partner)
                            .address(address)
                            .build());
                    eventsInFile++;
                }
                log.info("Из файла {} загружено мероприятий: {}", file.getName(), eventsInFile);
            } catch (Exception e) {
                log.error("Ошибка чтения файла {}: {}", file.getName(), e.getMessage());
            }
        }
        log.info("Всего загружено мероприятий: {}", allEvents.size());
        return allEvents;
    }

    public List<Application> loadApplications() {
        log.info("Загрузка заявок из всех файлов в папке: {}", AppConfig.EVENTS_FOLDER);
        List<Application> allApps = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();          // для отслеживания уникальных (eventId + normalizedName)
        int totalDuplicates = 0;

        List<File> files = getExcelFiles(AppConfig.EVENTS_FOLDER);
        for (File file : files) {
            try (Workbook wb = WorkbookFactory.create(file)) {
                Sheet sheet = wb.getSheet("Заявки");
                if (sheet == null) {
                    log.warn("Лист 'Заявки' не найден в файле {}", file.getName());
                    continue;
                }

                // Читаем заголовки из первой строки
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    log.warn("Первая строка (заголовки) не найдена в файле {}", file.getName());
                    continue;
                }

                // Составляем карту "заголовок -> индекс колонки"
                Map<String, Integer> columnIndexes = new HashMap<>();
                for (Cell cell : headerRow) {
                    String headerValue = getCellString(cell).trim();
                    if (!headerValue.isEmpty()) {
                        columnIndexes.put(headerValue, cell.getColumnIndex());
                    }
                }

                // Список обязательных заголовков
                String[] requiredHeaders = {"ID события", "ФИО", "Класс", "Литтера класса"};
                boolean missingHeader = false;
                for (String header : requiredHeaders) {
                    if (!columnIndexes.containsKey(header)) {
                        log.warn("В файле {} отсутствует обязательный заголовок: {}", file.getName(), header);
                        missingHeader = true;
                    }
                }
                if (missingHeader) {
                    continue; // пропускаем файл, если не хватает колонок
                }

                int eventIdCol = columnIndexes.get("ID события");
                int studentNameCol = columnIndexes.get("ФИО");
                int classDigitCol = columnIndexes.get("Класс");
                int classLetterCol = columnIndexes.get("Литтера класса");

                int appsInFile = 0;
                int duplicatesInFile = 0;
                // Проходим по строкам, начиная со второй (индекс 1)
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // пропускаем строку заголовков

                    String eventId = getCellString(row.getCell(eventIdCol));
                    String studentName = getCellString(row.getCell(studentNameCol));
                    if (eventId.isEmpty() || studentName.isEmpty()) continue;

                    // Нормализуем ФИО для ключа (убираем лишние пробелы, приводим к нижнему регистру)
                    String normalizedName = normalizeFullNameForDeduplication(studentName);
                    String key = eventId + "|" + normalizedName;

                    // Проверяем уникальность
                    if (uniqueKeys.contains(key)) {
                        duplicatesInFile++;
                        totalDuplicates++;
                        log.debug("Дубликат заявки: событие {}, ученик {} (файл {})",
                                eventId, studentName, file.getName());
                        continue;
                    }
                    uniqueKeys.add(key);

                    String classDigit = getCellString(row.getCell(classDigitCol));
                    String classLetter = getCellString(row.getCell(classLetterCol));

                    allApps.add(Application.builder()
                            .eventId(eventId)
                            .studentName(studentName)      // сохраняем оригинальное написание
                            .classDigit(classDigit)
                            .classLetter(classLetter)
                            .build());
                    appsInFile++;
                }
                log.info("Из файла {} загружено заявок: {} (уникальных), пропущено дубликатов: {}",
                        file.getName(), appsInFile, duplicatesInFile);
            } catch (Exception e) {
                log.error("Ошибка чтения файла {}: {}", file.getName(), e.getMessage());
            }
        }
        log.info("Всего загружено уникальных заявок: {}, отброшено дубликатов: {}", allApps.size(), totalDuplicates);
        return allApps;
    }

    private String normalizeFullNameForDeduplication(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private List<File> getExcelFiles(String folderPath) {
        List<File> excelFiles = new ArrayList<>();
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.error("Папка не существует или не является директорией: {}", folderPath);
            return excelFiles;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
        if (files != null) {
            excelFiles.addAll(Arrays.asList(files));
        }
        log.info("Найдено Excel-файлов в папке {}: {}", folderPath, excelFiles.size());
        return excelFiles;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}