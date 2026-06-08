package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectArea;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.SubjectAreaRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class SubjectCatalogServiceImplFkCutoverTest {

    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;
    @Mock
    private SubjectAreaRepository subjectAreaRepository;

    @Test
    void updateSubjectAreaUsesSubjectAreaIdAndKeepsRelationAfterAreaRename() {
        SubjectCatalogEntry existing = new SubjectCatalogEntry();
        existing.setId(5L);
        existing.setSubjectName("Математика");
        existing.setSubjectType(SubjectType.CORE);
        existing.setSubjectAreaRef(area(7L, "Старое название"));
        existing.setSubjectAreaName("Старое название");
        SubjectArea renamedArea = area(7L, "Математика и информатика");
        SubjectCreateRequest request = new SubjectCreateRequest();
        request.setSubjectName("Математика");
        request.setSubjectType(SubjectType.CORE);
        request.setSubjectAreaId(7L);
        request.setSubjectAreaName("Любой legacy текст");
        SubjectCatalogServiceImpl service = new SubjectCatalogServiceImpl(subjectCatalogRepository, subjectAreaRepository);
        when(subjectCatalogRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectCatalogRepository.findBySubjectNameAndSubjectType("Математика", SubjectType.CORE)).thenReturn(Optional.of(existing));
        when(subjectAreaRepository.findById(7L)).thenReturn(Optional.of(renamedArea));
        when(subjectCatalogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectCatalogEntry saved = service.update(5L, request);

        assertEquals(7L, saved.getSubjectAreaId());
        assertEquals("Математика и информатика", saved.getSubjectAreaName());
        ArgumentCaptor<SubjectCatalogEntry> captor = ArgumentCaptor.forClass(SubjectCatalogEntry.class);
        verify(subjectCatalogRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getSubjectAreaId());
    }

    @Test
    void importFromExcelUpdatesExistingSubjectAndKeepsId() throws Exception {
        SubjectArea oldArea = area(1L, "Старая область");
        SubjectArea newArea = area(2L, "Математика и информатика");
        SubjectCatalogEntry existing = new SubjectCatalogEntry();
        existing.setId(10L);
        existing.setSubjectName("Математика");
        existing.setSubjectType(SubjectType.CORE);
        existing.setSubjectAreaRef(oldArea);
        existing.setSubjectAreaName(oldArea.getName());
        existing.setSubjectCoefficient(BigDecimal.ONE);

        SubjectCatalogServiceImpl service = new SubjectCatalogServiceImpl(subjectCatalogRepository, subjectAreaRepository);
        when(subjectAreaRepository.findById(2L)).thenReturn(Optional.of(newArea));
        when(subjectCatalogRepository.findBySubjectNameAndSubjectType("Математика", SubjectType.CORE)).thenReturn(Optional.of(existing));
        when(subjectCatalogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.importFromExcel(subjectWorkbook(
                "Математика", "1", "Математика и информатика", "1.5", 2L
        ));

        assertEquals(1, result.get("imported"));
        assertEquals(0, result.get("skipped"));
        ArgumentCaptor<SubjectCatalogEntry> captor = ArgumentCaptor.forClass(SubjectCatalogEntry.class);
        verify(subjectCatalogRepository).save(captor.capture());
        SubjectCatalogEntry saved = captor.getValue();
        assertEquals(10L, saved.getId());
        assertEquals(2L, saved.getSubjectAreaId());
        assertEquals("Математика и информатика", saved.getSubjectAreaName());
        assertEquals(new BigDecimal("1.5"), saved.getSubjectCoefficient());
    }

    private SubjectArea area(Long id, String name) {
        SubjectArea area = new SubjectArea();
        area.setId(id);
        area.setName(name);
        return area;
    }

    private MockMultipartFile subjectWorkbook(String subjectName, String type, String areaName, String coefficient, Long areaId) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Предметы");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(subjectName);
            row.createCell(1).setCellValue(type);
            row.createCell(2).setCellValue(areaName);
            row.createCell(3).setCellValue(coefficient);
            row.createCell(4).setCellValue(areaId);
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "subjects.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

}
