package org.school.personalLoad.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurriculumLoadStandardTest {

    @Test
    void providesMaximumWeeklyLoadForEveryParallel() {
        assertEquals(BigDecimal.valueOf(21), CurriculumLoadStandard.maxHours(1));
        assertEquals(BigDecimal.valueOf(23), CurriculumLoadStandard.maxHours(4));
        assertEquals(BigDecimal.valueOf(29), CurriculumLoadStandard.maxHours(5));
        assertEquals(BigDecimal.valueOf(32), CurriculumLoadStandard.maxHours(7));
        assertEquals(BigDecimal.valueOf(33), CurriculumLoadStandard.maxHours(9));
        assertEquals(BigDecimal.valueOf(34), CurriculumLoadStandard.maxHours(11));
    }

    @Test
    void extractsParallelOnlyFromOrdinaryClassNames() {
        assertEquals(7, CurriculumLoadStandard.parallelOf("7-А"));
        assertNull(CurriculumLoadStandard.parallelOf("МГ:7-А/7-Б"));
    }
}
