package org.school.personalLoad.vsoko.mcko;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.MckoClassDiagnosticSummary;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VsokoMckoParallelSummaryTest {

    @Test
    void buildsWeightedSubjectParallelMatrixAndAssignsCityComparisonColors() throws Exception {
        MckoClassDiagnosticSummaryRepository summaries = mock(MckoClassDiagnosticSummaryRepository.class);
        when(summaries.findAllByAcademicYear("2025/2026")).thenReturn(List.of(
                summary("7-А", "Математика", 20, 70, 68D),
                summary("7-Б", "Математика", 30, 74, 70D),
                summary("8-А", "Математика", 25, 65, 65.5),
                summary("9-А", "Математика", 20, 59, 62D),
                summary("7-А", "Русский язык", 10, 80, null)));
        MckoStudentResultRepository results = mock(MckoStudentResultRepository.class);
        when(results.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc("2025/2026"))
                .thenReturn(List.of());
        VsokoMckoQueryService service = service(results, summaries);

        VsokoMckoDtos.ParallelSummary matrix = service.parallelSummary("2025-2026");

        assertEquals(List.of(7, 8, 9), matrix.parallels());
        VsokoMckoDtos.ParallelSubjectRow mathematics = matrix.subjects().stream()
                .filter(row -> row.subjectName().equals("Математика")).findFirst().orElseThrow();
        Map<Integer, VsokoMckoDtos.ParallelSubjectCell> cells = mathematics.parallels().stream()
                .collect(Collectors.toMap(VsokoMckoDtos.ParallelSubjectCell::parallel, Function.identity()));
        assertEquals(72.4, cells.get(7).schoolPercent());
        assertEquals(69.2, cells.get(7).cityPercent());
        assertEquals(50, cells.get(7).participantCount());
        assertEquals("ABOVE_CITY", cells.get(7).comparison());
        assertEquals("AT_CITY", cells.get(8).comparison());
        assertEquals("BELOW_CITY", cells.get(9).comparison());
        VsokoMckoDtos.ParallelSubjectCell russian = matrix.subjects().stream()
                .filter(row -> row.subjectName().equals("Русский язык")).findFirst().orElseThrow()
                .parallels().get(0);
        assertEquals("NO_CITY_DATA", russian.comparison());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                service.exportParallelSummary("2025/2026")))) {
            assertNotNull(workbook.getSheet("Матрица"));
            assertNotNull(workbook.getSheet("Данные"));
            assertEquals(IndexedColors.LIGHT_GREEN.getIndex(),
                    workbook.getSheet("Матрица").getRow(1).getCell(1).getCellStyle().getFillForegroundColor());
            assertEquals(IndexedColors.LIGHT_YELLOW.getIndex(),
                    workbook.getSheet("Матрица").getRow(1).getCell(2).getCellStyle().getFillForegroundColor());
            assertEquals(IndexedColors.ROSE.getIndex(),
                    workbook.getSheet("Матрица").getRow(1).getCell(3).getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void fallsBackToDetailedRowsWhenPdfClassSummaryIsMissing() {
        MckoClassDiagnosticSummaryRepository summaries = mock(MckoClassDiagnosticSummaryRepository.class);
        when(summaries.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        MckoStudentResultRepository results = mock(MckoStudentResultRepository.class);
        MckoStudentResult result = new MckoStudentResult();
        result.setAcademicYear("2025/2026");
        result.setClassName("6-В");
        result.setParallel(6);
        result.setSubjectName("Биология");
        result.setPercent(75D);
        result.setCityLevel("73%");
        when(results.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc("2025/2026"))
                .thenReturn(List.of(result));

        VsokoMckoDtos.ParallelSubjectCell cell = service(results, summaries).parallelSummary("2025/2026")
                .subjects().get(0).parallels().get(0);

        assertEquals(6, cell.parallel());
        assertEquals(75D, cell.schoolPercent());
        assertEquals(73D, cell.cityPercent());
        assertEquals("ABOVE_CITY", cell.comparison());
    }

    private MckoClassDiagnosticSummary summary(String className, String subject, int participants,
                                               double schoolPercent, Double cityPercent) {
        MckoClassDiagnosticSummary row = new MckoClassDiagnosticSummary();
        row.setAcademicYear("2025/2026");
        row.setClassName(className);
        row.setSubjectName(subject);
        row.setParticipantCount(participants);
        row.setAveragePercent(schoolPercent);
        row.setCityPercent(cityPercent);
        return row;
    }

    private VsokoMckoQueryService service(MckoStudentResultRepository results,
                                          MckoClassDiagnosticSummaryRepository summaries) {
        return new VsokoMckoQueryService(results, summaries, mock(MckoImportFileRepository.class),
                mock(MckoTeacherClassAssignmentRepository.class), mock(StudentProfileRepository.class),
                mock(StudentNameHistoryRepository.class), mock(StudentClassEnrollmentRepository.class),
                mock(TeacherDirectoryRepository.class), mock(ManualLoadEntryRepository.class),
                mock(PaReportStudentResultRepository.class), mock(OgeWorkResultRepository.class),
                mock(StudentResultLinker.class));
    }
}
