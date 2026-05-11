package org.school.personalLoad.config;

import java.util.Locale;
import java.util.Map;

public final class SchoolCodeResolver {

    private static final Map<String, String> DOMAIN_TO_CODE = Map.of(
            "schadmin.ru", "7",
            "www.schadmin.ru", "7",
            "schadmindemo.ru", "demo",
            "www.schadmindemo.ru", "demo",
            "sch-1811.ru", "1811",
            "www.sch-1811.ru", "1811"
    );

    private SchoolCodeResolver() {
    }

    public static String resolve() {
        String schoolCode = getenv("SCHOOL_CODE");
        if (!schoolCode.isBlank()) {
            return schoolCode.toLowerCase(Locale.ROOT);
        }

        String appDomain = getenv("APP_DOMAIN").toLowerCase(Locale.ROOT);
        if (!appDomain.isBlank()) {
            return DOMAIN_TO_CODE.getOrDefault(appDomain, "demo");
        }

        return "demo";
    }

    private static String getenv(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }
}
