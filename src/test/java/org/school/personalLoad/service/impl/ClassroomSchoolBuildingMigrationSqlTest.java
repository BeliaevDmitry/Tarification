package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomSchoolBuildingMigrationSqlTest {

    private static final Path MIGRATION = Path.of("scripts/migrations/2026-06-02_classroom_school_building_fk.sql");

    @Test
    void migrationBackfillsSchoolBuildingIdByAddressWithoutBuildingGroupFilter() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("add column if not exists school_building_id bigint"));
        assertTrue(sql.contains("foreign key (school_building_id)"));
        assertTrue(sql.contains("references school_building(id)"));
        assertTrue(sql.contains("set school_building_id = unique_match.school_building_id"));
        assertTrue(sql.contains("sb.address"));
        assertTrue(sql.contains("c.campus_address"));
        assertFalse(sql.contains("sb.building_group_id = c.building_group_id"));
        assertFalse(sql.contains("c.building_group_id = sb.building_group_id"));
    }

    @Test
    void migrationFailsWhenAddressHasNoMatchOrMultipleMatches() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("match_count = 0"));
        assertTrue(sql.contains("match_count > 1"));
        assertTrue(sql.contains("raise exception 'cannot backfill classroom_leadership_entry.school_building_id"));
    }
}
