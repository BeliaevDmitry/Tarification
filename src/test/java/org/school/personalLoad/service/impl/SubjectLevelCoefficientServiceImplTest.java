package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.SubjectLevelCoefficientRequest;
import org.school.personalLoad.model.EducationStage;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SubjectLevelCoefficientServiceImplTest {

    @Mock
    private SubjectLevelCoefficientRepository repository;

    @Test
    void saveUpdatesExistingSubjectLevelCoefficient() {
        SubjectLevelCoefficientEntry existing = new SubjectLevelCoefficientEntry();
        existing.setId(7L);
        existing.setSubjectName("Алгебра");
        existing.setEducationStage(EducationStage.OOO);
        existing.setCoefficient(BigDecimal.ONE);
        SubjectLevelCoefficientRequest request = new SubjectLevelCoefficientRequest();
        request.setSubjectName(" Алгебра ");
        request.setEducationStage(EducationStage.OOO);
        request.setCoefficient(new BigDecimal("1.5"));
        when(repository.findBySubjectNameIgnoreCaseAndEducationStage("Алгебра", EducationStage.OOO)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubjectLevelCoefficientServiceImpl service = new SubjectLevelCoefficientServiceImpl(repository);

        SubjectLevelCoefficientEntry saved = service.save(request);

        assertEquals(7L, saved.getId());
        assertEquals("Алгебра", saved.getSubjectName());
        assertEquals(EducationStage.OOO, saved.getEducationStage());
        assertEquals(new BigDecimal("1.5"), saved.getCoefficient());
        ArgumentCaptor<SubjectLevelCoefficientEntry> captor = ArgumentCaptor.forClass(SubjectLevelCoefficientEntry.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getId());
    }

    @Test
    void importFromExcelReadsTwoColumnSubjectStageWorkbook() throws Exception {
        when(repository.findBySubjectNameIgnoreCaseAndEducationStage(anyString(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubjectLevelCoefficientServiceImpl service = new SubjectLevelCoefficientServiceImpl(repository);

        Map<String, Object> result = service.importFromExcel(coefficientsWorkbook());

        assertEquals(4, result.get("imported"));
        assertEquals(2, result.get("skipped"));
        ArgumentCaptor<SubjectLevelCoefficientEntry> captor = ArgumentCaptor.forClass(SubjectLevelCoefficientEntry.class);
        verify(repository, times(4)).save(captor.capture());
        List<SubjectLevelCoefficientEntry> saved = captor.getAllValues();
        assertEquals("Алгебра", saved.get(0).getSubjectName());
        assertEquals(EducationStage.OOO, saved.get(0).getEducationStage());
        assertEquals(new BigDecimal("1.5"), saved.get(0).getCoefficient());
        assertEquals("Разговоры о важном", saved.get(1).getSubjectName());
        assertEquals(EducationStage.NOO, saved.get(1).getEducationStage());
        assertEquals("Разговоры о важном", saved.get(2).getSubjectName());
        assertEquals(EducationStage.OOO, saved.get(2).getEducationStage());
        assertEquals("Разговоры о важном", saved.get(3).getSubjectName());
        assertEquals(EducationStage.SOO, saved.get(3).getEducationStage());
    }

    @Test
    void exportWorkbookUsesTwoColumnsWithCombinedSubjectStage() throws Exception {
        when(repository.findAll()).thenReturn(List.of(coefficient("Алгебра", EducationStage.OOO, "1.5")));
        SubjectLevelCoefficientServiceImpl service = new SubjectLevelCoefficientServiceImpl(repository);

        Resource resource = service.exportWorkbook();

        try (Workbook workbook = WorkbookFactory.create(resource.getInputStream())) {
            Sheet sheet = workbook.getSheet("Коэффициенты");
            assertEquals("Предмет", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Для расчета", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Алгебра ООО", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("1.5", sheet.getRow(1).getCell(1).getStringCellValue());
        }
    }

    private MockMultipartFile coefficientsWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Коэффициенты");
            Row blank = sheet.createRow(0);
            blank.createCell(1).setCellValue("Для расчета");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("Предмет");
            header.createCell(1).setCellValue("Для расчета");
            Row algebra = sheet.createRow(2);
            algebra.createCell(0).setCellValue("Алгебра ООО");
            algebra.createCell(1).setCellValue("1.5");
            Row important = sheet.createRow(3);
            important.createCell(0).setCellValue("Разговоры о важном");
            important.createCell(1).setCellValue(1);
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "coefficients.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

    private SubjectLevelCoefficientEntry coefficient(String subjectName, EducationStage stage, String coefficient) {
        SubjectLevelCoefficientEntry entry = new SubjectLevelCoefficientEntry();
        entry.setSubjectName(subjectName);
        entry.setEducationStage(stage);
        entry.setCoefficient(new BigDecimal(coefficient));
        return entry;
    }
}
