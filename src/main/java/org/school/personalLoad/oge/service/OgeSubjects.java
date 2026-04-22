package org.school.personalLoad.oge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OgeSubjects {
    public static final List<String> CORE_SUBJECTS = List.of(
            "Русский язык", "Математика", "Физика", "Химия", "Информатика и ИКТ", "Биология",
            "История", "География", "Английский язык", "Немецкий язык", "Французский язык",
            "Обществознание", "Испанский язык", "Литература"
    );

    public static final List<Integer> SUBJECT_CODES_ORDER = List.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 18, 29, 30, 31, 33, 51, 52, 53, 54, 55, 56,
            57, 58, 59, 60, 61, 62, 63, 68
    );

    public static final Map<Integer, String> CODE_TO_LABEL = Map.ofEntries(
            Map.entry(1, "1 - Русский язык"), Map.entry(2, "2 - Математика"), Map.entry(3, "3 - Физика"),
            Map.entry(4, "4 - Химия"), Map.entry(5, "5 - Информатика и ИКТ"), Map.entry(6, "6 - Биология"),
            Map.entry(7, "7 - История"), Map.entry(8, "8 - География"), Map.entry(9, "9 - Английский язык"),
            Map.entry(10, "10 - Немецкий язык"), Map.entry(11, "11 - Французский язык"),
            Map.entry(12, "12 - Обществознание"), Map.entry(13, "13 - Испанский язык"),
            Map.entry(18, "18 - Литература"), Map.entry(29, "29 - Английский язык (устный)"),
            Map.entry(30, "30 - Немецкий язык (устный)"), Map.entry(31, "31 - Французский язык (устный)"),
            Map.entry(33, "33 - Испанский язык (устный)"), Map.entry(51, "51 - Русский язык"),
            Map.entry(52, "52 - Математика"), Map.entry(53, "53 - Физика"), Map.entry(54, "54 - Химия"),
            Map.entry(55, "55 - Информатика и ИКТ"), Map.entry(56, "56 - Биология"), Map.entry(57, "57 - История"),
            Map.entry(58, "58 - География"), Map.entry(59, "59 - Английский язык"), Map.entry(60, "60 - Немецкий язык"),
            Map.entry(61, "61 - Французский язык"), Map.entry(62, "62 - Обществознание"),
            Map.entry(63, "63 - Испанский язык"), Map.entry(68, "68 - Литература")
    );

    private static final Map<Integer, String> CODE_TO_CORE = new LinkedHashMap<>();

    static {
        CODE_TO_CORE.put(1, "Русский язык");
        CODE_TO_CORE.put(51, "Русский язык");
        CODE_TO_CORE.put(2, "Математика");
        CODE_TO_CORE.put(52, "Математика");
        CODE_TO_CORE.put(3, "Физика");
        CODE_TO_CORE.put(53, "Физика");
        CODE_TO_CORE.put(4, "Химия");
        CODE_TO_CORE.put(54, "Химия");
        CODE_TO_CORE.put(5, "Информатика и ИКТ");
        CODE_TO_CORE.put(55, "Информатика и ИКТ");
        CODE_TO_CORE.put(6, "Биология");
        CODE_TO_CORE.put(56, "Биология");
        CODE_TO_CORE.put(7, "История");
        CODE_TO_CORE.put(57, "История");
        CODE_TO_CORE.put(8, "География");
        CODE_TO_CORE.put(58, "География");
        CODE_TO_CORE.put(9, "Английский язык");
        CODE_TO_CORE.put(29, "Английский язык");
        CODE_TO_CORE.put(59, "Английский язык");
        CODE_TO_CORE.put(10, "Немецкий язык");
        CODE_TO_CORE.put(30, "Немецкий язык");
        CODE_TO_CORE.put(60, "Немецкий язык");
        CODE_TO_CORE.put(11, "Французский язык");
        CODE_TO_CORE.put(31, "Французский язык");
        CODE_TO_CORE.put(61, "Французский язык");
        CODE_TO_CORE.put(12, "Обществознание");
        CODE_TO_CORE.put(62, "Обществознание");
        CODE_TO_CORE.put(13, "Испанский язык");
        CODE_TO_CORE.put(33, "Испанский язык");
        CODE_TO_CORE.put(63, "Испанский язык");
        CODE_TO_CORE.put(18, "Литература");
        CODE_TO_CORE.put(68, "Литература");
    }

    private OgeSubjects() {
    }

    public static String toCoreSubject(Integer code) {
        return CODE_TO_CORE.get(code);
    }

    public static String normalizeClassName(String value) {
        if (value == null) return "";
        String normalized = value.trim().replace('—', '-').replace('–', '-')
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("^(\\d+)([А-ЯA-Z])$", "$1-$2");
        normalized = normalized.replaceAll("^(\\d+)\\-?([А-ЯA-Z])$", "$1-$2");
        return normalized;
    }

    public static String normalizeFio(String fio) {
        if (fio == null) return "";
        return fio.trim().replaceAll("\\s+", " ");
    }

    public static String normalizeSnils(String snils) {
        if (snils == null) return "";
        return snils.replaceAll("[^0-9]", "");
    }
}
