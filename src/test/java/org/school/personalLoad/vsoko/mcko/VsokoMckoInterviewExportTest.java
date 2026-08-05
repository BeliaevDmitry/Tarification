package org.school.personalLoad.vsoko.mcko;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.model.MckoTeacherClassAssignment;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VsokoMckoInterviewExportTest {

    @Test
    void createsPrintableTeacherSheetWithMckoAndPaComparison() throws Exception {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(5L);
        teacher.setFioTeacher("Петрова Анна Сергеевна");
        TeacherDirectoryRepository teachers = mock(TeacherDirectoryRepository.class);
        when(teachers.findAllById(any())).thenReturn(List.of(teacher));

        MckoTeacherClassAssignment assignment = new MckoTeacherClassAssignment();
        assignment.setAcademicYear("2025/2026");
        assignment.setClassName("7-А");
        assignment.setSubjectName("Математика");
        assignment.setTeacherId(5L);
        MckoTeacherClassAssignmentRepository assignments = mock(MckoTeacherClassAssignmentRepository.class);
        when(assignments.findAllByAcademicYearOrderByClassNameAscSubjectNameAsc("2025/2026"))
                .thenReturn(List.of(assignment));

        MckoStudentResult mckoRow = new MckoStudentResult();
        mckoRow.setStudentId(101L);
        mckoRow.setAcademicYear("2025/2026");
        mckoRow.setClassName("7-А");
        mckoRow.setSubjectName("Математика");
        mckoRow.setPercent(80D);
        mckoRow.setMark(4);
        MckoStudentResultRepository mcko = mock(MckoStudentResultRepository.class);
        when(mcko.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc("2025/2026"))
                .thenReturn(List.of(mckoRow));

        PaReportStudentResult paRow = new PaReportStudentResult();
        paRow.setStudentId(101L);
        paRow.setAcademicYear("2025/2026");
        paRow.setClassName("7А");
        paRow.setSubjectName("Математика");
        paRow.setPercent(70D);
        paRow.setMark(3);
        paRow.setHasResult(true);
        PaReportStudentResultRepository pa = mock(PaReportStudentResultRepository.class);
        when(pa.findAllByAcademicYear("2025/2026")).thenReturn(List.of(paRow));

        VsokoMckoQueryService service = new VsokoMckoQueryService(mcko, mock(MckoImportFileRepository.class),
                assignments, mock(StudentProfileRepository.class), mock(StudentNameHistoryRepository.class),
                mock(StudentClassEnrollmentRepository.class), teachers, mock(ManualLoadEntryRepository.class),
                pa, mock(OgeWorkResultRepository.class), mock(StudentResultLinker.class));

        byte[] body = service.interviewWorkbook("2025/2026", List.of(5L));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheet("Петрова Анна Сергеевна");
            assertNotNull(sheet);
            assertEquals("Класс", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("7-А", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1D, sheet.getRow(4).getCell(2).getNumericCellValue());
            assertEquals(1D, sheet.getRow(4).getCell(14).getNumericCellValue());
            assertEquals(4, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }
}
