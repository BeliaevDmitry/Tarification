package org.school.personalLoad.service.impl;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassNameNormalizer {

    private static final Pattern CLASS_WITH_LETTER = Pattern.compile("^(\\d{1,2})\\s*[- ]?\\s*([А-ЯA-Z])$");

    private ClassNameNormalizer() {
    }

    public static Integer extractParallel(String rawClassName) {
        String normalized = normalize(rawClassName);
        Matcher matcher = CLASS_WITH_LETTER.matcher(normalized);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        Matcher digits = Pattern.compile("^(\d{1,2})").matcher(normalized);
        return digits.find() ? Integer.parseInt(digits.group(1)) : null;
    }

    public static String normalize(String rawClassName) {
        String value = rawClassName == null ? "" : rawClassName.trim();
        if (value.isBlank()) {
            return "";
        }

        value = value
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.forLanguageTag("ru"));

        Matcher matcher = CLASS_WITH_LETTER.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1) + "-" + matcher.group(2);
        }

        return value;
    }
}
