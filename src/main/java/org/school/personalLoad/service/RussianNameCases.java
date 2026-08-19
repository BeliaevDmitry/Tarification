package org.school.personalLoad.service;

import org.school.personalLoad.dto.PersonnelDtos.NameCases;

import java.util.Arrays;
import java.util.Locale;

public final class RussianNameCases {
    private RussianNameCases() {
    }

    public static NameCases derive(String source) {
        String normalized = normalize(source);
        if (normalized.isBlank()) return new NameCases(
                "", "", "", "", "", "", "", "", "", "", "", "");
        String[] parts = normalized.split(" ");
        boolean female = isFemale(parts);
        String surname = parts[0];
        String name = parts.length > 1 ? parts[1] : "";
        String patronymic = parts.length > 2 ? parts[2] : "";
        String tail = parts.length > 3 ? " " + String.join(" ", Arrays.copyOfRange(parts, 3, parts.length)) : "";
        String initials = initials(surname, name, patronymic);
        String genitiveSurname = decline(surname, female, CaseType.GENITIVE, true);
        String dativeSurname = decline(surname, female, CaseType.DATIVE, true);
        String accusativeSurname = decline(surname, female, CaseType.ACCUSATIVE, true);
        String instrumentalSurname = decline(surname, female, CaseType.INSTRUMENTAL, true);
        String prepositionalSurname = decline(surname, female, CaseType.PREPOSITIONAL, true);
        String initialsSuffix = initialsSuffix(name, patronymic);
        return new NameCases(
                normalized,
                join(genitiveSurname,
                        decline(name, female, CaseType.GENITIVE, false),
                        decline(patronymic, female, CaseType.GENITIVE, false)) + tail,
                join(dativeSurname,
                        decline(name, female, CaseType.DATIVE, false),
                        decline(patronymic, female, CaseType.DATIVE, false)) + tail,
                join(accusativeSurname,
                        decline(name, female, CaseType.ACCUSATIVE, false),
                        decline(patronymic, female, CaseType.ACCUSATIVE, false)) + tail,
                join(instrumentalSurname,
                        decline(name, female, CaseType.INSTRUMENTAL, false),
                        decline(patronymic, female, CaseType.INSTRUMENTAL, false)) + tail,
                join(prepositionalSurname,
                        decline(name, female, CaseType.PREPOSITIONAL, false),
                        decline(patronymic, female, CaseType.PREPOSITIONAL, false)) + tail,
                initials,
                genitiveSurname + initialsSuffix,
                dativeSurname + initialsSuffix,
                accusativeSurname + initialsSuffix,
                instrumentalSurname + initialsSuffix,
                prepositionalSurname + initialsSuffix
        );
    }

    /**
     * Определяет грамматический род ФИО для согласования слов в документах.
     * ФИО в системе хранится в именительном падеже; наиболее надёжным
     * признаком служит русское отчество.
     */
    public static boolean isFemale(String source) {
        String normalized = normalize(source);
        return !normalized.isBlank() && isFemale(normalized.split(" "));
    }

