package org.school.personalLoad.vsoko.mcko;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.oge.model.OgeWorkResult;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VsokoMckoReconcileTest {

    @Test
    void reconcileAlsoBackfillsExistingOgeResultsToPermanentStudentCard() {
        StudentProfile student = new StudentProfile();
        student.setId(91L);
        student.setCurrentFullName("Иванов Иван Иванович");
        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setStudent(student);
        enrollment.setAcademicYear("2025/2026");
        enrollment.setClassName("9-А");

        StudentProfileRepository profiles = mock(StudentProfileRepository.class);
        StudentNameHistoryRepository names = mock(StudentNameHistoryRepository.class);
        StudentClassEnrollmentRepository enrollments = mock(StudentClassEnrollmentRepository.class);
        when(profiles.findAll()).thenReturn(List.of(student));
        when(names.findAll()).thenReturn(List.of());
        when(enrollments.findAll()).thenReturn(List.of(enrollment));
        StudentResultLinker linker = new StudentResultLinker(profiles, names, enrollments);

        MckoStudentResultRepository mcko = mock(MckoStudentResultRepository.class);
        PaReportStudentResultRepository pa = mock(PaReportStudentResultRepository.class);
        OgeWorkResultRepository oge = mock(OgeWorkResultRepository.class);
        when(mcko.findAll()).thenReturn(List.of());
        when(pa.findAll()).thenReturn(List.of());
        OgeWorkResult ogeRow = new OgeWorkResult();
        ogeRow.setFullName("Иванов Иван");
        ogeRow.setAcademicYear("2025/2026");
        ogeRow.setClassName("9А");
        ogeRow.setNeedsManualStudentMatch(true);
        ogeRow.setSourceIssue("старое несопоставленное значение");
        when(oge.findAll()).thenReturn(List.of(ogeRow));

        VsokoMckoQueryService service = new VsokoMckoQueryService(mcko, mock(MckoClassDiagnosticSummaryRepository.class),
                mock(MckoImportFileRepository.class),
                mock(MckoTeacherClassAssignmentRepository.class), profiles, names, enrollments,
                mock(TeacherDirectoryRepository.class), mock(ManualLoadEntryRepository.class), pa, oge, linker);

        service.reconcile();

        assertEquals(91L, ogeRow.getStudentId());
        assertFalse(ogeRow.isNeedsManualStudentMatch());
        assertNull(ogeRow.getSourceIssue());
        verify(oge).saveAll(List.of(ogeRow));
    }
}
