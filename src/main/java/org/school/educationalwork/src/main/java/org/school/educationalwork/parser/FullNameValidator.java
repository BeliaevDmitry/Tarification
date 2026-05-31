package org.school.educationalwork.src.main.java.org.school.educationalwork.parser;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FullNameValidator {
    private static final Pattern WORD = Pattern.compile("[А-ЯЁ][а-яё]+(?:-[А-ЯЁ][а-яё]+)?");

    public Optional<String> normalizeTeacher(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.trim().replaceAll("[_—]+", " ").replaceAll("\\s+", " ");
        if (value.isBlank() || value.equalsIgnoreCase("ФИО")) return Optional.empty();
        String[] words = value.split(" ");
        if (words.length < 2 || words.length > 4) return Optional.empty();
        String normalized = Arrays.stream(words).map(this::capitalize).collect(Collectors.joining(" "));
        return Arrays.stream(normalized.split(" ")).allMatch(w -> WORD.matcher(w).matches())
                ? Optional.of(normalized) : Optional.empty();
    }

    public boolean isStudentName(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String[] words = raw.trim().replaceAll("\\s+", " ").split(" ");
        if (words.length < 2 || words.length > 3) return false;
        return Arrays.stream(words).map(this::capitalize).allMatch(w -> WORD.matcher(w).matches());
    }

    private String capitalize(String word) {
        if (word.isBlank()) return word;
        String low = word.toLowerCase(Locale.forLanguageTag("ru"));
        if (low.contains("-")) {
            return Arrays.stream(low.split("-"))
                    .map(this::capitalize)
                    .collect(Collectors.joining("-"));
        }
        return Character.toUpperCase(low.charAt(0)) + low.substring(1);
    }
}
