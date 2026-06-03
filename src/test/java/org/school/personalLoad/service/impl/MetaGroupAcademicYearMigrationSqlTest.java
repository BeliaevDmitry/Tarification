package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaGroupAcademicYearMigrationSqlTest {

    @Test
    void migrationAddsAcademicYearAndReplacesUniqueConstraintWithYearScope() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("add column if not exists academic_year varchar(9)"));
        assertTrue(sql.contains("alter column academic_year set not null"));
        assertTrue(sql.contains("drop constraint if exists uk_meta_group_scope"));
        assertTrue(sql.contains("add constraint uk_meta_group_year_scope"));
        assertTrue(sql.contains("unique (academic_year, number_school_building, parallel, name, class_type)"));
    }

    @Test
    void migrationFailsFastForUnusedMetaGroups() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("not exists ("));
        assertTrue(sql.contains("from curriculum_plan_entry cpe"));
        assertTrue(sql.contains("from manual_load_entry mle"));
        assertTrue(sql.contains("cannot assign meta_group.academic_year: unused meta groups exist. remove or assign them manually before migration."));
    }

    @Test
    void migrationAssignsSingleYearMetaGroupsAndSplitsMultiYearMetaGroups() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("create temp table _meta_group_year_usage"));
        assertTrue(sql.contains("count(*) as year_count"));
        assertTrue(sql.contains("and latest.year_count = 1"));
        assertTrue(sql.contains("and latest.year_count > 1"));
        assertTrue(sql.contains("create temp table _meta_group_year_copy"));
        assertTrue(sql.contains("insert into meta_group ("));
        assertTrue(sql.contains("school_building_id"));
        assertTrue(sql.contains("study_period_setting_id"));
        assertTrue(sql.contains("academic_year"));
    }

    @Test
    void migrationRepointsHistoricalCurriculumAndManualLoadRowsAndChecksCrossYearReferences() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("update curriculum_plan_entry cpe"));
        assertTrue(sql.contains("set meta_group_id = copy.new_meta_group_id"));
        assertTrue(sql.contains("where cpe.meta_group_id = copy.original_meta_group_id"));
        assertTrue(sql.contains("and cpe.academic_year = copy.academic_year"));
        assertTrue(sql.contains("update manual_load_entry mle"));
        assertTrue(sql.contains("where mle.meta_group_id = copy.original_meta_group_id"));
        assertTrue(sql.contains("and mle.academic_year = copy.academic_year"));
        assertTrue(sql.contains("cpe.academic_year <> mg.academic_year"));
        assertTrue(sql.contains("mle.academic_year <> mg.academic_year"));
        assertTrue(sql.contains("raise exception 'curriculum_plan_entry rows reference meta_group from another academic_year"));
        assertTrue(sql.contains("raise exception 'manual_load_entry rows reference meta_group from another academic_year"));
    }

    @Test
    void auditSqlContainsMetaGroupAcademicYearChecks() throws Exception {
        String audit = Files.readString(Path.of("scripts/migrations/audit/verify_completed_fk_migration.sql"))
                .toLowerCase(Locale.ROOT);

        assertTrue(audit.contains("meta_group.academic_year is null"));
        assertTrue(audit.contains("curriculum academic_year does not match meta_group.academic_year"));
        assertTrue(audit.contains("manual load academic_year does not match meta_group.academic_year"));
    }

    private String migration() throws Exception {
        return Files.readString(Path.of("scripts/migrations/2026-06-04_meta_group_academic_year.sql"))
                .toLowerCase(Locale.ROOT);
    }
}
