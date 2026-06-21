package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualLoadIdentityFrontendTest {

    @Test
    void assignmentIdentityIncludesCurriculumPartButNotEducationLevel() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/load.js"));

        assertTrue(js.contains("`${row.className}|${row.subjectName}|${row.curriculumPart || \"CORE\"}|${rowStudyPeriod(row)}${moduleToken}${groupSuffix(row)}`"));
        assertFalse(js.contains("${row.curriculumPart || \"CORE\"}|${row.educationLevel}|${rowStudyPeriod(row)}"));
        assertTrue(js.contains("curriculumPart: row.curriculumPart || \"CORE\""));
        assertTrue(js.contains("`|M:${row.__moduleId}`"));
        assertTrue(js.contains("curriculumModuleId: row.__moduleId || null"));
    }
}
