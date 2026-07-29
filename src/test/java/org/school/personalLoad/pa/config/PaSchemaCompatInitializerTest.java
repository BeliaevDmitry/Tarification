package org.school.personalLoad.pa.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaSchemaCompatInitializerTest {

    @Test
    void legacyPaSpecificationRowsReceiveSafeGradingScaleBeforeNotNullConstraint() throws Exception {
        String java = Files.readString(Path.of(
                "src/main/java/org/school/personalLoad/pa/config/PaSchemaCompatInitializer.java"));
        String sql = Files.readString(Path.of(
                "deploy/sql/2026-07-29_load_in_rate_compatibility.sql"));

        assertTrue(java.contains("ADD COLUMN IF NOT EXISTS grading_scale VARCHAR(20) NOT NULL DEFAULT 'FIVE_POINT'"));
        assertTrue(java.contains("SET grading_scale='FIVE_POINT' WHERE grading_scale IS NULL"));
        assertTrue(java.contains("ALTER COLUMN grading_scale SET NOT NULL"));
        assertTrue(sql.contains("UPDATE pa_specification"));
        assertTrue(sql.contains("ALTER COLUMN grading_scale SET NOT NULL"));
    }
}
