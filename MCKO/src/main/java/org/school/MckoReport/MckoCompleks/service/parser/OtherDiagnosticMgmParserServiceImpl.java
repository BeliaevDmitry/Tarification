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
import java.util.Locale;

@Slf4j
@Service
public class OtherDiagnosticMgmParserServiceImpl implements OtherDiagnosticMgmParserService {

    @Override
    public List<OtherDiagnosticData> extractDiagnosticData(Path filePath) {
        List<OtherDiagnosticData> results = new ArrayList<>();

        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            String rawDate = extractDate(text);
            String normalizedDate = DateNormalizerUtil.normalizeDate(rawDate);
            String schoolYear = DateNormalizerUtil.calculateSchoolYear(normalizedDate);

            String school = extractSchool(text);
            String className = extractClass(text);
            String subject = extractSubjectForMgm(text);

            OtherDiagnosticData data = OtherDiagnosticData.builder()
                    .school(school)
                    .className(className)
                    .subject(subject)
                    .date(normalizedDate)
                    .schoolYear(schoolYear)
                    .fileName(filePath.getFileName().toString())
                    .build();

            setAveragePercents(data, text);
            validateRequiredFields(data, filePath, rawDate);
            results.add(data);

        } catch (IOException e) {
            throw new ProcessingException("Не удалось обработать PDF МГМ: " + filePath, e);
        }

        return results;
    }

    private String extractDate(String text) {
        Pattern pattern = Pattern.compile("Дата:\\s*(.+?)(?=\\s+Округ:|\\s+Школа:|\\r|\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "дата не определена";
    }

    private String extractSubjectForMgm(String text) {
        // Старый вариант: "Дата: ... года Математическая грамотность Округ:"
        Pattern pattern = Pattern.compile(
                "Дата:\\s*.+?\\b(?:года|год|г\\.?|г)\\b\\s+(.+?)\\s+Округ:",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return SubjectNormalizerUtil.normalize(cleanSubject(matcher.group(1)));
        }

        // Новый fallback: между "Дата:" и "Округ:" ищем известную грамотность
        Pattern blockPattern = Pattern.compile(
                "Дата:\\s*(.+?)\\s+Округ:",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher blockMatcher = blockPattern.matcher(text);
        if (blockMatcher.find()) {
            String block = blockMatcher.group(1)
                    .replaceAll("\\s+", " ")
                    .trim();

            if (block.toLowerCase(Locale.ROOT).contains("математическая грамотность")) {
                return SubjectNormalizerUtil.normalize("Математическая грамотность");
            }

            if (block.toLowerCase(Locale.ROOT).contains("читательская грамотность")) {
                return SubjectNormalizerUtil.normalize("Читательская грамотность");
            }

            if (block.toLowerCase(Locale.ROOT).contains("естественно-научная грамотность")
                    || block.toLowerCase(Locale.ROOT).contains("естественнонаучная грамотность")) {
                return SubjectNormalizerUtil.normalize("Естественно-научная грамотность");
            }
        }

        return "не указан";
    }

    private String cleanSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String cleaned = raw.replaceAll("\\s+", " ").trim();

        // Убираем дату в начале: "14-15 марта 2023г. Математическая грамотность"
        cleaned = cleaned.replaceFirst(
                "^\\d{1,2}(?:[-–—.]\\d{1,2})?\\s*[а-яА-ЯёЁ]*\\s*\\d{4}\\s*(?:года|год|г\\.?|г)?\\s*\\.?",
                ""
        ).trim();

        // Убираем цифровую дату в начале: "18.05.2023 Математическая грамотность"
        cleaned = cleaned.replaceFirst(
                "^\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\s*",
                ""
        ).trim();

        return cleaned;
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
            String value = matcher.group(1).trim().toUpperCase();
            if (value.matches("^\\d+[А-ЯЁ]$")) {
                return value.replaceAll("^(\\d+)([А-ЯЁ])$", "$1-$2");
            }
            return value;
        }
        return "не указан";
    }

    private void setAveragePercents(OtherDiagnosticData data, String text) {
        String normalizedText = text == null ? "" : text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ");

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
            throw new ProcessingException(
                    "Файл МГМ не прошел валидацию, отсутствуют обязательные поля: " +
                            String.join(", ", missingFields) +
                            " (" + filePath.getFileName() + "); rawDate='" + rawDate + "'"
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
