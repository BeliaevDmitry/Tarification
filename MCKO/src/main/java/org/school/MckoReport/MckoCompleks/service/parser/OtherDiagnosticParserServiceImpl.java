package org.school.MckoReport.MckoCompleks.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.OtherDiagnosticData;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.school.MckoReport.MckoCompleks.util.SubjectNormalizerUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OtherDiagnosticParserServiceImpl implements OtherDiagnosticParserService {

    public List<OtherDiagnosticData> extractDiagnosticData(Path filePath) {
        List<OtherDiagnosticData> results = new ArrayList<>();

        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            String date = extractDate(text);
            String normalizedDate = DateNormalizerUtil.normalizeDate(date);
            String schoolYear = DateNormalizerUtil.calculateSchoolYear(normalizedDate);

            String school = extractSchool(text);
            String className = extractClass(text);
            String subject = extractSubject(text);

            OtherDiagnosticData data = OtherDiagnosticData.builder()
                    .school(school)
                    .className(className)
                    .subject(subject)
                    .date(normalizedDate)
                    .schoolYear(schoolYear)
                    .fileName(filePath.getFileName().toString())
                    .build();

            setAveragePercents(data, text);
            validateRequiredFields(data, filePath, date);

            results.add(data);

            log.info("Извлечены данные: школа={}, класс={}, предмет={}, дата={}, %={}, город%={}",
                    school, className, subject, normalizedDate, data.getAvgPercent(), data.getCityPercent());

        } catch (IOException e) {
            log.error("Ошибка при обработке файла {}: {}", filePath, e.getMessage());
            throw new ProcessingException("Не удалось обработать PDF: " + filePath, e);
        }

        return results;
    }

    private String extractDate(String text) {
        Pattern pattern = Pattern.compile("Дата:\\s*(.+?)(?=\\s+Округ:|\\s+Предмет:|\\r|\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "дата не определена";
    }

    private String extractSchool(String text) {
        Pattern pattern = Pattern.compile("ГБОУ\\s+Школа\\s+№\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "ГБОУ Школа № " + matcher.group(1);
        }
        return "не указана";
    }

    private String extractClass(String text) {
        Pattern pattern = Pattern.compile("Класс:\\s*(\\d+[А-Яа-яЁё]?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return normalizeClassName(matcher.group(1));
        }
        return "не указан";
    }

    private String extractSubject(String text) {
        // 1. Явный "Предмет:"
        Pattern pattern = Pattern.compile("Предмет:\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return normalizeSubject(cleanSubject(matcher.group(1)));
        }

        // 2. Fallback: после "года" до ближайшего маркера или конца строки
        //    Ищем "года", затем захватываем всё до первого из: "Округ:", "Школа:", "Класс:", или конца строки.
        Pattern fallbackPattern = Pattern.compile(
                "\\bгода\\b\\s+(.+?)(?=\\s+(?:Округ|Школа|Класс):|\\r?\\n|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher fallbackMatcher = fallbackPattern.matcher(text);
        if (fallbackMatcher.find()) {
            String raw = fallbackMatcher.group(1).trim();
            return normalizeSubject(cleanSubject(raw));
        }

        return "не указан";
    }

    /**
     * Обрезает строку по первому вхождению маркеров "Округ", "Школа", "Класс"
     * (с учётом регистра, возможного двоеточия и пробелов после них)
     */
    private String cleanSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase();
        String[] markers = {"округ", "школа", "класс"};
        int cutIndex = raw.length();

        for (String marker : markers) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && idx < cutIndex) {
                cutIndex = idx;
            }
        }

        if (cutIndex < raw.length()) {
            raw = raw.substring(0, cutIndex).trim();
        }
        // дополнительно удаляем возможные хвостовые символы (двоеточие, пробелы, точки)
        raw = raw.replaceAll("[\\.;:,\\-\\s]+$", "").trim();
        return raw;
    }

    private void setAveragePercents(OtherDiagnosticData data, String text) {
        String normalizedText = text == null ? "" : text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ");

        // Основной вариант:
        // "Средний % выполнения диагн. работы: 27% 38%"
        // "Средний процент выполнения диагностической работы 27 % 38 %"
        Pattern patternDouble = Pattern.compile(
                "Средн(?:ий|яя|ее)?\\s*(?:%|процент)?\\s*выполнения\\s*" +
                        "(?:диагн\\.?\\s*работы|диагностической\\s*работы|работы|теста)?\\s*[:\\-]?\\s*" +
                        "(\\d{1,3})\\s*%\\s*(\\d{1,3})\\s*%",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher matcherDouble = patternDouble.matcher(normalizedText);
        if (matcherDouble.find()) {
            data.setAvgPercent(matcherDouble.group(1) + "%");
            data.setCityPercent(matcherDouble.group(2) + "%");
            return;
        }

        // Один процент:
        // "Средний % выполнения: 27%"
        Pattern patternSingle = Pattern.compile(
                "Средн(?:ий|яя|ее)?\\s*(?:%|процент)?\\s*выполнения\\s*" +
                        "(?:диагн\\.?\\s*работы|диагностической\\s*работы|работы|теста)?\\s*[:\\-]?\\s*" +
                        "(\\d{1,3})\\s*%",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher matcherSingle = patternSingle.matcher(normalizedText);
        if (matcherSingle.find()) {
            data.setAvgPercent(matcherSingle.group(1) + "%");
            data.setCityPercent(null);
            return;
        }

        // Fallback: если в строке рядом со словом "Средний" есть два процента
        Pattern fallbackDouble = Pattern.compile(
                "Средн.{0,120}?(\\d{1,3})\\s*%\\s*(\\d{1,3})\\s*%",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher fallbackMatcherDouble = fallbackDouble.matcher(normalizedText);
        if (fallbackMatcherDouble.find()) {
            data.setAvgPercent(fallbackMatcherDouble.group(1) + "%");
            data.setCityPercent(fallbackMatcherDouble.group(2) + "%");
            return;
        }

        // Fallback: если рядом со словом "Средний" есть один процент
        Pattern fallbackSingle = Pattern.compile(
                "Средн.{0,120}?(\\d{1,3})\\s*%",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher fallbackMatcherSingle = fallbackSingle.matcher(normalizedText);
        if (fallbackMatcherSingle.find()) {
            data.setAvgPercent(fallbackMatcherSingle.group(1) + "%");
            data.setCityPercent(null);
            return;
        }

        data.setAvgPercent("не определен");
        data.setCityPercent(null);
    }

    private void validateRequiredFields(OtherDiagnosticData data, Path filePath, String rawDate) {
        List<String> missingFields = new ArrayList<>();

        if (!DateNormalizerUtil.isValidDate(data.getDate())) {
            missingFields.add("дата");
        }
        if (!hasText(data.getClassName()) || "не указан".equalsIgnoreCase(data.getClassName())) {
            missingFields.add("класс");
        }
        if (!hasText(data.getSubject()) || "не указан".equalsIgnoreCase(data.getSubject())) {
            missingFields.add("предмет");
        }
        if (!hasText(data.getAvgPercent()) || "не определен".equalsIgnoreCase(data.getAvgPercent())) {
            missingFields.add("средний % выполнения");
        }

        if (!missingFields.isEmpty()) {
            log.warn(
                    "Неудачный парсинг файла {}. Причина: {}. Извлечено: date='{}', class='{}', subject='{}', avg='{}', city='{}'",
                    filePath.getFileName(),
                    String.join(", ", missingFields),
                    data.getDate(),
                    data.getClassName(),
                    data.getSubject(),
                    data.getAvgPercent(),
                    data.getCityPercent()
            );
            throw new ProcessingException(
                    "Файл других диагностик не прошел валидацию: " +
                            filePath.getFileName() +
                            "; отсутствуют обязательные поля: " + String.join(", ", missingFields) +
                            "; rawDate='" + rawDate + "'" +
                            "; class='" + data.getClassName() + "'" +
                            "; subject='" + data.getSubject() + "'" +
                            "; avg='" + data.getAvgPercent() + "'" +
                            "; city='" + data.getCityPercent() + "'"
            );
        }
    }

    private String normalizeSubject(String rawSubject) {
        if (!hasText(rawSubject)) {
            return "не указан";
        }
        return SubjectNormalizerUtil.normalize(rawSubject);
    }

    private String normalizeClassName(String className) {
        if (!hasText(className)) {
            return "не указан";
        }

        String normalized = className.trim().toUpperCase();
        if (normalized.matches("^\\d+[А-ЯЁ]$")) {
            return normalized.replaceAll("^(\\d+)([А-ЯЁ])$", "$1-$2");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
