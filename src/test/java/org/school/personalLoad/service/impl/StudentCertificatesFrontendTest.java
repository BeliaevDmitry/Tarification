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
        assertTrue(html.contains("id=\"certificate-student-search\""));
        assertTrue(html.contains("id=\"certificate-student-suggestions\""));
        assertTrue(html.contains("id=\"certificate-student\" type=\"hidden\""));
        assertTrue(html.contains("id=\"certificate-nosology-letter\""));
        assertTrue(html.contains("id=\"certificate-prolongation-panel\""));
        assertTrue(html.contains("id=\"certificate-direction-body\""));
        assertTrue(html.contains("value=\"CPMPC_RECOMMENDATION\">Рекомендация ЦМПК"));
        assertTrue(html.contains("id=\"certificate-recommendation-stage\""));
        assertTrue(html.contains("<select id=\"certificate-recommendation-program\">"));
        assertTrue(html.contains("Основная образовательная программа среднего образования."));
        assertTrue(html.contains("id=\"certificate-correction-fields\""));
        assertTrue(html.contains("ИПР/ИПРА имеется"));
        assertTrue(html.contains("<label>Уровень образования"));
        assertTrue(html.contains("id=\"certificate-education-program\" type=\"text\""));
        assertTrue(js.contains("[['ORIGINAL', 'Оригинал'], ['ELECTRONIC_COPY', 'Электронная копия']]"));
        assertTrue(js.contains(": [['COPY', 'Копия']]"));
        assertTrue(js.contains("nosologyCode: cpmpc ? certificateNosologyCode() : null"));
        assertTrue(js.contains("ipraPresent: mse &&"));
        assertTrue(js.contains("Для заключения ЦМПК обязательно укажите нозологию"));
        assertTrue(js.contains("`ФК ${student.studentId}`"));
        assertTrue(js.contains("Выберите ребёнка из появившейся подсказки"));
        assertTrue(js.contains("/api/contingent/special-support/nosologies"));
        assertTrue(js.contains("/api/contingent/special-support/correction-specialists"));
        assertTrue(js.contains("CPMPC_RECOMMENDATION: 'Рекомендация ЦМПК'"));
        assertTrue(js.contains("Выберите образовательную программу"));
        assertTrue(js.contains("correctionDirections: cpmpc || recommendation ? certificateDirectionPayload() : []"));
    }
}
