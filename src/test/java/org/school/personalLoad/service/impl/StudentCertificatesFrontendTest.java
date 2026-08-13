package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentCertificatesFrontendTest {

    @Test
    void supportTabIsReplacedWithCertificateRegisterBoundToStudentProfile() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/contingent.html"));
        String js = Files.readString(Path.of("src/main/resources/static/contingent.js"));

        assertTrue(html.contains("data-contingent-tab=\"support\">Справки"));
        assertTrue(html.contains("data-contingent-tab=\"nosologies\">Справочник нозологий"));
        assertTrue(html.contains("data-contingent-pane=\"nosologies\""));
        assertTrue(html.contains("id=\"certificate-student\""));
        assertTrue(html.contains("id=\"certificate-nosology-letter\""));
        assertTrue(html.contains("id=\"certificate-prolongation-panel\""));
        assertTrue(html.contains("id=\"certificate-direction-body\""));
        assertTrue(html.contains("ИПР/ИПРА имеется"));
        assertTrue(js.contains("student.recordNumber ? `ФК ${student.recordNumber}`"));
        assertTrue(js.contains("/api/contingent/special-support/nosologies"));
        assertTrue(js.contains("/api/contingent/special-support/correction-specialists"));
        assertTrue(js.contains("correctionDirections: cpmpc ? certificateDirectionPayload() : []"));
    }
}
