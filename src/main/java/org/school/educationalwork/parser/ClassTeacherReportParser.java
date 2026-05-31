package org.school.educationalwork.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.ValidationIssue;
import org.school.educationalwork.model.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassTeacherReportParser {
    private static final int TABLE_COUNT = 13;
    private static final String ACADEMIC_YEAR = "2025-2026";
    private static final Pattern TITLE = Pattern.compile("[«\"]\\s*(.*?)\\s*[»\"]\\s*класса\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COUNT_PERCENT = Pattern.compile("^(\\d{1,3})\\s*(?:чел(?:овек)?[а-я]*\\s*)?/\\s*(\\d{1,3})\\s*%$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INTEGER = Pattern.compile("^\\d+$");
    private static final Pattern RESULT = Pattern.compile("^(приз[её]р|победитель)(?:\\s*[,;/]\\s*(приз[её]р|победитель))*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.uuuu", Locale.forLanguageTag("ru"));

    private final ClassNameNormalizer classNameNormalizer = new ClassNameNormalizer();
    private final FullNameValidator fullNameValidator = new FullNameValidator();

    public ValidationResult<ClassTeacherReport> parse(InputStream input) {
        List<ValidationIssue> issues = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(input)) {
            if (document.getTables().size() < TABLE_COUNT) {
                issues.add(error("TEMPLATE_TABLE_COUNT", "Документ", "Изменена структура шаблона.",
                        "Не менее 13 таблиц исходной формы.", String.valueOf(document.getTables().size())));
                return new ValidationResult<>(null, issues);
            }
            Header header = parseHeader(document.getParagraphs(), issues);
            validateHeaders(document.getTables(), issues);
            ClassTeacherReport.AcademicPerformance performance = parsePerformance(document.getTables().get(0), header.schoolClass(), issues);
            List<ClassTeacherReport.AcademicDebt> debts = parseDebts(document.getTables().get(1), issues);
            ClassTeacherReport.AdditionalEducation additionalEducation = parseAdditionalEducation(document.getTables().get(2), performance.studentCount(), issues);
            ClassTeacherReport.ActivityCounters counters = parseCounters(document.getTables().get(3), issues);
            List<ClassTeacherReport.StudentAchievement> achievements = parseAchievements(document.getTables().get(4), issues);
            List<ClassTeacherReport.SpecialProjectParticipation> projects = parseProjects(document.getTables(), issues);
            ClassTeacherReport.TeacherPortfolio portfolio = parseTeacherPortfolio(document.getTables().get(10));
            List<ClassTeacherReport.StaffRecognition> recognitions = parseRecognitions(document.getTables().get(11), issues);
            List<ClassTeacherReport.Diagnostic> diagnostics = parseDiagnostics(document.getTables().get(12), issues);
            ClassTeacherReport report = new ClassTeacherReport(ACADEMIC_YEAR, header.schoolClass(), header.teacherName(), performance,
                    debts, additionalEducation, counters, achievements, projects, portfolio, recognitions, diagnostics);
            return new ValidationResult<>(report, issues);
        } catch (IOException | RuntimeException e) {
            issues.add(error("DOCX_READ_ERROR", "Файл", "Не удалось прочитать DOCX-файл.",
                    "Исправный файл .docx, заполненный на основе выданного шаблона.", e.getMessage()));
            return new ValidationResult<>(null, issues);
        }
    }

    private Header parseHeader(List<XWPFParagraph> paragraphs, List<ValidationIssue> issues) {
        String titleLine = paragraphs.stream().map(XWPFParagraph::getText).map(DocxCells::clean)
                .filter(text -> text.toLowerCase(Locale.forLanguageTag("ru")).contains("класса"))
                .findFirst().orElse("");
        Matcher matcher = TITLE.matcher(titleLine);
        String rawClass = matcher.find() ? matcher.group(1) : "";
        String rawTeacher = matcher.find(0) ? matcher.group(2) : "";
        String schoolClass = classNameNormalizer.normalize(rawClass).orElseGet(() -> {
            issues.add(error("HEADER_CLASS", "Заголовок: класс", "Класс не распознан.",
                    "Например: «7А» класса Иванова Мария Петровна", rawClass));
            return rawClass;
        });
        String teacher = fullNameValidator.normalizeTeacher(rawTeacher).orElseGet(() -> {
            issues.add(error("HEADER_TEACHER", "Заголовок: ФИО классного руководителя", "ФИО учителя не заполнено или записано неверно.",
                    "Фамилия Имя Отчество, например: Иванова Мария Петровна", rawTeacher));
            return rawTeacher;
        });
        return new Header(schoolClass, teacher);
    }

    private ClassTeacherReport.AcademicPerformance parsePerformance(XWPFTable table, String headerClass, List<ValidationIssue> issues) {
        String classCell = DocxCells.cell(table, 1, 0);
        Optional<String> normalizedClass = classNameNormalizer.normalize(classCell);
        if (normalizedClass.isEmpty()) {
            issues.add(error("PERFORMANCE_CLASS", "Таблица 1, строка 2, столбец «Класс»", "Класс не распознан.", "7А", classCell));
        } else if (!headerClass.isBlank() && !normalizedClass.get().equals(headerClass)) {
            issues.add(error("CLASS_MISMATCH", "Таблица 1, столбец «Класс»", "Класс в таблице отличается от класса в заголовке.", headerClass, normalizedClass.get()));
        }
        Integer students = requiredInteger(DocxCells.cell(table, 1, 1), "Таблица 1, столбец «Количество учащихся»", issues);
        List<String> grade5 = validatePeople(DocxCells.cell(table, 1, 2), "Таблица 1, «Отметка 5»", issues);
        List<String> grade45 = validatePeople(DocxCells.cell(table, 1, 3), "Таблица 1, «Отметки 4, 5»", issues);
        List<String> one3 = validatePeople(DocxCells.cell(table, 1, 4), "Таблица 1, «С одной 3»", issues);
        List<String> grade34 = validatePeople(DocxCells.cell(table, 1, 5), "Таблица 1, «Отметки 3, 4»", issues);
        List<String> failing = validatePeople(DocxCells.cell(table, 1, 6), "Таблица 1, «Неуспевающие»", issues);
        return new ClassTeacherReport.AcademicPerformance(students == null ? 0 : students, grade5, grade45, one3, grade34, failing);
    }

    private List<ClassTeacherReport.AcademicDebt> parseDebts(XWPFTable table, List<ValidationIssue> issues) {
        List<ClassTeacherReport.AcademicDebt> rows = new ArrayList<>();
        for (int row = 1; row < table.getNumberOfRows(); row++) {
            if (DocxCells.emptyRow(table, row)) continue;
            String student = DocxCells.cell(table, row, 0);
            if (!fullNameValidator.isStudentName(student)) {
                issues.add(error("DEBT_STUDENT", "Таблица 2, строка " + (row + 1) + ", «ФИО обучающихся»", "Неверное ФИО обучающегося.", "Фамилия Имя или Фамилия Имя Отчество", student));
            }
            rows.add(new ClassTeacherReport.AcademicDebt(student, DocxCells.cell(table, row, 1), DocxCells.cell(table, row, 2), DocxCells.cell(table, row, 3), DocxCells.cell(table, row, 4)));
        }
        return rows;
    }

    private ClassTeacherReport.AdditionalEducation parseAdditionalEducation(XWPFTable table, int studentCount, List<ValidationIssue> issues) {
        int[] inside = requiredCountPercent(DocxCells.cell(table, 1, 0), "Таблица 3, «Охват ДО внутри школы»", issues);
        int[] outside = requiredCountPercent(DocxCells.cell(table, 1, 1), "Таблица 3, «Охват ДО вне школы»", issues);
        Integer noDo = optionalInteger(DocxCells.cell(table, 1, 2), "Таблица 3, «Не посещают ДО»", issues);
        if (studentCount > 0 && inside != null && inside[0] > studentCount) {
            issues.add(error("DO_COUNT_LIMIT", "Таблица 3, «Охват ДО внутри школы»", "Количество больше числа учащихся класса.", "От 0 до " + studentCount, String.valueOf(inside[0])));
        }
        return new ClassTeacherReport.AdditionalEducation(inside == null ? null : inside[0], inside == null ? null : inside[1], outside == null ? null : outside[0], outside == null ? null : outside[1], noDo);
    }

    private ClassTeacherReport.ActivityCounters parseCounters(XWPFTable table, List<ValidationIssue> issues) {
        return new ClassTeacherReport.ActivityCounters(
                optionalInteger(DocxCells.cell(table, 0, 1), "Таблица 4, «ГТО»", issues),
                optionalInteger(DocxCells.cell(table, 1, 1), "Таблица 4, «Движение Первых»", issues),
                optionalInteger(DocxCells.cell(table, 2, 1), "Таблица 4, «Волонтеры»", issues),
                optionalInteger(DocxCells.cell(table, 3, 1), "Таблица 4, «Совет обучающихся»", issues));
    }

    private List<ClassTeacherReport.StudentAchievement> parseAchievements(XWPFTable table, List<ValidationIssue> issues) {
        List<ClassTeacherReport.StudentAchievement> result = new ArrayList<>();
        for (int row = 1; row < table.getNumberOfRows(); row++) {
            if (DocxCells.emptyRow(table, row)) continue;
            String level = DocxCells.cell(table, row, 0);
            String project = DocxCells.cell(table, row, 1);
            if (project.isBlank()) issues.add(error("ACHIEVEMENT_PROJECT", "Таблица 5, строка " + (row + 1), "Заполнена строка достижения, но отсутствует название проекта.", "Название конкурса/олимпиады", project));
            result.add(new ClassTeacherReport.StudentAchievement(level, project, DocxCells.cell(table, row, 2), DocxCells.cell(table, row, 3), DocxCells.cell(table, row, 4), DocxCells.cell(table, row, 5), DocxCells.cell(table, row, 6)));
        }
        return result;
    }

    private List<ClassTeacherReport.SpecialProjectParticipation> parseProjects(List<XWPFTable> tables, List<ValidationIssue> issues) {
        String[] names = {"Музеи, Парки, усадьбы", "Не прервется связь поколений — 2026", "История и культура храмов столицы и городов России", "Мой героический район", "Читать. Знать. Помнить"};
        List<ClassTeacherReport.SpecialProjectParticipation> result = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            XWPFTable table = tables.get(5 + index);
            for (int row = 1; row < table.getNumberOfRows(); row++) {
                if (DocxCells.emptyRow(table, row)) continue;
                String klass = DocxCells.cell(table, row, 0);
                if (classNameNormalizer.normalize(klass).isEmpty()) issues.add(error("PROJECT_CLASS", "Проект «" + names[index] + "», класс", "Класс не распознан.", "Например: 7А", klass));
                String teacher = DocxCells.cell(table, row, 1);
                if (fullNameValidator.normalizeTeacher(teacher).isEmpty()) issues.add(error("PROJECT_TEACHER", "Проект «" + names[index] + "», классный руководитель", "Неверное ФИО учителя.", "Фамилия Имя Отчество", teacher));
                int resultCol = table.getRow(row).getTableCells().size() - 1;
                validateResult(DocxCells.cell(table, row, resultCol), "Проект «" + names[index] + "»", issues);
                String format = table.getRow(row).getTableCells().size() == 5 ? DocxCells.cell(table, row, 2) : "";
                String students = table.getRow(row).getTableCells().size() == 5 ? DocxCells.cell(table, row, 3) : DocxCells.cell(table, row, 2);
                result.add(new ClassTeacherReport.SpecialProjectParticipation(names[index], klass, teacher, format, students, DocxCells.cell(table, row, resultCol)));
            }
        }
        return result;
    }

    private ClassTeacherReport.TeacherPortfolio parseTeacherPortfolio(XWPFTable table) {
        return new ClassTeacherReport.TeacherPortfolio(DocxCells.cell(table, 1, 0), DocxCells.cell(table, 1, 1), DocxCells.cell(table, 1, 2), DocxCells.cell(table, 1, 3));
    }

    /**
     * Таблица «Педагогические работники» является многострочной: учитель может
     * добавлять любое количество строк. Пустые строки пропускаются, а каждая
     * заполненная строка превращается в отдельную запись отчёта.
     */
    private List<ClassTeacherReport.StaffRecognition> parseRecognitions(XWPFTable table, List<ValidationIssue> issues) {
        List<ClassTeacherReport.StaffRecognition> result = new ArrayList<>();
        for (int row = 1; row < table.getNumberOfRows(); row++) {
            if (DocxCells.emptyRow(table, row)) continue;

            int visibleRow = row + 1;
            String location = "Таблица «Педагогические работники», строка " + visibleRow;
            String rawName = DocxCells.cell(table, row, 0);
            String category = DocxCells.cell(table, row, 1);
            String awards = DocxCells.cell(table, row, 2);

            if (rawName.isBlank()) {
                issues.add(error("STAFF_FIO_REQUIRED", location + ", колонка «ФИО»",
                        "Строка заполнена частично: не указано ФИО работника.",
                        "Фамилия Имя Отчество, например: Иванова Мария Петровна", rawName));
            }
            Optional<String> normalizedName = fullNameValidator.normalizeTeacher(rawName);
            if (!rawName.isBlank() && normalizedName.isEmpty()) {
                issues.add(error("STAFF_FIO", location + ", колонка «ФИО»",
                        "Неверный формат ФИО работника.",
                        "Фамилия Имя Отчество, например: Иванова Мария Петровна", rawName));
            }
            result.add(new ClassTeacherReport.StaffRecognition(normalizedName.orElse(rawName), category, awards));
        }
        return result;
    }

    /**
     * Таблица «Диагностики МЦКО» является многострочной. Все четыре значения
     * обязательны для каждой добавленной пользователем строки.
     */
    private List<ClassTeacherReport.Diagnostic> parseDiagnostics(XWPFTable table, List<ValidationIssue> issues) {
        List<ClassTeacherReport.Diagnostic> result = new ArrayList<>();
        for (int row = 1; row < table.getNumberOfRows(); row++) {
            if (DocxCells.emptyRow(table, row)) continue;

            int visibleRow = row + 1;
            String location = "Таблица «Диагностики МЦКО», строка " + visibleRow;
            String name = DocxCells.cell(table, row, 0);
            String diagnosticResult = DocxCells.cell(table, row, 1);
            String date = DocxCells.cell(table, row, 2);
            String published = DocxCells.cell(table, row, 3);

            requireText(name, location + ", колонка «Название»", "Название действующей диагностики", issues);
            requireText(diagnosticResult, location + ", колонка «Результат»", "Результат, например: высокий или 82%", issues);
            if (date.isBlank()) {
                issues.add(error("DIAGNOSTIC_DATE_REQUIRED", location + ", колонка «Дата»",
                        "Строка заполнена частично: не указана дата диагностики.",
                        "ДД.ММ.ГГГГ, например: 20.05.2026", date));
            } else {
                validateDate(date, location + ", колонка «Дата»", issues);
            }
            if (!published.matches("[+-]")) {
                issues.add(error("DIAGNOSTIC_PUBLISHED", location + ", колонка «Опубликовано +/-»",
                        "Укажите, опубликован ли результат.", "+ или -", published));
            }
            result.add(new ClassTeacherReport.Diagnostic(name, diagnosticResult, date, "+".equals(published)));
        }
        return result;
    }

    private void validateHeaders(List<XWPFTable> tables, List<ValidationIssue> issues) {
        String[][] requiredHeaders = {
                {"класс", "количество учащихся", "отметка"}, {"фио обучающихся", "1 триместр", "итоговая отметка"},
                {"охват до", "не посещают до"}, {"гто", "движения первых"}, {"название рейтингового проекта", "количество победителей"},
                {"класс", "участники", "результат"}, {"класс", "фио обучающегося", "результат"},
                {"класс", "фио обучающегося", "результат"}, {"класс", "номинация", "результат"},
                {"класс", "фио обучающегося", "результат"}, {"чемпионаты", "повышение квалификации"},
                {"фио", "категория"}, {"название", "дата", "опубликовано"}
        };
        for (int i = 0; i < requiredHeaders.length; i++) {
            String tableText = DocxCells.clean(tables.get(i).getText()).toLowerCase(Locale.forLanguageTag("ru"));
            for (String expected : requiredHeaders[i]) {
                if (!tableText.contains(expected)) {
                    issues.add(error("TEMPLATE_HEADER", "Таблица " + (i + 1), "Таблица не соответствует исходному шаблону или переименован заголовок.", "Заголовок содержит: «" + expected + "»", tableText));
                }
            }
        }
    }

    private List<String> validatePeople(String raw, String location, List<ValidationIssue> issues) {
        List<String> people = DocxCells.people(raw);
        for (String person : people) {
            if (!fullNameValidator.isStudentName(person)) {
                issues.add(error("STUDENT_FIO", location, "Неверный формат ФИО обучающегося.", "Список через запятую: Иванов Иван, Петрова Мария", person));
            }
        }
        return people;
    }

    private Integer requiredInteger(String raw, String location, List<ValidationIssue> issues) {
        if (!INTEGER.matcher(raw).matches()) {
            issues.add(error("REQUIRED_INTEGER", location, "Значение не заполнено или не является числом.", "Целое неотрицательное число, например: 28", raw));
            return null;
        }
        return Integer.parseInt(raw);
    }

    private Integer optionalInteger(String raw, String location, List<ValidationIssue> issues) {
        if (raw.isBlank() || raw.equals("-")) return null;
        if (!INTEGER.matcher(raw).matches()) {
            issues.add(error("INTEGER", location, "Ожидается количество.", "Целое неотрицательное число или -", raw));
            return null;
        }
        return Integer.parseInt(raw);
    }

    private int[] requiredCountPercent(String raw, String location, List<ValidationIssue> issues) {
        if (raw.toLowerCase(Locale.forLanguageTag("ru")).contains("пример")) raw = "";
        Matcher matcher = COUNT_PERCENT.matcher(raw);
        if (!matcher.matches()) {
            issues.add(error("COUNT_PERCENT", location, "Неверный формат количества и процента.", "20 человек / 80%", raw));
            return null;
        }
        int count = Integer.parseInt(matcher.group(1));
        int percent = Integer.parseInt(matcher.group(2));
        if (percent > 100) issues.add(error("PERCENT_LIMIT", location, "Процент не может превышать 100.", "От 0% до 100%", String.valueOf(percent) + "%"));
        return new int[]{count, percent};
    }

    private void validateResult(String raw, String location, List<ValidationIssue> issues) {
        if (raw.isBlank() || !RESULT.matcher(raw).matches()) {
            issues.add(error("PROJECT_RESULT", location + ", результат", "Указан неверный результат участия.", "призер или победитель", raw));
        }
    }

    private void requireText(String raw, String location, String expected, List<ValidationIssue> issues) {
        if (raw.isBlank()) {
            issues.add(error("REQUIRED_TEXT", location,
                    "Строка заполнена частично: обязательное поле не заполнено.", expected, raw));
        }
    }

    private void validateDate(String raw, String location, List<ValidationIssue> issues) {
        try {
            LocalDate.parse(raw, DATE);
        } catch (DateTimeParseException exception) {
            issues.add(error("DATE", location, "Неверный формат даты.", "ДД.ММ.ГГГГ, например: 20.05.2026", raw));
        }
    }

    private ValidationIssue error(String code, String location, String message, String expected, String actual) {
        return ValidationIssue.error(code, location, message, expected, actual == null ? "" : actual);
    }

    private record Header(String schoolClass, String teacherName) {}
}
