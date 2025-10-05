package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.TarifficationNamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Pattern;

public class TarifficationNamingServiceImpl implements TarifficationNamingService {

    private static final Logger logger = LoggerFactory.getLogger(TarifficationNamingServiceImpl.class);
    private static final Pattern SHEET_NAME_PATTERN = Pattern.compile("Тарификация");

    // Индексы колонок (нумерация с 0) для новой структуры
    private static final int COL_TEACHER = 0;        // A - ФИО педагога
    private static final int COL_BUILDING = 1;       // B - Корпус
    private static final int COL_SUBJECT = 2;        // C - Предмет
    private static final int COL_CLASS_UP = 3;       // D - Класс по УП
    private static final int COL_GROUP_UP = 4;       // E - Группа по УП
    private static final int COL_CLASS_MESH = 5;     // F - Класс по МЭШ
    private static final int COL_GROUP_MESH = 6;     // G - Группа по МЭШ
    private static final int COL_HOURS = 7;          // H - Количество часов в группе
    private static final int COL_FLAG = 8;           // I - схождение 1 есть 0 нет
    private static final int COL_CHANGE_GROUP = 9;   // J - Необходимо поменять номера групп
    private static final int COL_CHECK_MESH = 10;    // K - Проверка по МЭШ

    // Ожидаемые заголовки столбцов для новой структуры
    private static final String[] EXPECTED_HEADERS = {
            "ФИО педагога", "Корпус", "Предмет", "Класс по УП", "Группа по УП",
            "Класс по МЭШ", "Группа по МЭШ", "Количество часов в группе",
            "схождение 1 есть 0 нет", "Необходимо поменять номера групп", "Проверка по МЭШ"
    };

    @Override
    public Map<String, String[]> loadNamingMapping(String excelFilePath) {
        Map<String, String[]> mapping = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet mappingSheet = findMappingSheet(workbook);
            if (mappingSheet == null) {
                throw new RuntimeException("Лист 'тарификация' не найден в файле: " + excelFilePath);
            }

            // Валидация заголовков
            if (!validateHeaders(mappingSheet)) {
                throw new RuntimeException("Неверная структура файла маппинга. Проверьте заголовки столбцов.");
            }

            readMappingData(mappingSheet, mapping);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения файла маппинга: " + excelFilePath, e);
        }

