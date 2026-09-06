package org.school.personalLoad.masterfot;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** Reads the grouped Master FOT export; totals and teacher headings are not load rows. */
@Component
public class FotParser {
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*/\\s*(20\\d{2})");
    private static final Pattern DATE = Pattern.compile("Состояние на\\s+(\\d{2}\\.\\d{2}\\.\\d{4})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    public FotDtos.Source parse(MultipartFile file, String year) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите выгрузку Мастер ФОТ");
        try (Workbook book = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = book.getSheet("Тарификация");
            if (sheet == null) throw new IllegalArgumentException("В файле нет листа «Тарификация»");
            DataFormatter fmt = new DataFormatter(Locale.forLanguageTag("ru"));
            String title = value(sheet, 1, 0, fmt);
            Matcher y = YEAR.matcher(title), date = DATE.matcher(title);
            if (!y.find() || !date.find()) throw new IllegalArgumentException("В заголовке нужны учебный год и «Состояние на ДД.ММ.ГГГГ»");
            String sourceYear = y.group(1) + "/" + y.group(2);
            if (!sourceYear.equals(year)) throw new IllegalArgumentException("Файл за " + sourceYear + ", выбран " + year);
            LocalDate snapshot = LocalDate.parse(date.group(1), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT));
            if (!sourceYear.equals((snapshot.getMonthValue() >= 9 ? snapshot.getYear() : snapshot.getYear() - 1) + "/" + (snapshot.getMonthValue() >= 9 ? snapshot.getYear() + 1 : snapshot.getYear())))
                throw new IllegalArgumentException("Дата выгрузки не относится к указанному учебному году");
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            List<FotDtos.SourceRow> rows = "Педагог".equals(value(sheet, 3, 0, fmt))
                    ? parseFlat(sheet, fmt, evaluator)
                    : parseGrouped(sheet, fmt, evaluator);
            return new FotDtos.Source(sourceYear, snapshot, title, rows);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Не удалось прочитать Excel Мастер ФОТ: " + ex.getMessage(), ex); }
    }

    private List<FotDtos.SourceRow> parseGrouped(Sheet sheet, DataFormatter fmt, FormulaEvaluator evaluator) {
            String[] header = {"Учебная группа", "Должность", "Часть УП", "Предмет"};
            for (int c = 0; c < header.length; c++) if (!header[c].equals(value(sheet, 3, c, fmt)))
                throw new IllegalArgumentException("Не распознан столбец " + (c + 1) + ": ожидается «" + header[c] + "»");
            for (int c = 4; c <= 6; c++) if (!List.of("Всего", "Назначено", "Не назначено").get(c - 4).equals(value(sheet, 4, c, fmt)))
                throw new IllegalArgumentException("Не распознаны колонки часов Мастер ФОТ");
            List<FotDtos.SourceRow> rows = new ArrayList<>();
            String teacher = "";
            BigDecimal sumTotal = BigDecimal.ZERO, sumAssigned = BigDecimal.ZERO, sumUnassigned = BigDecimal.ZERO;
            boolean footer = false;
            for (int i = 5; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String group = value(sheet, i, 0, fmt), subject = value(sheet, i, 3, fmt);
                if (group.equalsIgnoreCase("ОБЩИЙ ИТОГ")) {
                    if (footer) throw new IllegalArgumentException("В файле несколько общих итогов");
                    footer = true;
                    if (sumTotal.compareTo(number(row, 4, evaluator)) != 0 || sumAssigned.compareTo(number(row, 5, evaluator)) != 0 || sumUnassigned.compareTo(number(row, 6, evaluator)) != 0)
                        throw new IllegalArgumentException("Сумма часов строк не совпадает с общим итогом. Загрузите полную выгрузку");
                    continue;
                }
                if (group.toUpperCase(Locale.ROOT).startsWith("ИТОГ")) { teacher = ""; continue; }
                if (group.isBlank() && subject.isBlank()) continue;
                if (footer) throw new IllegalArgumentException("После общего итога обнаружены данные");
                if (subject.isBlank()) {
                    if (!value(sheet, i, 2, fmt).isBlank() || !value(sheet, i, 4, fmt).isBlank())
                        throw new IllegalArgumentException("Строка " + (i + 1) + ": не указан предмет");
                    teacher = group;
                    continue;
                }
                if (teacher.isBlank() || group.isBlank()) throw new IllegalArgumentException("Строка " + (i + 1) + ": не найдены педагог или учебная группа");
                String part = part(value(sheet, i, 2, fmt), i);
                BigDecimal total = number(row, 4, evaluator), assigned = number(row, 5, evaluator), unassigned = number(row, 6, evaluator);
                if (total.compareTo(assigned.add(unassigned)) != 0) throw new IllegalArgumentException("Строка " + (i + 1) + ": всего часов не равно назначенным и неназначенным");
                rows.add(new FotDtos.SourceRow(i + 1, teacher, group, part, subject, total, assigned, unassigned));
                sumTotal = sumTotal.add(total); sumAssigned = sumAssigned.add(assigned); sumUnassigned = sumUnassigned.add(unassigned);
            }
            if (rows.isEmpty() || !footer) throw new IllegalArgumentException("Нужна полная выгрузка с данными и строкой «ОБЩИЙ ИТОГ»");
            return rows;
    }

    private List<FotDtos.SourceRow> parseFlat(Sheet sheet, DataFormatter fmt, FormulaEvaluator evaluator) {
        String[] header = {"Педагог", "Учебная группа", "Должность", "Часть УП", "Предмет"};
        for (int c = 0; c < header.length; c++) if (!header[c].equals(value(sheet, 3, c, fmt)))
            throw new IllegalArgumentException("Не распознан столбец " + (c + 1) + ": ожидается «" + header[c] + "»");
        for (int c = 5; c <= 7; c++) if (!List.of("Всего", "Назначено", "Не назначено").get(c - 5).equals(value(sheet, 4, c, fmt)))
            throw new IllegalArgumentException("Не распознаны колонки часов Мастер ФОТ");
        if (!"ВСЕГО ПО ШКОЛЕ".equalsIgnoreCase(value(sheet, 5, 0, fmt)))
            throw new IllegalArgumentException("Не найдена строка «ВСЕГО ПО ШКОЛЕ»");

        List<FotDtos.SourceRow> rows = new ArrayList<>();
        BigDecimal sumTotal = BigDecimal.ZERO, sumAssigned = BigDecimal.ZERO, sumUnassigned = BigDecimal.ZERO;
        for (int i = 6; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String teacher = value(sheet, i, 0, fmt), group = value(sheet, i, 1, fmt);
            String subject = value(sheet, i, 4, fmt);
            if (teacher.isBlank() && group.isBlank() && subject.isBlank()) continue;
            if (teacher.isBlank() || group.isBlank() || subject.isBlank())
                throw new IllegalArgumentException("Строка " + (i + 1) + ": не найдены педагог, учебная группа или предмет");
            String part = part(value(sheet, i, 3, fmt), i);
            BigDecimal total = number(row, 5, evaluator), assigned = number(row, 6, evaluator), unassigned = number(row, 7, evaluator);
            if (total.compareTo(assigned.add(unassigned)) != 0)
                throw new IllegalArgumentException("Строка " + (i + 1) + ": всего часов не равно назначенным и неназначенным");
            rows.add(new FotDtos.SourceRow(i + 1, teacher, group, part, subject, total, assigned, unassigned));
            sumTotal = sumTotal.add(total); sumAssigned = sumAssigned.add(assigned); sumUnassigned = sumUnassigned.add(unassigned);
        }
        Row summary = sheet.getRow(5);
        if (rows.isEmpty() || sumTotal.compareTo(number(summary, 5, evaluator)) != 0
                || sumAssigned.compareTo(number(summary, 6, evaluator)) != 0
                || sumUnassigned.compareTo(number(summary, 7, evaluator)) != 0)
            throw new IllegalArgumentException("Сумма часов строк не совпадает с итогом по школе. Загрузите полную выгрузку");
        return rows;
    }

    private String part(String value, int row) {
        return switch (value) {
            case "Обязательная часть" -> "CORE";
            case "Часть, формируемая участниками образовательных отношений" -> "FORMABLE";
            case "Внеурочная деятельность" -> "EXTRACURRICULAR";
            case "Коррекционная работа", "Коррекционная часть" -> "CORRECTIONAL";
            default -> throw new IllegalArgumentException("Строка " + (row + 1) + ": неизвестная часть учебного плана");
        };
    }
    private String value(Sheet sheet, int row, int col, DataFormatter fmt) {
        Row r = sheet.getRow(row);
        return r == null ? "" : fmt.formatCellValue(r.getCell(col)).replace('\u00a0', ' ').trim();
    }
    private BigDecimal number(Row row, int col, FormulaEvaluator evaluator) {
        Cell cell = row == null ? null : row.getCell(col);
        try {
            if (cell == null || cell.getCellType() == CellType.BLANK) throw new IllegalArgumentException();
            CellType type = cell.getCellType();
            CellValue evaluated = type == CellType.FORMULA ? evaluator.evaluate(cell) : null;
            BigDecimal n;
            if (evaluated != null) {
                n = evaluated.getCellType() == CellType.NUMERIC
                        ? BigDecimal.valueOf(evaluated.getNumberValue())
                        : new BigDecimal(evaluated.getStringValue().replace("\u00a0", "").replace(" ", "").replace(',', '.'));
            } else {
                n = type == CellType.NUMERIC ? BigDecimal.valueOf(cell.getNumericCellValue())
                        : new BigDecimal(cell.getStringCellValue().replace("\u00a0", "").replace(" ", "").replace(',', '.'));
            }
            if (n.signum() < 0) throw new IllegalArgumentException();
            return n;
        } catch (Exception ex) { throw new IllegalArgumentException("Строка " + (row == null ? "?" : row.getRowNum() + 1) + ": неверные часы в столбце " + (col + 1)); }
    }
}
