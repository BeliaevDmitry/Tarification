package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaGroupYearAwareSyncTriggerSqlTest {

    @Test
    void migrationRunsAfterMetaGroupAcademicYearMigrationAndRedefinesTrigger() throws Exception {
        Path migration = Path.of("scripts/migrations/2026-06-05_meta_group_year_aware_sync_trigger.sql");

        assertTrue(migration.getFileName().toString().compareTo("2026-06-04_meta_group_academic_year.sql") > 0);
        String sql = read(migration);
        assertTrue(sql.contains("create or replace function trg_sync_meta_group_fk() returns trigger"));
        assertTrue(sql.contains("academic_year is required for explicit meta group row"));
    }

    @Test
    void nullMetaGroupIdResolutionIsAcademicYearAwareForCurriculumAndManualLoad() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("where academic_year = new.academic_year"));
        assertTrue(sql.contains("and lower(trim(number_school_building)) = lower(trim(new.number_school_building))"));
        assertTrue(sql.contains("and lower(trim(name)) = lower(trim(regexp_replace(new.class_name"));
        assertTrue(sql.contains("meta group not found for academic_year="));
        assertTrue(sql.contains("create the meta group with a physical school building before saving curriculum/manual-load rows"));
        assertTrue(sql.contains("raise exception"));
        assertFalse(sql.contains("insert into meta_group("));
    }

    @Test
    void explicitMetaGroupIdFromAnotherAcademicYearIsRejected() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("select name, number_school_building, academic_year"));
        assertTrue(sql.contains("if myear is distinct from new.academic_year then"));
        assertTrue(sql.contains("belongs to academic_year"));
    }

    @Test
    void explicitMetaGroupRowsContinueToNullClassId() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("new.meta_group := true"));
        assertTrue(sql.contains("new.class_id := null"));
        assertTrue(sql.contains("if tg_table_name = 'manual_load_entry' then"));
    }

    @Test
    void psqlRegressionScriptCoversBothYearsBothTablesAndCrossYearRejection() throws Exception {
        String sql = read(Path.of("scripts/migrations/tests/2026-06-05_meta_group_year_aware_sync_trigger_test.sql"));

        assertTrue(sql.contains("'2025/2026'"));
        assertTrue(sql.contains("'2026/2027'"));
        assertTrue(sql.contains("curriculum explicit мг row resolved"));
        assertTrue(sql.contains("manual-load explicit мг row resolved"));
        assertTrue(sql.contains("cross-year explicit meta_group_id was accepted unexpectedly"));
        assertTrue(sql.contains("class_id=null"));
        assertTrue(sql.contains("school_building_id"));
        assertTrue(sql.contains("building_group_id"));
        assertTrue(sql.contains("subject_id"));
        assertTrue(sql.contains("teacher_id"));
        assertTrue(sql.contains("missing meta group explicit row was accepted unexpectedly"));
    }

    private String migration() throws Exception {
        return read(Path.of("scripts/migrations/2026-06-05_meta_group_year_aware_sync_trigger.sql"));
    }

    private String read(Path path) throws Exception {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}
