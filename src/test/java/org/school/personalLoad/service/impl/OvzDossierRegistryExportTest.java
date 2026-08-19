package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.OvzDossierService;
import org.school.personalLoad.service.StudentSupportDocumentService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class OvzDossierRegistryExportTest {

    @Test
    void exportUsesExactlyTheFilteredAndSortedStudentIdsFromRegistryView() throws Exception {
        OvzDossierService service = spy(new OvzDossierService(
                mock(StudentSupportDocumentService.class), mock(StudentSupportDocumentRepository.class),
                mock(StudentProfileRepository.class), mock(StudentClassEnrollmentRepository.class),
                mock(OvzWorkflowStageRepository.class), mock(OvzApplicationChoiceRepository.class),
                mock(PpkProtocolRepository.class), mock(StudentSupportDocumentCorrectionRepository.class),
                mock(StudentSupportStatusRepository.class), mock(StudentSupportDocumentAttachmentRepository.class)));
        doReturn(List.of(row(11L, "Иванов Иван"), row(22L, "Петров Пётр"), row(33L, "Сидоров Семён")))
                .when(service).registry(eq("2026/2027"), any(LocalDate.class));

        byte[] exported = service.exportRegistry("2026/2027", List.of(33L, 11L));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheet("Реестр ОВЗ");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(33);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Сидоров Семён");
            assertThat(sheet.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(11);
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Этапы");
        }
    }

    private static OvzDtos.DossierSummary row(long id, String fullName) {
        OvzDtos.DossierSummary row = new OvzDtos.DossierSummary();
        row.setStudentId(id); row.setFullName(fullName); row.setClassName("5-А");
        row.setCorrectionDirections(List.of()); row.setStages(List.of());
        return row;
    }
}
