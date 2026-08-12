package org.school.personalLoad.vsoko.mcko.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the class result PDFs produced by the legacy MCKO application.
 * The source system has several layouts (regular works, FG, MGM and MGCH),
 * but all of them share the same first-page result table.
 */
@Service
public class MckoLegacyPdfParser {
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?iu)класс\s*:\s*(1[01]|[1-9])\s*[-–—]?\s*([а-яё])");
    private static final Pattern CLASS_BEFORE_LABEL_PATTERN = Pattern.compile(
            "(?iu)(1[01]|[1-9])\s*[-–—]?\s*([а-яё])\s*класс\s*:");
    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{4}-\\d{4}[iI]?");
    private static final Pattern RUSSIAN_DATE = Pattern.compile(
            "(?iu)(?<!\\d)(\\d{1,2})(?:\\s*[-–—]\\s*(\\d{1,2}))?\\s*" +
                    "(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)" +
                    "\\s*(20\\d{2})(?!\\d)");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](20\\d{2})(?!\\d)");
    private static final Pattern ENGLISH_DATE = Pattern.compile(
            "(?iu)(?<!\\d)(\\d{1,2})[_\\-\\s]*(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)[_\\-\\s]*(\\d{2,4})(?!\\d)");
    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "(?iu)число\\s+учащихся\\s*:\\s*(\\d+)\\s+среднее(?:\\s+по\\s+классу)?\\s*:\\s*" +
                    "(\\d{1,4}(?:[.,]\\d+)?)\\s+(\\d{1,3}(?:[.,]\\d+)?)\\s*%?");
    private static final Pattern BENCHMARK_PATTERN = Pattern.compile(
            "(?iu)средн(?:ий|яя|ее)?\\s*(?:%|процент)?\\s*выполнения.{0,80}?" +
                    "(\\d{1,3}(?:[.,]\\d+)?)\\s*%\\s*(\\d{1,3}(?:[.,]\\d+)?)\\s*%");
    private static final Pattern MASTERY_SUFFIX = Pattern.compile(
            "(?iu)\\s+(ниже\\s+базового|повышенный|базовый|высокий|средний|низкий)\\s*$");
    private static final Map<String, Integer> MONTHS = monthMap();

    public Optional<ParsedPdf> parse(String fileName, byte[] pdfBytes, String requestedAcademicYear) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            return parseText(fileName, new PDFTextStripper().getText(document), requestedAcademicYear);
        }
    }

    Optional<ParsedPdf> parseText(String fileName, String sourceText, String requestedAcademicYear) {
        String text = clean(sourceText);
        String normalized = normalizeSpace(text);
        String lowered = normalized.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (!lowered.contains("результат") || !lowered.contains("дата:") || !lowered.contains("фамилия, имя")) {
            return Optional.empty();
        }

        String className = extractClass(normalized);
        String subject = extractSubject(normalized);
        LocalDate date = extractDate(normalized, fileName);
        String academicYear = date == null ? cleanYear(requestedAcademicYear) : academicYearFor(date);
        String school = extractSchool(normalized);
        boolean functional = isFunctional(fileName, lowered, subject);
        WorkSummary summary = extractSummary(normalized);
        List<StudentRow> rows = extractRows(text, functional);

        if (className.isBlank() || subject.isBlank() || academicYear.isBlank()) {
            return Optional.empty();
        }
        PdfKind kind = functional ? PdfKind.FUNCTIONAL_LITERACY
                : (rows.isEmpty() ? PdfKind.CLASS_SUMMARY : PdfKind.STANDARD);
        return Optional.of(new ParsedPdf(kind, school, className, subject, date, academicYear, summary, rows));
    }

    private List<StudentRow> extractRows(String text, boolean functional) {
        List<StudentRow> result = new ArrayList<>();
        boolean inTable = false;
        boolean hasMark = false;
        boolean hasSectionColumns = false;
        for (String rawLine : text.split("\\R")) {
            String line = normalizeSpace(rawLine);
            String key = line.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (key.contains("фамилия, имя")) {
                inTable = true;
            }
            if (!inTable) continue;
            hasMark = hasMark || key.contains("отметка") || key.contains("оценка");
            hasSectionColumns = hasSectionColumns || key.contains("по блокам")
                    || key.matches(".*\\bi\\s+ii\\s+iii\\b.*")
                    || (key.contains("раздел 1") && key.contains("раздел 2") && key.contains("раздел 3"));
            if (key.contains("фамилия, имя") || key.equals("уч.") || key.equals("уч")) continue;
            if (key.startsWith("число учащихся:") || key.startsWith("результаты класса")) break;
            StudentRow parsed = functional
                    ? parseFunctionalRow(line, hasSectionColumns)
                    : parseStandardRow(line, hasMark);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private StudentRow parseFunctionalRow(String line, boolean hasSectionColumns) {
        if (!line.matches("^\\d{1,3}\\s+.*") || !CODE_PATTERN.matcher(line).find()) return null;
        Matcher masteryMatcher = MASTERY_SUFFIX.matcher(line);
        String mastery = "";
        if (masteryMatcher.find()) {
            mastery = display(masteryMatcher.group(1));
            line = line.substring(0, masteryMatcher.start()).trim();
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length < 6) return null;
        Integer number = integerToken(tokens[0]);
        int codeIndex = -1;
        for (int i = 1; i < Math.min(tokens.length, 5); i++) {
            if (CODE_PATTERN.matcher(tokens[i]).matches()) { codeIndex = i; break; }
        }
        if (number == null || codeIndex < 0 || codeIndex + 1 >= tokens.length) return null;
        String variant = tokens[codeIndex + 1];
        if (!variant.matches("\\d{3,6}")) return null;

        int firstPercent = -1;
        List<Double> percents = new ArrayList<>();
        for (int i = codeIndex + 2; i < tokens.length; i++) {
            if (tokens[i].matches("\\d{1,3}(?:[.,]\\d+)?%")) {
                if (firstPercent < 0) firstPercent = i;
                percents.add(decimal(tokens[i]));
            }
        }
        if (firstPercent <= codeIndex + 2 || percents.isEmpty()) return null;
        int scoreIndex = firstPercent - 1;
        Double score = decimal(tokens[scoreIndex]);
        if (score == null) return null;
        Map<String, Double> tasks = taskScores(tokens, codeIndex + 2, scoreIndex);
        Double section1 = null, section2 = null, section3 = null;
        if (hasSectionColumns && percents.size() >= 3) {
            section1 = percents.get(percents.size() - 3);
            section2 = percents.get(percents.size() - 2);
            section3 = percents.get(percents.size() - 1);
        }
        return new StudentRow(number, tokens[codeIndex].toUpperCase(Locale.ROOT), variant, score,
                percents.get(0), null, tasks, mastery, section1, section2, section3);
    }

    private StudentRow parseStandardRow(String line, boolean hasMark) {
        if (!line.matches("^\\d{1,3}\\s+.*")) return null;
        String[] tokens = line.split("\\s+");
        if (tokens.length < 5) return null;
        Integer number = integerToken(tokens[0]);
        if (number == null) return null;
        int cursor = 1;
        String code = "";
        String variant;
        if (cursor < tokens.length && CODE_PATTERN.matcher(tokens[cursor]).matches()) {
            code = tokens[cursor++];
            if (cursor >= tokens.length || !tokens[cursor].matches("\\d{3,6}")) return null;
            variant = tokens[cursor++];
        } else {
            if (cursor >= tokens.length || !tokens[cursor].matches("\\d{3,6}")) return null;
            variant = tokens[cursor++];
            if (cursor < tokens.length && CODE_PATTERN.matcher(tokens[cursor]).matches()) code = tokens[cursor++];
        }
        int tail = hasMark ? 3 : 2;
        if (tokens.length - cursor <= tail) return null;
        int scoreIndex = tokens.length - tail;
        Double score = decimal(tokens[scoreIndex]);
        Double percent = decimal(tokens[scoreIndex + 1]);
        Integer mark = hasMark ? integerToken(tokens[scoreIndex + 2]) : null;
        if (score == null || percent == null || percent < 0 || percent > 100) return null;
        return new StudentRow(number, code.toUpperCase(Locale.ROOT), variant, score, percent, mark,
                taskScores(tokens, cursor, scoreIndex), "", null, null, null);
    }

    private Map<String, Double> taskScores(String[] tokens, int fromInclusive, int toExclusive) {
        Map<String, Double> result = new LinkedHashMap<>();
        int taskNo = 1;
        for (int i = fromInclusive; i < toExclusive; i++) {
            String token = tokens[i].trim();
            Double value;
            if (token.equalsIgnoreCase("N")) value = 0D;
            else value = decimal(token);
            if (value != null) result.put(String.valueOf(taskNo), value);
            taskNo++;
        }
        return result;
    }

    private WorkSummary extractSummary(String text) {
        Integer count = null;
        Double score = null;
        Double percent = null;
        Double cityPercent = null;
        Matcher summary = SUMMARY_PATTERN.matcher(text);
        if (summary.find()) {
            count = integerToken(summary.group(1));
            score = decimal(summary.group(2));
            percent = decimal(summary.group(3));
        }
        Matcher benchmark = BENCHMARK_PATTERN.matcher(text);
        if (benchmark.find()) {
            if (percent == null) percent = decimal(benchmark.group(1));
            cityPercent = decimal(benchmark.group(2));
        }
        return new WorkSummary(count, score, percent, cityPercent);
    }

    private String extractClass(String text) {
        Matcher matcher = CLASS_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = CLASS_BEFORE_LABEL_PATTERN.matcher(text);
            if (!matcher.find()) return "";
        }
        return matcher.group(1) + "-" + matcher.group(2).toUpperCase(Locale.ROOT).replace('Ё', 'Е');
    }

    private String extractSubject(String text) {
        Matcher explicit = Pattern.compile("(?iu)предмет\\s*:\\s*(.+?)(?=\\s+округ\\s*:)").matcher(text);
        if (explicit.find()) return normalizeSubject(explicit.group(1));
        String[] known = {"Функциональная грамотность", "Математическая грамотность",
                "Читательская грамотность", "Естественно-научная грамотность", "Естественнонаучная грамотность"};
        for (String value : known) {
            if (text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) return normalizeSubject(value);
        }
        Matcher block = Pattern.compile("(?iu)дата\\s*:\\s*(.+?)(?=\\s+округ\\s*:)").matcher(text);
        if (!block.find()) return "";
        String value = block.group(1)
                .replaceFirst("(?iu)^\\d{1,2}(?:\\s*[-–—]\\s*\\d{1,2})?\\s+[а-яё]+\\s+20\\d{2}\\s*(?:года|год|г\\.?)?\\s*", "")
                .replaceFirst("^\\d{1,2}[./-]\\d{1,2}[./-]20\\d{2}\\s*", "");
        return normalizeSubject(value);
    }

    private String normalizeSubject(String value) {
        String normalized = display(value).replaceAll("(?iu)\\s+(?:округ|школа|класс)\\s*:.*$", "")
                .replaceAll("[.;:,\\-\\s]+$", "").trim();
        if (normalized.equalsIgnoreCase("Читательская")) return "Читательская грамотность";
        if (normalized.equalsIgnoreCase("Информационная")) return "Информационная безопасность";
        if (normalized.equalsIgnoreCase("Вероятность и")) return "Вероятность и статистика";
        if (normalized.equalsIgnoreCase("Естественнонаучная грамотность")) return "Естественно-научная грамотность";
        return normalized;
    }

    private String extractSchool(String text) {
        Matcher matcher = Pattern.compile("(?iu)школа\\s*:\\s*(.+?)(?=\\s+класс\\s*:)").matcher(text);
        return matcher.find() ? display(matcher.group(1)) : "";
    }

    private LocalDate extractDate(String text, String fileName) {
        Matcher russian = RUSSIAN_DATE.matcher(text);
        if (russian.find()) {
            int day = Integer.parseInt(russian.group(2) == null ? russian.group(1) : russian.group(2));
            return safeDate(Integer.parseInt(russian.group(4)), MONTHS.get(russian.group(3).toLowerCase(Locale.ROOT)), day);
        }
        Matcher numeric = NUMERIC_DATE.matcher(text);
        if (numeric.find()) return safeDate(Integer.parseInt(numeric.group(3)), Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1)));
        Matcher english = ENGLISH_DATE.matcher(clean(fileName));
        if (english.find()) {
            int year = Integer.parseInt(english.group(3));
            if (year < 100) year += 2000;
            return safeDate(year, MONTHS.get(english.group(2).toLowerCase(Locale.ROOT)), Integer.parseInt(english.group(1)));
        }
        return null;
    }

    private boolean isFunctional(String fileName, String loweredText, String subject) {
        String name = clean(fileName).toLowerCase(Locale.ROOT).replace('ё', 'е');
        String subjectKey = clean(subject).toLowerCase(Locale.ROOT).replace('ё', 'е');
        return loweredText.contains("функциональн") || subjectKey.contains("грамотност")
                || name.contains("фг") || name.contains("фкг") || name.contains("мгм") || name.contains("мгч");
    }

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        if (year == null || month == null || day == null) return null;
        try { return LocalDate.of(year, month, day); } catch (RuntimeException ignored) { return null; }
    }

    private Double decimal(String value) {
        String normalized = clean(value).replace("%", "").replace(',', '.').replaceAll("[+\\-]$", "");
        if (normalized.equalsIgnoreCase("N") || normalized.isBlank()) return null;
        try { return Double.valueOf(normalized); } catch (NumberFormatException ignored) { return null; }
    }

    private Integer integerToken(String value) {
        Double parsed = decimal(value);
        return parsed == null ? null : parsed.intValue();
    }

    private String academicYearFor(LocalDate date) {
        int start = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
        return start + "/" + (start + 1);
    }

    private String cleanYear(String value) {
        Matcher matcher = Pattern.compile("(20\\d{2})\\s*[/\\-]\\s*(20\\d{2})").matcher(clean(value));
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : clean(value).replace('-', '/');
    }

    private static Map<String, Integer> monthMap() {
        Map<String, Integer> result = new HashMap<>();
        String[][] names = {
                {"january", "jan", "января"}, {"february", "feb", "февраля"},
                {"march", "mar", "марта"}, {"april", "apr", "апреля"}, {"may", "мая"},
                {"june", "jun", "июня"}, {"july", "jul", "июля"}, {"august", "aug", "августа"},
                {"september", "sep", "sept", "сентября"}, {"october", "oct", "октября"},
                {"november", "nov", "ноября"}, {"december", "dec", "декабря"}
        };
        for (int month = 1; month <= names.length; month++) {
            for (String name : names[month - 1]) result.put(name, month);
        }
        return result;
    }

    private String normalizeSpace(String value) { return clean(value).replace('\u00a0', ' ').replaceAll("\\s+", " "); }
    private String display(String value) { return normalizeSpace(value); }
    private String clean(String value) { return value == null ? "" : value.trim(); }

    public enum PdfKind { STANDARD, FUNCTIONAL_LITERACY, CLASS_SUMMARY }

    public record WorkSummary(Integer participantCount, Double averageScore, Double averagePercent, Double cityPercent) {}

    public record StudentRow(Integer studentNumber, String code, String variant, Double score, Double percent,
                             Integer mark, Map<String, Double> taskScores, String masteryLevel,
                             Double section1Percent, Double section2Percent, Double section3Percent) {}

    public record ParsedPdf(PdfKind kind, String schoolName, String className, String subjectName,
                            LocalDate workDate, String academicYear, WorkSummary summary,
                            List<StudentRow> students) {}
}
