package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicYearDeepLinkFrontendTest {

    @Test
    void academicYearFromDeepLinkOverridesStoredYearBeforeDataLoads() throws Exception {
        String authJs = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(authJs.contains("new URLSearchParams(window.location.search).get('academicYear')"));
        assertTrue(authJs.contains("setStoredAcademicYear(linkedAcademicYear)"));
        assertTrue(authJs.contains("requestedFromLink && availableCodes.has(requestedFromLink)"));
    }
}
