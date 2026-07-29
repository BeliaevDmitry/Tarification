package org.school.personalLoad.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadInRateSchemaInitializerTest {

    @Test
    void productionCompatibilityInitializerRepairsColumnsThatCannotBeAddedAsNotNullToPopulatedTables() throws Exception {
        String java = Files.readString(Path.of(
                "src/main/java/org/school/personalLoad/config/LoadInRateSchemaInitializer.java"));
        String sql = Files.readString(Path.of(
                "deploy/sql/2026-07-29_load_in_rate_compatibility.sql"));

        assertTrue(java.contains("ADD COLUMN IF NOT EXISTS in_rate_allocation_confirmed boolean DEFAULT false"));
        assertTrue(java.contains("SET in_rate_allocation_confirmed = false WHERE in_rate_allocation_confirmed IS NULL"));
        assertTrue(java.contains("ADD COLUMN IF NOT EXISTS load_hours_may_be_included_in_rate boolean DEFAULT false"));
        assertTrue(java.contains("SET load_hours_may_be_included_in_rate = false WHERE load_hours_may_be_included_in_rate IS NULL"));
        assertTrue(java.contains("ADD COLUMN IF NOT EXISTS grade_band varchar(32) DEFAULT 'ALL'"));
        assertTrue(java.contains("CREATE TABLE IF NOT EXISTS load_in_rate_rule"));
        assertTrue(java.contains("CREATE TABLE IF NOT EXISTS load_in_rate_rule_band"));
        assertTrue(sql.startsWith("BEGIN;"));
        assertTrue(sql.contains("UPDATE manual_load_entry"));
        assertTrue(sql.contains("UPDATE employment_contract"));
        assertTrue(sql.endsWith("COMMIT;\n"));
    }
}
