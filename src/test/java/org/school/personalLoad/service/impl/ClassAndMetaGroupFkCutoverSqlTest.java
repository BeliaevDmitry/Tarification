package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassAndMetaGroupFkCutoverSqlTest {

    @Test
    void classTailQueriesUseClassIdWithoutLegacyStringFallback() throws Exception {
        String curriculumRepository = read("src/main/java/org/school/personalLoad/repository/CurriculumPlanEntryRepository.java");
        String manualRepository = read("src/main/java/org/school/personalLoad/repository/ManualLoadEntryRepository.java");
        String classroomService = read("src/main/java/org/school/personalLoad/service/impl/ClassroomLeadershipServiceImpl.java");

        assertTrue(curriculumRepository.contains("where academic_year = :academicyear and class_id = :classid"));
        assertTrue(manualRepository.contains("where academic_year = :academicyear and class_id = :classid"));
        assertFalse(curriculumRepository.contains("or (lower(number_school_building)"));
        assertFalse(manualRepository.contains("or (lower(number_school_building)"));
        assertFalse(classroomService.contains("renameclasseverywhere"));
        assertFalse(classroomService.contains("synccurriculumbuildingbyclass"));
        assertFalse(classroomService.contains("syncmanualloadbuildingbyclass"));
    }

    @Test
    void metaGroupOperationsUseMetaGroupIdWithoutClassNameLookup() throws Exception {
        String curriculumRepository = read("src/main/java/org/school/personalLoad/repository/CurriculumPlanEntryRepository.java");
        String manualRepository = read("src/main/java/org/school/personalLoad/repository/ManualLoadEntryRepository.java");
        String metaGroupController = read("src/main/java/org/school/personalLoad/controller/api/MetaGroupController.java");

        assertTrue(curriculumRepository.contains("where meta_group_id = :metagroupid"));
        assertTrue(manualRepository.contains("where meta_group_id = :metagroupid"));
        assertTrue(metaGroupController.contains("findallbymetagroupid(existing.getid())"));
        assertTrue(metaGroupController.contains("curriculumplanentryrepository.deletebymetagroupid(existing.getid())"));
        assertTrue(metaGroupController.contains("manualloadentryrepository.deletebymetagroupid(existing.getid())"));
        assertFalse(metaGroupController.contains("findallbynumberschoolbuildingandclassname"));
        assertFalse(metaGroupController.contains("deletebynumberschoolbuildingandclassname"));
    }

    @Test
    void loadFrontendShowsExplicitMetaGroupsButSkipsOrdinaryMembers() throws Exception {
        String loadJs = readRaw("src/main/resources/static/load.js");
        String normalized = loadJs.toLowerCase(Locale.ROOT);

        assertTrue(loadJs.contains("function isExplicitMetaGroupRow(row)"));
        assertTrue(loadJs.contains("row?.metaGroupId != null"));
        assertTrue(loadJs.contains("startsWith(\"МГ:\")"));
        assertTrue(loadJs.contains("function contributesToManualLoad(row)"));
        assertTrue(loadJs.contains("if (isExplicitMetaGroupRow(row)) return true;"));
        assertTrue(loadJs.contains("return !Boolean(row?.excludedFromManualLoad);"));
        assertTrue(loadJs.contains("const filtered = scoped.filter(contributesToManualLoad);"));
        assertFalse(normalized.contains("scoped.filter((row) => !boolean(row.metagroup))"));
    }


    @Test
    void metaGroupSchoolBuildingMigrationIsNullableFkOnlyAndAudited() throws Exception {
        String migration = read("scripts/migrations/2026-06-03_meta_group_school_building_fk.sql");
        String audit = read("scripts/migrations/audit/verify_meta_group_school_building.sql");

        assertTrue(migration.startsWith("begin;"));
        assertTrue(migration.contains("alter table meta_group"));
        assertTrue(migration.contains("add column if not exists school_building_id bigint"));
        assertTrue(migration.contains("foreign key (school_building_id)"));
        assertTrue(migration.contains("references school_building(id)"));
        assertTrue(migration.contains("create index if not exists idx_meta_group_school_building_id"));
        assertFalse(migration.contains("set not null"));
        assertFalse(migration.contains("update meta_group"));
        assertTrue(audit.contains("meta groups without school_building_id"));
        assertTrue(audit.contains("manual-load metagroup rows whose parent has no school_building_id"));
        assertTrue(audit.contains("duplicate metagroup manual-load rows"));
    }

    @Test
    void metaGroupUiAndManualLoadScopeUseSchoolBuildingId() throws Exception {
        String curriculumHtml = readRaw("src/main/resources/static/curriculum.html");
        String curriculumJs = readRaw("src/main/resources/static/curriculum.js");
        String manualRepository = read("src/main/java/org/school/personalLoad/repository/ManualLoadEntryRepository.java");

        assertTrue(curriculumHtml.contains("Физическая площадка проведения занятий"));
        assertTrue(curriculumHtml.contains("select name=\"schoolBuildingId\" required"));
        assertTrue(curriculumJs.contains("renderMetaGroupFormSchoolBuildingOptions"));
        assertTrue(curriculumJs.contains("schoolBuildingId: Number(form.get(\"schoolBuildingId\")) || null"));
        assertTrue(manualRepository.contains("left join meta_group mg on mg.id = m.meta_group_id"));
        assertTrue(manualRepository.contains("mg.school_building_id = :schoolbuildingid"));
        assertTrue(manualRepository.contains("deletebyacademicyearandmetagroupids"));
    }

    @Test
    void completedFkAuditChecksExplicitMetaGroupSnapshotsAgainstParent() throws Exception {
        String audit = readRaw("scripts/migrations/audit/verify_completed_fk_migration.sql");

        assertTrue(audit.contains("curriculum explicit meta row organizational SP differs from parent meta_group"));
        assertTrue(audit.contains("manual explicit meta row organizational SP differs from parent meta_group"));
        assertTrue(audit.contains("cpe.class_name IS DISTINCT FROM ('МГ:' || mg.name)"));
        assertTrue(audit.contains("mle.class_name IS DISTINCT FROM ('МГ:' || mg.name)"));
    }

    private String read(String path) throws Exception {
        return readRaw(path).toLowerCase(Locale.ROOT);
    }

    private String readRaw(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
