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
        assertTrue(loadJs.contains("return !Boolean(row?.metaGroup);"));
        assertTrue(loadJs.contains("const filtered = scoped.filter(contributesToManualLoad);"));
        assertFalse(normalized.contains("scoped.filter((row) => !boolean(row.metagroup))"));
    }

    private String read(String path) throws Exception {
        return readRaw(path).toLowerCase(Locale.ROOT);
    }

    private String readRaw(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
