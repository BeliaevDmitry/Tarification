package org.school.MckoReport.MckoCompleks.util;

import java.util.Locale;

public final class DiagnosticCodeUtil {

    private DiagnosticCodeUtil() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d{4}-\\d{4}I?") || normalized.matches("\\d{1,4}")) {
            return normalized;
        }
        return null;
    }

    public static boolean isUsable(String value) {
        return normalize(value) != null;
    }
}
