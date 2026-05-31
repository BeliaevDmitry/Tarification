package org.school.educationalwork.src.main.java.org.school.educationalwork.parser;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassNameNormalizer {
    private static final Pattern CLASS_PATTERN = Pattern.compile("^(?<grade>1[01]|[1-9])\\s*[-–—.]?\\s*[\\\"«]?\\s*(?<letter>[A-ZА-ЯЁ])\\s*[\\\"»]?$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Map<Character, Character> LATIN_LOOKALIKE = Map.of(
            'A', 'А', 'B', 'В', 'C', 'С', 'E', 'Е', 'H', 'Н', 'K', 'К',
            'M', 'М', 'O', 'О', 'P', 'Р', 'T', 'Т', 'X', 'Х'
    );

    public Optional<String> normalize(String raw) {
        if (raw == null) return Optional.empty();
        String cleaned = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("ru"));
        Matcher matcher = CLASS_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return Optional.empty();
        char letter = matcher.group("letter").charAt(0);
        letter = LATIN_LOOKALIKE.getOrDefault(letter, letter);
        return Optional.of(matcher.group("grade") + letter);
    }
}
