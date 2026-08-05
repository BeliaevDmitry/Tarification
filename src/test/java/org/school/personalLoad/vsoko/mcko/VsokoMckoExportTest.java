package org.school.personalLoad.vsoko.mcko;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.vsoko.mcko.model.MckoResultType;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentLinkStatus;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VsokoMckoExportTest {

    @Test
    void exportKeepsLegacyFiveSheetStructure() throws Exception {
        MckoStudentResultRepository results = mock(MckoStudentResultRepository.class);
        MckoImportFileRepository files = mock(MckoImportFileRepository.class);
        MckoStudentResult row = new MckoStudentResult();
        row.setId(1L); row.setStudentFioSnapshot("Иванов Иван"); row.setStudentCode("9116-0001");
        row.setClassName("7-К"); row.setSubjectName("Физика"); row.setAcademicYear("2025/2026");
        row.setResultType(MckoResultType.STANDARD); row.setStudentLinkStatus(MckoStudentLinkStatus.NOT_FOUND);
        row.setPercent(75D); row.setMark(4);
        when(results.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc("2025/2026"))
                .thenReturn(List.of(row));
        when(files.findAll()).thenReturn(List.of());
        when(files.findTop200ByOrderByIdDesc()).thenReturn(List.of());
        VsokoMckoQueryService service = new VsokoMckoQueryService(results, files,
                mock(MckoTeacherClassAssignmentRepository.class), mock(StudentProfileRepository.class),
                mock(StudentNameHistoryRepository.class), mock(StudentClassEnrollmentRepository.class),
                mock(TeacherDirectoryRepository.class), mock(ManualLoadEntryRepository.class),
                mock(PaReportStudentResultRepository.class), mock(OgeWorkResultRepository.class),
                mock(StudentResultLinker.class));

        byte[] body = service.exportResults("2025/2026", null, null, null, null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            assertEquals(List.of("Результаты", "Функциональная грамотность", "Все работы", "Незагруженные работы", "Ошибки обработки"),
                    java.util.stream.IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(i -> workbook.getSheetAt(i).getSheetName()).toList());
            assertEquals("ФИО", workbook.getSheet("Результаты").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Иванов Иван", workbook.getSheet("Результаты").getRow(1).getCell(0).getStringCellValue());
        }
    }
}
