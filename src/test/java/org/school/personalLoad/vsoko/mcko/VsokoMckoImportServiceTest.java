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
import org.school.personalLoad.vsoko.mcko.model.MckoImportFile;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.model.VsokoMckoImportBatch;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.MckoParticipantRosterParser;
import org.school.personalLoad.vsoko.mcko.service.MckoLegacyPdfParser;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoImportService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VsokoMckoImportServiceTest {

    @Test
    void importsOldReportLayoutAndLinksStudentByCode() throws Exception {
        StudentProfile profile = new StudentProfile();
        profile.setId(77L);
        profile.setCurrentFullName("Зайцева Олеся");
        profile.setNormalizedRecordNumber("9116-0190");
        Fixture fixture = fixture(List.of(profile));

        MockMultipartFile file = new MockMultipartFile("files", "ОБЩИЙ_отчет.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", consolidatedWorkbookBytes());
        VsokoMckoDtos.ImportResponse response = fixture.service.importFiles("2023/2024", List.of(file), "tester");

        assertEquals(1, response.filesProcessed());
        assertEquals(1, response.rowsImported());
        MckoStudentResult result = savedResult(fixture.resultRepository);
        assertEquals(77L, result.getStudentId());
        assertEquals("Физика", result.getSubjectName());
        assertEquals(71D, result.getPercent());
        assertEquals(4, result.getMark());
        assertNotNull(result.getFingerprint());
    }

    @Test
    void importsRawMckoWorkFormWithParallelLetterAndStudentNumber() throws Exception {
        Fixture fixture = fixture(List.of());
        MockMultipartFile file = new MockMultipartFile("files", "9116_pm1991_20_October_2026_МАТ-9М.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", rawWorkFormBytes());

        VsokoMckoDtos.ImportResponse response = fixture.service.importFiles("2025/2026", List.of(file), "tester");

        assertEquals(1, response.filesProcessed());
        assertEquals(0, response.filesFailed());
        assertEquals(1, response.rowsImported());
        VsokoMckoDtos.FileStatusRow history = response.files().get(0);
        assertEquals("2022/2023", history.detectedAcademicYear());
        assertEquals("26.10.2022", history.detectedWorkDate());
        assertEquals("Математика", history.detectedSubject());
        MckoStudentResult result = savedResult(fixture.resultRepository);
        assertEquals("9-М", result.getClassName());
        assertEquals(LocalDate.of(2022, 10, 26), result.getDiagnosticDate());
        assertEquals("2022/2023", result.getAcademicYear());
        assertEquals(1, result.getStudentNumber());
        assertEquals(3D, result.getScore());
        assertEquals(21D, result.getPercent());
    }

    @Test
    void readsZipWhoseRussianEntryNamesUseCp866() throws Exception {
        Fixture fixture = fixture(List.of());
        MockMultipartFile file = new MockMultipartFile("files", "9116_list_mcl-28apr26-ir.zip",
                "application/zip", cp866ZipWithWorkbook());

        VsokoMckoDtos.ImportResponse response = fixture.service.importFiles("2022/2023", List.of(file), "tester");

        assertEquals(1, response.filesProcessed());
        assertEquals(0, response.filesFailed());
        assertEquals(1, response.rowsImported());
        assertEquals("Математика", response.files().get(0).detectedSubject());
        assertFalse(response.files().get(0).reason().contains("malformed input"));
    }

    private Fixture fixture(List<StudentProfile> profiles) {
        VsokoMckoImportBatchRepository batchRepository = mock(VsokoMckoImportBatchRepository.class);
        MckoImportFileRepository fileRepository = mock(MckoImportFileRepository.class);
        MckoStudentResultRepository resultRepository = mock(MckoStudentResultRepository.class);
        MckoParticipantRosterRepository rosterRepository = mock(MckoParticipantRosterRepository.class);
        MckoClassDiagnosticSummaryRepository classSummaryRepository = mock(MckoClassDiagnosticSummaryRepository.class);
        MckoTeacherClassAssignmentRepository assignmentRepository = mock(MckoTeacherClassAssignmentRepository.class);
        when(batchRepository.save(any())).thenAnswer(invocation -> {
            VsokoMckoImportBatch row = invocation.getArgument(0);
            if (row.getId() == null) row.setId(1L);
            return row;
        });
        when(fileRepository.save(any())).thenAnswer(invocation -> {
            MckoImportFile row = invocation.getArgument(0);
            if (row.getId() == null) row.setId(10L);
            return row;
        });
        when(resultRepository.findAll()).thenReturn(List.of());
        when(resultRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rosterRepository.findAll()).thenReturn(List.of());
        when(rosterRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classSummaryRepository.findAll()).thenReturn(List.of());
        when(classSummaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.findAll()).thenReturn(List.of());

        StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
        StudentNameHistoryRepository nameRepository = mock(StudentNameHistoryRepository.class);
        StudentClassEnrollmentRepository enrollmentRepository = mock(StudentClassEnrollmentRepository.class);
        when(profileRepository.findAll()).thenReturn(profiles);
        when(nameRepository.findAll()).thenReturn(List.of());
        when(enrollmentRepository.findAll()).thenReturn(List.of());
        StudentResultLinker linker = new StudentResultLinker(profileRepository, nameRepository, enrollmentRepository);
        VsokoMckoImportService service = new VsokoMckoImportService(batchRepository, fileRepository, resultRepository,
                rosterRepository, classSummaryRepository, assignmentRepository, linker,
                new MckoParticipantRosterParser(), new MckoLegacyPdfParser(), new ObjectMapper());
        return new Fixture(service, resultRepository);
    }

    private MckoStudentResult savedResult(MckoStudentResultRepository repository) {
        ArgumentCaptor<List<MckoStudentResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().get(0).get(0);
    }

    private byte[] consolidatedWorkbookBytes() throws Exception {
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
            writeRow(row, values);
            return workbookBytes(workbook);
        }
    }

    private byte[] rawWorkFormBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Лист 1");
            sheet.createRow(0).createCell(0).setCellValue("Рабочая форма для внутреннего пользования!");
            Row header = sheet.createRow(1);
            String[] headers = {"Школа", "Параллель", "Буква", "Предмет", "Дата", "Фамилия, имя", "№ уч.",
                    "Вариант", "1", "2", "3", "Балл", "% вып."};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row row = sheet.createRow(2);
            writeRow(row, new Object[]{"ГБОУ Школа № 7", 9, "М", "Математика", "26.10.2022", "", 1,
                    1023, 1, 0, "4-", 3, 21});
            return workbookBytes(workbook);
        }
    }

    private byte[] cp866ZipWithWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, Charset.forName("CP866"))) {
            zip.putNextEntry(new ZipEntry("Русский язык/результаты класса.xlsx"));
            zip.write(rawWorkFormBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private void writeRow(Row row, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
            else row.createCell(i).setCellValue(String.valueOf(values[i]));
        }
    }

    private byte[] workbookBytes(Workbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private record Fixture(VsokoMckoImportService service, MckoStudentResultRepository resultRepository) {}
}
