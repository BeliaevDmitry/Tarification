package org.school.personalLoad.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CurriculumLoadStandard {

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^\\s*(\\d{1,2})");
    private static final Map<Integer, BigDecimal> MAX_HOURS = createMaxHours();

    private CurriculumLoadStandard() {
    }

    public static Map<Integer, BigDecimal> maxHoursByParallel() {
        return MAX_HOURS;
    }

    public static BigDecimal maxHours(int parallel) {
        return MAX_HOURS.get(parallel);
    }

    public static Integer parallelOf(String className) {
        Matcher matcher = PARALLEL_PATTERN.matcher(className == null ? "" : className);
        if (!matcher.find()) {
            return null;
        }
        int parallel = Integer.parseInt(matcher.group(1));
        return MAX_HOURS.containsKey(parallel) ? parallel : null;
    }

    private static Map<Integer, BigDecimal> createMaxHours() {
        Map<Integer, BigDecimal> result = new LinkedHashMap<>();
        result.put(1, BigDecimal.valueOf(21));
        result.put(2, BigDecimal.valueOf(23));
        result.put(3, BigDecimal.valueOf(23));
        result.put(4, BigDecimal.valueOf(23));
        result.put(5, BigDecimal.valueOf(29));
        result.put(6, BigDecimal.valueOf(30));
        result.put(7, BigDecimal.valueOf(32));
        result.put(8, BigDecimal.valueOf(33));
        result.put(9, BigDecimal.valueOf(33));
        result.put(10, BigDecimal.valueOf(34));
        result.put(11, BigDecimal.valueOf(34));
        return Map.copyOf(result);
    }
}
