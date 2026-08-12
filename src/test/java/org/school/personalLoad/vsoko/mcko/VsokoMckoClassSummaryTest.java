package org.school.personalLoad.vsoko.mcko;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.MckoClassDiagnosticSummary;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VsokoMckoClassSummaryTest {
    @Test
    void usesPdfClassSummaryWhenDetailedStudentRowsAreAbsent() {
        MckoStudentResultRepository results = mock(MckoStudentResultRepository.class);
        when(results.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc("2024/2025"))
                .thenReturn(List.of());
        MckoClassDiagnosticSummaryRepository summaries = mock(MckoClassDiagnosticSummaryRepository.class);
        MckoClassDiagnosticSummary row = new MckoClassDiagnosticSummary();
        row.setAcademicYear("2024/2025");
        row.setClassName("8-Б");
        row.setSubjectName("Информационная безопасность");
        row.setParticipantCount(28);
        row.setAveragePercent(70.2);
        when(summaries.findAllByAcademicYear("2024/2025")).thenReturn(List.of(row));
        PaReportStudentResultRepository pa = mock(PaReportStudentResultRepository.class);
        when(pa.findAllByAcademicYear("2024/2025")).thenReturn(List.of());

        VsokoMckoQueryService service = new VsokoMckoQueryService(results, summaries,
                mock(MckoImportFileRepository.class), mock(MckoTeacherClassAssignmentRepository.class),
                mock(StudentProfileRepository.class), mock(StudentNameHistoryRepository.class),
                mock(StudentClassEnrollmentRepository.class), mock(TeacherDirectoryRepository.class),
                mock(ManualLoadEntryRepository.class), pa, mock(OgeWorkResultRepository.class),
                mock(StudentResultLinker.class));

        VsokoMckoDtos.ClassSubjectComparison comparison = service.classSummary("2024/2025", "8Б").subjects().get(0);
        assertEquals(28, comparison.mckoCount());
        assertEquals(70.2, comparison.mckoAveragePercent());
    }
}
