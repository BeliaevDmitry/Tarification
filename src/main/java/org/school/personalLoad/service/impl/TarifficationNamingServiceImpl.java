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

    // Индексы колонок (нумерация с 0) и ожидаемые заголовки
    private static final int COL_TEACHER = 0;     // A - ФИО педагога
    private static final int COL_BUILDING = 1;    // B - Корпус
    private static final int COL_SUBJECT = 2;     // C - Предмет
    private static final int COL_CLASS = 3;       // D - Класс
    private static final int COL_GROUP = 4;       // E - группа
    private static final int COL_NAME_UP = 5;     // F - Название по УП
    private static final int COL_FLAG = 6;        // G - схождение 1 есть 0 нет
    private static final int COL_NAME_MESH = 7;   // H - Название в МЭШ/тарификации

    // Ожидаемые заголовки столбцов
    private static final String[] EXPECTED_HEADERS = {
            "ФИО педагога", "Корпус", "Предмет", "Класс", "группа",
            "Название по УП", "схождение 1 есть 0 нет"
    };

    @Override
    public Map<String, String[]> loadNamingMapping(String excelFilePath) {
        Map<String, String[]> mapping = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet mappingSheet = findMappingSheet(workbook);
            if (mappingSheet == null) {
                throw new RuntimeException("Лист 'Соответствие' не найден в файле: " + excelFilePath);
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

            // Читаем остальные данные
            String teacher = getCellStringValue(row.getCell(COL_TEACHER));
            String subject = getCellStringValue(row.getCell(COL_SUBJECT));
            String className = getCellStringValue(row.getCell(COL_CLASS));
            String group = getCellStringValue(row.getCell(COL_GROUP));
            String nameUP = getCellStringValue(row.getCell(COL_NAME_UP));
            String flag = getCellStringValue(row.getCell(COL_FLAG));
            String nameMesh = getCellStringValue(row.getCell(COL_NAME_MESH));

            // Валидация флага
            if (!isValidFlag(flag)) {
                logger.error("Ошибка в строке {}: неверное значение флага '{}'. Допустимы: '0' или '1'",
                        rowNumber + 1, flag);
                errorRows++;
                continue;
            }

            // Валидация обязательных полей
            if (subject == null || subject.isEmpty() || className == null || className.isEmpty()) {
                logger.error("Ошибка в строке {}: отсутствуют обязательные поля (Предмет или Класс)",
                        rowNumber + 1);
                errorRows++;
                continue;
            }

            // Создаем ключ
            String key = createKey(building, subject, className, group);

            // Проверка на дубликаты
            if (mapping.containsKey(key)) {
                logger.warn("Дубликат ключа в строке {}: {}", rowNumber + 1, key);
            }

            // Сохраняем маппинг: [classNameMesh, groupNameMesh, флаг]
            mapping.put(key, new String[]{nameMesh, nameMesh, flag});
            validRows++;
            rowNumber++;
        }

        logger.info("Прочитано строк: {}, валидных: {}, с ошибками: {}",
                rowNumber, validRows, errorRows);
    }

    private boolean isValidFlag(String flag) {
        return "0".equals(flag) || "1".equals(flag);
    }

    private void applyMappingToPerson(TarifficationPerson person, String[] mappingValues) {
        String nameMesh = mappingValues[0];
        String flag = mappingValues[2];

        if (nameMesh == null || nameMesh.isEmpty()) {
            logger.warn("Пустое название МЭШ для: {}", createKey(person));
            return;
        }

        // Если флаг = "0" и группа пустая → используем для класса
        if ("0".equals(flag) &&
                (person.getGroupNameEducationalPlan() == null ||
                        person.getGroupNameEducationalPlan().isEmpty())) {
            person.setClassNameMesh(nameMesh);
            logger.debug("Установлено название класса МЭШ: {} для {}", nameMesh, createKey(person));
        }
        // Иначе используем для группы
        else {
            person.setGroupNameMesh(nameMesh);
            logger.debug("Установлено название группы МЭШ: {} для {}", nameMesh, createKey(person));
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