    private static String normalize(String source) {
        return Arrays.stream(String.valueOf(source == null ? "" : source).trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private static boolean isFemale(String[] parts) {
        return parts.length > 2 && femalePatronymic(parts[2]);
    }

    private static boolean femalePatronymic(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("вна") || lower.endsWith("чна");
    }

    private static String initials(String surname, String name, String patronymic) {
        return surname + initialsSuffix(name, patronymic);
    }

    private static String initialsSuffix(String name, String patronymic) {
        return (name.isBlank() ? "" : " " + Character.toUpperCase(name.charAt(0)) + ".")
                + (patronymic.isBlank() ? "" : Character.toUpperCase(patronymic.charAt(0)) + ".");
    }

    private static String join(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private enum CaseType { GENITIVE, DATIVE, ACCUSATIVE, INSTRUMENTAL, PREPOSITIONAL }

    private static String decline(String value, boolean female, CaseType type, boolean surname) {
        if (value == null || value.isBlank() || value.contains("-")) return value == null ? "" : value;
        String lower = value.toLowerCase(Locale.ROOT);
        if (surname && (lower.endsWith("ко") || lower.endsWith("ых") || lower.endsWith("их")
                || lower.endsWith("енко") || lower.endsWith("ук"))) return value;
        if (surname && female && (lower.endsWith("ова") || lower.endsWith("ева")
                || lower.endsWith("ина") || lower.endsWith("ына"))) {
            return stem(value, 1) + switch (type) {
                case GENITIVE, DATIVE, INSTRUMENTAL, PREPOSITIONAL -> "ой";
                case ACCUSATIVE -> "у";
            };
        }
        // Женские фамилии с согласным окончанием, включая мягкий знак
        // (Рысь, Блок, Врубель), в документах не склоняются. Женские имена
        // на мягкий знак (Любовь) по-прежнему обрабатываются ниже.
        if (surname && female && lower.endsWith("ь")) return value;
        if (female) return declineFemale(value, lower, type);
        return declineMale(value, lower, type);
    }

    private static String declineFemale(String value, String lower, CaseType type) {
        if (lower.endsWith("ия")) return stem(value, 1) + switch (type) {
            case GENITIVE, DATIVE, PREPOSITIONAL -> "и";
            case ACCUSATIVE -> "ю";
            case INSTRUMENTAL -> "ей";
        };
        if (lower.endsWith("ья")) return stem(value, 1) + switch (type) {
            case GENITIVE, DATIVE, PREPOSITIONAL -> "е";
            case ACCUSATIVE -> "ю";
            case INSTRUMENTAL -> "ей";
        };
        if (lower.endsWith("ая")) return stem(value, 2) + switch (type) {
            case GENITIVE, DATIVE, INSTRUMENTAL, PREPOSITIONAL -> "ой";
            case ACCUSATIVE -> "ую";
        };
        if (lower.endsWith("яя")) return stem(value, 2) + switch (type) {
            case GENITIVE, DATIVE, INSTRUMENTAL, PREPOSITIONAL -> "ей";
            case ACCUSATIVE -> "юю";
        };
        if (lower.endsWith("а")) return stem(value, 1) + switch (type) {
            case GENITIVE -> "ы";
            case DATIVE, PREPOSITIONAL -> "е";
            case ACCUSATIVE -> "у";
            case INSTRUMENTAL -> "ой";
        };
        if (lower.endsWith("я")) return stem(value, 1) + switch (type) {
            case GENITIVE, DATIVE, PREPOSITIONAL -> "и";
            case ACCUSATIVE -> "ю";
            case INSTRUMENTAL -> "ей";
        };
        if (lower.endsWith("ь")) return switch (type) {
            case GENITIVE, DATIVE, PREPOSITIONAL -> stem(value, 1) + "и";
            case ACCUSATIVE -> value;
            case INSTRUMENTAL -> stem(value, 1) + "ью";
        };
        return value;
    }

    private static String declineMale(String value, String lower, CaseType type) {
        if (lower.endsWith("ий")) return stem(value, 2) + switch (type) {
            case GENITIVE, ACCUSATIVE -> "ия";
            case DATIVE -> "ию";
            case INSTRUMENTAL -> "ием";
            case PREPOSITIONAL -> "ии";
        };
        if (lower.endsWith("ый") || lower.endsWith("ой")) return stem(value, 2) + switch (type) {
            case GENITIVE, ACCUSATIVE -> "ого";
            case DATIVE -> "ому";
            case INSTRUMENTAL -> "ым";
            case PREPOSITIONAL -> "ом";
        };
        if (lower.endsWith("й") || lower.endsWith("ь")) return stem(value, 1) + switch (type) {
            case GENITIVE, ACCUSATIVE -> "я";
            case DATIVE -> "ю";
            case INSTRUMENTAL -> "ем";
            case PREPOSITIONAL -> "е";
        };
        if (lower.endsWith("а")) return stem(value, 1) + switch (type) {
            case GENITIVE, ACCUSATIVE -> "ы";
            case DATIVE, PREPOSITIONAL -> "е";
            case INSTRUMENTAL -> "ой";
        };
        if (lower.endsWith("я")) return stem(value, 1) + switch (type) {
            case GENITIVE, ACCUSATIVE -> "и";
            case DATIVE -> "е";
            case INSTRUMENTAL -> "ей";
            case PREPOSITIONAL -> "е";
        };
        return value + switch (type) {
            case GENITIVE, ACCUSATIVE -> "а";
            case DATIVE -> "у";
            case INSTRUMENTAL -> "ом";
            case PREPOSITIONAL -> "е";
        };
    }

    private static String stem(String value, int count) {
        return value.substring(0, Math.max(0, value.length() - count));
    }
}
