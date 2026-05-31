package org.school.personalLoad.model;

import java.util.List;

public final class SubjectAreaNames {
    public static final List<String> BASE_AREAS = List.of(
            "Русский язык и литература",
            "Иностранные языки",
            "Математика и информатика",
            "Общественно-научные предметы",
            "Основы духовно-нравственной культуры народов России",
            "Естественно-научные предметы",
            "Искусство",
            "Технология",
            "Физическая культура и основы безопасности и защиты Родины",
            "Коррекционно-развивающая область",
            "Иное"
    );

    private SubjectAreaNames() {
    }

    public static boolean isBaseArea(String value) {
        String normalized = normalize(value);
        return BASE_AREAS.stream().anyMatch(area -> area.equalsIgnoreCase(normalized));
    }

    public static String resolveBaseArea(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Выберите одну из 11 предметных областей");
        }
        return BASE_AREAS.stream()
                .filter(area -> area.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Предметная область должна быть одной из 11 предметных областей"));
    }

    public static String defaultArea() {
        return BASE_AREAS.get(0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
