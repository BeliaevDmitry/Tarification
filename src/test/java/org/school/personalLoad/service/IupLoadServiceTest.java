package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IupLoadServiceTest {

    @Mock
    private ManualLoadEntryRepository manualLoadRepository;
    @Mock
    private IupPlanRepository iupPlanRepository;
    @Mock
    private IupSubjectLineRepository iupSubjectLineRepository;
    @Mock
    private IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private StudentSupportStatusRepository supportStatusRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumRepository;
    @Mock
    private SubjectCatalogRepository subjectRepository;
    @Mock
    private ClassroomLeadershipRepository classroomRepository;
    @Mock
    private LoadSalaryCalculationService salaryCalculationService;
    @Mock
    private IupCompensationCalculator iupCompensationCalculator;
    @Mock
    private ObjectProvider<HrDocumentService> hrDocumentServiceProvider;
    @Mock
    private HrDocumentService hrDocumentService;

    @InjectMocks
    private IupLoadService service;

    @Test
    void issuedFaceToFaceAssignmentCreatesProtectedCommonLoadRow() {
        when(hrDocumentServiceProvider.getIfAvailable()).thenReturn(hrDocumentService);
        StudentProfile student = new StudentProfile();
        student.setId(11L);
        student.setCurrentFullName("Иванов Алексей Андреевич");

        IupPlan plan = new IupPlan();
        plan.setId(21L);
        plan.setStudent(student);
        plan.setAcademicYear("2026/2027");
        plan.setStatus(IupStatus.ACTIVE);
        plan.setValidFrom(LocalDate.of(2026, 9, 1));
        plan.setValidTo(LocalDate.of(2027, 5, 31));

        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setId(31L);
        subject.setSubjectName("Математика");
        subject.setSubjectType(SubjectType.CORE);

        CurriculumPlanEntry curriculum = new CurriculumPlanEntry();
        curriculum.setId(41L);
        curriculum.setAcademicYear("2026/2027");
        curriculum.setNumberSchoolBuilding("СП1");
        curriculum.setClassName("5-А");
        curriculum.setSubjectName("Математика");
        curriculum.setSubject(subject);
        curriculum.setEducationLevel(EducationLevel.BASIC);
        curriculum.setStudyPeriod(StudyPeriod.YEAR);

        IupSubjectLine line = new IupSubjectLine();
        line.setId(51L);
        line.setIupPlan(plan);
        line.setCurriculumEntryId(41L);
        line.setSubjectName("Математика");

        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(61L);
        teacher.setFioTeacher("Петров Пётр Петрович");

        IupTeacherAssignment assignment = new IupTeacherAssignment();
        assignment.setId(71L);
        assignment.setSubjectLine(line);
        assignment.setTeacher(teacher);
        assignment.setTeacherFioSnapshot(teacher.getFioTeacher());
        assignment.setHoursPerWeek(new BigDecimal("1.50"));
        assignment.setDeliveryForm(IupDeliveryForm.FACE_TO_FACE);
        assignment.setValidFrom(plan.getValidFrom());
        assignment.setValidTo(plan.getValidTo());

        StudentSupportStatus status = new StudentSupportStatus();
        status.setStudent(student);
        status.setAcademicYear(plan.getAcademicYear());
        status.setCategory(StudentCategory.K2);
        status.setValidFrom(plan.getValidFrom());

        when(iupPlanRepository.findById(21L)).thenReturn(Optional.of(plan));
        when(iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(21L))
                .thenReturn(List.of(line));
        when(iupTeacherAssignmentRepository.findAllBySubjectLine_IupPlan_Id(21L))
                .thenReturn(List.of(assignment));
        when(curriculumRepository.findById(41L)).thenReturn(Optional.of(curriculum));
        when(supportStatusRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                11L, "2026/2027"
        )).thenReturn(List.of(status));
        when(manualLoadRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronize(21L);

        ArgumentCaptor<List<ManualLoadEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(manualLoadRepository).deleteAllBySourceIupPlanId(21L);
        verify(manualLoadRepository).saveAll(captor.capture());
        ManualLoadEntry row = captor.getValue().get(0);
        assertEquals(ManualLoadSource.IUP, row.getLoadSource());
        assertEquals("ИУП-5-А-Иванов А.А.", row.getClassName());
        assertEquals(new BigDecimal("1.50"), row.getPreciseLoadHours());
        assertEquals(StudentCategory.K2, row.getIupStudentCategory());
        assertEquals(71L, row.getSourceIupAssignmentId());
        assertEquals(11L, row.getSourceStudentId());
        assertNull(row.getClassId());
        assertNull(row.getGroupNameEducationalPlan());
        verify(hrDocumentService).markAnnualIupAgreementsChanged("2026/2027", java.util.Set.of(61L));
    }
}
