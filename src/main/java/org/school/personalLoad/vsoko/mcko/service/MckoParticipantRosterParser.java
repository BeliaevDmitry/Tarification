package org.school.personalLoad.vsoko.mcko.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MckoParticipantRosterParser {
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?iu)(?:класс|группа)\\s*:\\s*(1[01]|[1-9])\\s*[-–—]?\\s*([а-яё])");
    private static final Pattern CLASS_BEFORE_LABEL_PATTERN = Pattern.compile(
            "(?iu)(1[01]|[1-9])\\s*[-–—]?\\s*([а-яё])\\s*(?:класс|группа)\\s*:");
    private static final Pattern SAME_LINE_PARTICIPANT = Pattern.compile(
            "^(.+?)\\s+(\\d{4}-\\d{4}[iI]?|\\d{1,4})$");
    private static final Pattern ENGLISH_DATE = Pattern.compile(
            "(?iu)(?<!\\d)(\\d{1,2})[_\\-\\s]*(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)[_\\-\\s]*(\\d{2,4})(?!\\d)");
    private static final Pattern RUSSIAN_DATE = Pattern.compile(
            "(?iu)(?<!\\d)(\\d{1,2})(?:\\s*[-–—]\\s*(\\d{1,2}))?\\s*" +
                    "(января|янв|февраля|фев|марта|мар|апреля|апр|мая|май|июня|июн|июля|июл|" +
                    "августа|авг|сентября|сен|октября|окт|ноября|ноя|декабря|дек)" +
                    "(?:[_\\-\\s]*(\\d{2,4}))?(?!\\d)");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[._-](\\d{1,2})[._-](\\d{2,4})(?!\\d)");
    private static final Map<String, Integer> MONTHS = monthMap();

    public Optional<ParsedRoster> parse(String fileName,
                                        String containerName,
                                        byte[] pdfBytes,
                                        String requestedAcademicYear) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            return parseText(fileName, containerName, text, requestedAcademicYear);
        }
    }

    Optional<ParsedRoster> parseText(String fileName,
                                     String containerName,
                                     String text,
                                     String requestedAcademicYear) {
        String source = clean(text);
        String lowered = source.toLowerCase(Locale.ROOT).replace('ё', 'е');
        boolean roster = lowered.contains("список кодов участников")
                || lowered.contains("список кодов диагностик")
                || lowered.contains("список кодов")
                || (lowered.contains("фио обучающегося") && lowered.contains("код участника"))
                || (lowered.contains("фио участника") && lowered.contains("индивидуальный код"))
                || (lowered.contains("основной список") && lowered.contains("номер учащегося"));
        if (!roster) return Optional.empty();

        List<String> lines = source.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        String school = lines.stream()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains("гбоу")
                        || line.toLowerCase(Locale.ROOT).contains("школа №"))
                .findFirst().orElse("");

        String className = "";
        int classLineIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = CLASS_PATTERN.matcher(lines.get(i));
            boolean found = matcher.find();
            if (!found) {
                matcher = CLASS_BEFORE_LABEL_PATTERN.matcher(lines.get(i));
                found = matcher.find();
            }
            if (found) {
                className = normalizeClass(matcher.group(1) + "-" + matcher.group(2));
                classLineIndex = i;
                break;
            }
        }

        String subject = findSubject(lines, classLineIndex);
        LocalDate workDate = dateFromNames(clean(containerName) + " " + clean(fileName), requestedAcademicYear);
        String academicYear = workDate == null ? cleanYear(requestedAcademicYear) : academicYearFor(workDate);
        List<Participant> participants = participants(lines);

        List<String> missing = new ArrayList<>();
        if (className.isBlank()) missing.add("класс");
        if (subject.isBlank()) missing.add("предмет");
        if (academicYear.isBlank()) missing.add("учебный год");
        if (participants.isEmpty()) missing.add("ФИО и коды участников");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("В PDF-списке не удалось определить: " + String.join(", ", missing));
        }
        return Optional.of(new ParsedRoster(school, className, subject, workDate, academicYear, participants));
    }

    private List<Participant> participants(List<String> lines) {
        List<Participant> combined = new ArrayList<>();
        boolean participantSection = false;
        for (String raw : lines) {
            String line = raw.replaceAll("(?iu)\\s+бланк\\s*$", "").trim();
            String key = line.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (key.contains("резервный список")) break;
            if (key.contains("фио обучающегося") || key.contains("фио участника") || key.contains("фио учащегося")) {
                participantSection = true;
                continue;
            }
            if (!participantSection) continue;
            Matcher matcher = SAME_LINE_PARTICIPANT.matcher(line);
            if (!matcher.find()) continue;
            String name = normalizeName(matcher.group(1));
            if (validName(name)) combined.add(new Participant(combined.size() + 1, name, matcher.group(2)));
        }
        if (!combined.isEmpty()) return combined;

        List<String> names = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        boolean namesSection = false;
        boolean codesSection = false;
        for (String raw : lines) {
            String line = raw.trim();
            String key = line.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (key.contains("резервный список")) break;
            if (key.contains("фио обучающегося") || key.contains("фио участника") || key.contains("фио учащегося")) {
                namesSection = true;
                codesSection = false;
                continue;
            }
            if (key.equals("код") || key.contains("код участника") || key.contains("индивидуальный код")) {
                namesSection = false;
                codesSection = true;
                continue;
            }
            if (namesSection) {
                String name = normalizeName(line);
                if (validName(name)) names.add(name);
            } else if (codesSection && line.matches("\\d{4}-\\d{4}[iI]?|\\d{1,4}")) {
                codes.add(line);
            }
        }
        int count = Math.min(names.size(), codes.size());
        List<Participant> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new Participant(i + 1, names.get(i), codes.get(i)));
        if (!result.isEmpty()) return result;

        boolean mainSection = false;
        for (String raw : lines) {
            String line = raw.trim();
            String key = line.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (key.contains("резервный список")) break;
            if (key.contains("основной список")) { mainSection = true; continue; }
            if (!mainSection || key.contains("фио") || key.contains("номер учащегося")) continue;
            Matcher matcher = Pattern.compile("^(.+?)\\s+(\\d{1,3})$").matcher(line);
            if (!matcher.find()) continue;
            String name = normalizeName(matcher.group(1));
            if (validName(name)) result.add(new Participant(Integer.parseInt(matcher.group(2)), name, ""));
        }
        return result;
    }

    private String findSubject(List<String> lines, int classLineIndex) {
        if (classLineIndex < 0) return "";
        for (int i = classLineIndex + 1; i < Math.min(lines.size(), classLineIndex + 7); i++) {
            String value = lines.get(i).replaceAll("(?iu)\\s*округ\\s*:.*$", "").trim();
            String key = value.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (value.isBlank() || key.contains("фио") || key.contains("код") || key.contains("номер")
                    || key.contains("основной список") || key.contains("резервный список")) continue;
            return value.replaceAll("\\s+", " ");
        }
        return "";
    }

    private LocalDate dateFromNames(String value, String academicYearHint) {
        Matcher numeric = NUMERIC_DATE.matcher(value);
        if (numeric.find()) {
            Integer year = parseYear(numeric.group(3));
            try {
                return LocalDate.of(year, Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1)));
            } catch (RuntimeException ignored) {
                // Continue with the textual date formats used by other MCKO archives.
            }
        }
        Matcher english = ENGLISH_DATE.matcher(value);
        if (english.find()) return buildDate(english.group(1), english.group(2), english.group(3), academicYearHint);
        Matcher russian = RUSSIAN_DATE.matcher(value);
        if (russian.find()) return buildDate(russian.group(2) == null ? russian.group(1) : russian.group(2),
                russian.group(3), russian.group(4), academicYearHint);
        return null;
    }

    private LocalDate buildDate(String dayText, String monthText, String yearText, String academicYearHint) {
        Integer month = MONTHS.get(monthText.toLowerCase(Locale.ROOT).replace('ё', 'е'));
        if (month == null) return null;
        Integer year = parseYear(yearText);
        if (year == null) year = yearForMonthFromAcademicYear(month, academicYearHint);
        if (year == null) return null;
        try {
            return LocalDate.of(year, month, Integer.parseInt(dayText));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer parseYear(String value) {
        if (value == null || value.isBlank()) return null;
        int year = Integer.parseInt(value);
        return year < 100 ? 2000 + year : year;
    }

    private Integer yearForMonthFromAcademicYear(int month, String academicYear) {
        Matcher matcher = Pattern.compile("(20\\d{2})\\D+(20\\d{2})").matcher(clean(academicYear));
        if (!matcher.find()) return null;
        return month >= 8 ? Integer.valueOf(matcher.group(1)) : Integer.valueOf(matcher.group(2));
    }

    private static Map<String, Integer> monthMap() {
        Map<String, Integer> result = new HashMap<>();
        String[][] names = {
                {"january", "jan", "января", "янв"}, {"february", "feb", "февраля", "фев"},
                {"march", "mar", "марта", "мар"}, {"april", "apr", "апреля", "апр"},
                {"may", "мая", "май"}, {"june", "jun", "июня", "июн"}, {"july", "jul", "июля", "июл"},
                {"august", "aug", "августа", "авг"}, {"september", "sep", "sept", "сентября", "сен"},
                {"october", "oct", "октября", "окт"}, {"november", "nov", "ноября", "ноя"},
                {"december", "dec", "декабря", "дек"}
        };
        for (int month = 1; month <= names.length; month++) {
            for (String name : names[month - 1]) result.put(name, month);
        }
        return result;
    }

    private String normalizeClass(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT).replace('Ё', 'Е')
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", "");
        return normalized.replaceAll("^(1[01]|[1-9])-?([А-ЯA-Z])$", "$1-$2");
    }

    private String normalizeName(String value) {
        return clean(value).replace('ѐ', 'ё').replace('Ѐ', 'Ё').replaceAll("\\s+", " ");
    }

    private boolean validName(String value) {
        String key = clean(value).toLowerCase(Locale.ROOT);
        return value.split("\\s+").length >= 2 && !key.contains("фио") && !key.contains("код")
                && value.matches(".*[А-ЯЁ][а-яё]+.*");
    }

    private String cleanYear(String value) {
        Matcher matcher = Pattern.compile("(20\\d{2})\\s*[/\\-]\\s*(20\\d{2})").matcher(clean(value));
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : clean(value).replace('-', '/');
    }

    private String academicYearFor(LocalDate date) {
        int start = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
        return start + "/" + (start + 1);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record Participant(int studentNumber, String fio, String code) {}

    public record ParsedRoster(String schoolName, String className, String subjectName,
                               LocalDate workDate, String academicYear, List<Participant> participants) {}
}
