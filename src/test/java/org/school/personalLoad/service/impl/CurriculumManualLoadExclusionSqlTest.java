package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumManualLoadExclusionSqlTest {

    @Test
    void migrationAddsBackfillsAndValidatesManualLoadExclusionFlag() throws Exception {
        String sql = read(Path.of("scripts/migrations/2026-06-06_curriculum_manual_load_exclusion_flag.sql"));

        assertTrue(sql.contains("add column if not exists excluded_from_manual_load boolean not null default false"));
        assertTrue(sql.contains("set excluded_from_manual_load = true"));
        assertTrue(sql.contains("meta_group = true"));
        assertTrue(sql.contains("class_id is not null"));
        assertTrue(sql.contains("meta_group_id is null"));
        assertTrue(sql.contains("set excluded_from_manual_load = false"));
        assertTrue(sql.contains("where meta_group_id is not null"));
        assertTrue(sql.contains("explicit meta-group curriculum rows excluded from manual load"));
        assertTrue(sql.contains("ordinary legacy meta-group member rows not excluded from manual load"));
    }

    @Test
    void redefinedTriggerKeepsExplicitMetaGroupRowsAssignable() throws Exception {
        String sql = read(Path.of("scripts/migrations/2026-06-06_curriculum_manual_load_exclusion_flag.sql"));

        assertTrue(sql.contains("create or replace function trg_sync_meta_group_fk() returns trigger"));
        assertTrue(sql.contains("where academic_year = new.academic_year"));
        assertTrue(sql.contains("and lower(trim(number_school_building)) = lower(trim(new.number_school_building))"));
        assertTrue(sql.contains("if myear is distinct from new.academic_year then"));
        assertTrue(sql.contains("new.meta_group := true"));
        assertTrue(sql.contains("new.excluded_from_manual_load := false"));
        assertTrue(sql.contains("new.class_id := null"));
    }

    @Test
    void auditContainsBlockingExclusionChecks() throws Exception {
        String sql = read(Path.of("scripts/migrations/audit/verify_completed_fk_migration.sql"));

        assertTrue(sql.contains("explicit meta curriculum rows excluded from manual load"));
        assertTrue(sql.contains("ordinary legacy meta rows not excluded from manual load"));
        assertTrue(sql.contains("excluded_from_manual_load = true"));
        assertTrue(sql.contains("excluded_from_manual_load = false"));
    }

    private String read(Path path) throws Exception {
        return Files.readString(path).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