        return mapping;
    }

    @Override
    public void applyNamingMapping(List<TarifficationPerson> list,
                                   Map<String, String[]> namingMapping) {
        logger.info("Начинаем применение маппинга названий к {} записям", list.size());
        int appliedCount = 0;

        for (TarifficationPerson person : list) {
            String key = createKey(person);
            String[] mappingValues = namingMapping.get(key);

            if (mappingValues != null) {
                applyMappingToPerson(person, mappingValues);
                appliedCount++;
            }
        }

        logger.info("Применен маппинг для {} записей", appliedCount);
    }

    private Sheet findMappingSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (SHEET_NAME_PATTERN.matcher(sheet.getSheetName()).find()) {
                logger.info("Найден лист для маппинга: {}", sheet.getSheetName());
                return sheet;
            }
        }
        return null;
    }

    private boolean validateHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            logger.error("Первая строка листа пустая");
            return false;
        }

        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            Cell cell = headerRow.getCell(i);
            String actualHeader = getCellStringValue(cell);

            if (!EXPECTED_HEADERS[i].equalsIgnoreCase(actualHeader)) {
                logger.error("Неверный заголовок в колонке {}. Ожидалось: '{}', получено: '{}'",
                        getColumnName(i), EXPECTED_HEADERS[i], actualHeader);
                return false;
            }
        }

        logger.info("Заголовки столбцов валидны");
        return true;
    }

    private void readMappingData(Sheet sheet, Map<String, String[]> mapping) {
        int rowNumber = 1; // начинаем с второй строки (после заголовка)
        int validRows = 0;
        int errorRows = 0;

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Читаем корпус (колонка B)
            String building = getCellStringValue(row.getCell(COL_BUILDING));

            // Если в колонке B пусто - заканчиваем обработку
            if (building == null || building.isEmpty()) {
                logger.info("Обнаружена пустая ячейка в колонке Корпус (строка {}), завершение чтения", i + 1);
                break;
            }

            // Читаем данные для ключа
            String subject = getCellStringValue(row.getCell(COL_SUBJECT));
            String classUP = getCellStringValue(row.getCell(COL_CLASS_UP));
            String groupUP = getCellStringValue(row.getCell(COL_GROUP_UP));

            // Читаем данные для маппинга - только Класс по МЭШ и Группа по МЭШ
            String classMesh = getCellStringValue(row.getCell(COL_CLASS_MESH));
            String groupMesh = getCellStringValue(row.getCell(COL_GROUP_MESH));

            // Валидация обязательных полей
            if (subject == null || subject.isEmpty() || classUP == null || classUP.isEmpty()) {
                logger.error("Ошибка в строке {}: отсутствуют обязательные поля (Предмет или Класс по УП)",
                        rowNumber + 1);
                errorRows++;
                continue;
            }

            // Создаем ключ из Корпус|Предмет|Класс по УП|Группа по УП
            String key = createKey(building, subject, classUP, groupUP);

            // Проверка на дубликаты
            if (mapping.containsKey(key)) {
                logger.warn("Дубликат ключа в строке {}: {}", rowNumber + 1, key);
            }

            // Сохраняем маппинг: [Класс по МЭШ, Группа по МЭШ]
            // Только два значения вместо трех
            mapping.put(key, new String[]{classMesh, groupMesh});
            validRows++;
            rowNumber++;
        }

        logger.info("Прочитано строк: {}, валидных: {}, с ошибками: {}",
                rowNumber, validRows, errorRows);
    }

    private void applyMappingToPerson(TarifficationPerson person, String[] mappingValues) {
        String classMesh = mappingValues[0];  // Класс по МЭШ
        String groupMesh = mappingValues[1];  // Группа по МЭШ

        // Устанавливаем оба значения
        if (classMesh != null && !classMesh.isEmpty()) {
            person.setClassNameMesh(classMesh);
            logger.debug("Установлено название класса МЭШ: {} для {}", classMesh, createKey(person));
        }

        if (groupMesh != null && !groupMesh.isEmpty()) {
            person.setGroupNameMesh(groupMesh);
            logger.debug("Установлено название группы МЭШ: {} для {}", groupMesh, createKey(person));
        }
    }

    private String createKey(TarifficationPerson person) {
        return createKey(person.getNumberSchoolBuilding(),
                person.getSubjectName(),
                person.getClassName(),
                person.getGroupNameEducationalPlan());
    }

    private String createKey(String building, String subject, String className, String group) {
        return String.format("%s|%s|%s|%s",
                normalizeValue(building),
                normalizeValue(subject),
                normalizeValue(className),
                normalizeValue(group != null ? group : ""));
    }

    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }

    private String getColumnName(int columnIndex) {
        return Character.toString((char) ('A' + columnIndex));
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numValue = cell.getNumericCellValue();
                        if (numValue == (int) numValue) {
                            return String.valueOf((int) numValue);
                        } else {
                            return String.valueOf(numValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        FormulaEvaluator evaluator = cell.getSheet().getWorkbook()
                                .getCreationHelper().createFormulaEvaluator();
                        CellValue cellValue = evaluator.evaluate(cell);

                        if (cellValue.getCellType() == CellType.NUMERIC) {
                            return String.valueOf((int) cellValue.getNumberValue());
                        } else if (cellValue.getCellType() == CellType.STRING) {
                            return cellValue.getStringValue();
                        } else if (cellValue.getCellType() == CellType.BOOLEAN) {
                            return String.valueOf(cellValue.getBooleanValue());
                        }
                    } catch (Exception e) {
                        return cell.getCellFormula();
                    }
                    return cell.getCellFormula();
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}