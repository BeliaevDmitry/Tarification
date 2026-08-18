package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StudentCertificatesFrontendTest {

    @Test
    void supportTabIsReplacedWithCertificateRegisterBoundToStudentProfile() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/contingent.html"));
        String js = Files.readString(Path.of("src/main/resources/static/contingent.js"));
        String controller = Files.readString(Path.of(
                "src/main/java/org/school/personalLoad/controller/api/StudentSupportController.java"
        ));
        String exchange = Files.readString(Path.of(
                "src/main/java/org/school/personalLoad/service/impl/StudentDataExchangeServiceImpl.java"
        ));

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
        assertTrue(html.contains("<select id=\"certificate-education-program\">"));
        assertTrue(html.contains("id=\"certificate-education-program-other\" type=\"text\""));
        assertTrue(html.contains("id=\"certificate-education-program-source\""));
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
        assertTrue(js.contains("/documents/education-defaults"));
        assertTrue(js.contains("certificateUi.validTo.readOnly = cpmpc && studentId > 0"));
        assertTrue(js.contains("prolongationAvailable: String(documentType === 'CPMPC_CONCLUSION'"));
        assertTrue(js.contains("educationProgramCustom: cpmpc && certificateUi.educationProgram.value === '__OTHER__'"));
        assertTrue(js.contains("defaults.educationPrograms || []"));
        assertTrue(js.contains("<option value=\"__OTHER__\">Другое</option>"));
        assertFalse(html.contains("id=\"certificate-file\""));
        assertFalse(html.contains("id=\"support-document-file\""));
        assertFalse(html.contains(">Скан<"));
        assertFalse(js.contains("data-certificate-delete-attachment"));
        assertFalse(js.contains("data-support-delete-attachment"));
        assertFalse(controller.contains("/documents/{documentId}/attachments"));
        assertFalse(exchange.contains("Прикреплено файлов"));
    }
}
