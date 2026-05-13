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

    public static String resolve(String host) {
        for (String candidate : splitHosts(host)) {
            String normalizedHost = normalizeHost(candidate);
            if (!normalizedHost.isBlank()) {
                String byHost = DOMAIN_TO_CODE.get(normalizedHost);
                if (byHost != null && !byHost.isBlank()) {
                    return byHost;
                }
            }
        }
        return resolve();
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

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(0, colon);
        }
        return normalized;
    }

    private static String[] splitHosts(String host) {
        if (host == null || host.isBlank()) {
            return new String[0];
        }
        return host.split(",");
    }
}
