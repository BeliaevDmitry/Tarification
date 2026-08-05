package org.school.personalLoad.vsoko.mcko;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.VsokoMckoImportBatch;
import org.school.personalLoad.vsoko.mcko.model.MckoImportFile;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoImportService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VsokoMckoImportServiceTest {

    @Test
    void importsOldReportLayoutAndLinksStudentByCode() throws Exception {
        VsokoMckoImportBatchRepository batchRepository = mock(VsokoMckoImportBatchRepository.class);
        MckoImportFileRepository fileRepository = mock(MckoImportFileRepository.class);
        MckoStudentResultRepository resultRepository = mock(MckoStudentResultRepository.class);
        MckoTeacherClassAssignmentRepository assignmentRepository = mock(MckoTeacherClassAssignmentRepository.class);
        when(batchRepository.save(any())).thenAnswer(invocation -> {
            VsokoMckoImportBatch row = invocation.getArgument(0); if (row.getId() == null) row.setId(1L); return row;
        });
        when(fileRepository.save(any())).thenAnswer(invocation -> {
            MckoImportFile row = invocation.getArgument(0); if (row.getId() == null) row.setId(10L); return row;
        });
        when(resultRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findAll()).thenReturn(List.of());

        StudentProfile profile = new StudentProfile();
        profile.setId(77L); profile.setCurrentFullName("Зайцева Олеся"); profile.setNormalizedRecordNumber("9116-0190");
        StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
        StudentNameHistoryRepository nameRepository = mock(StudentNameHistoryRepository.class);
        StudentClassEnrollmentRepository enrollmentRepository = mock(StudentClassEnrollmentRepository.class);
        when(profileRepository.findAll()).thenReturn(List.of(profile));
        when(nameRepository.findAll()).thenReturn(List.of());
        when(enrollmentRepository.findAll()).thenReturn(List.of());
        StudentResultLinker linker = new StudentResultLinker(profileRepository, nameRepository, enrollmentRepository);

        VsokoMckoImportService service = new VsokoMckoImportService(batchRepository, fileRepository, resultRepository,
                assignmentRepository, linker, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile("files", "ОБЩИЙ_отчет.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());

        VsokoMckoDtos.ImportResponse response = service.importFiles("2023/2024", List.of(file), "tester");

        assertEquals(1, response.filesProcessed());
        assertEquals(1, response.rowsImported());
        ArgumentCaptor<List<MckoStudentResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        MckoStudentResult result = captor.getValue().get(0);
        assertEquals(77L, result.getStudentId());
        assertEquals("Физика", result.getSubjectName());
        assertEquals(71D, result.getPercent());
        assertEquals(4, result.getMark());
        assertNotNull(result.getFingerprint());
    }

    private byte[] workbookBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Результаты");
            Row header = sheet.createRow(0);
            String[] headers = {"ФИО", "Код ученика", "Класс", "Предмет", "Дата", "Учебный год", "Школа",
                    "Уровень класса", "Уровень города", "Параллель", "Литера", "Вариант", "Балл", "Процент",
                    "Оценка", "Номер ученика", "JSON баллы"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row row = sheet.createRow(1);
            Object[] values = {"Зайцева Олеся", "9116-0190", "7-К", "Физика", "29.02.2024", "2023/2024",
                    "ГБОУ №7", "", "", 7, "К", "1007", 10, 71, 4, 6, "{\"1\":1}"};
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
                else row.createCell(i).setCellValue(String.valueOf(values[i]));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray();
        }
    }
}
