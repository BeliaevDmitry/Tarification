package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.StudentSupportDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.IupParticipationMode;
import org.school.personalLoad.model.IupSubjectLine;
import org.school.personalLoad.model.IupPlan;
import org.school.personalLoad.model.IupStatus;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.IupPlanRepository;
import org.school.personalLoad.repository.IupSubjectLineRepository;
import org.school.personalLoad.repository.IupTeacherAssignmentRepository;
import org.school.personalLoad.repository.NosologyCatalogEntryRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentGroupMembershipRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSupportServiceImplTest {

    private static final String YEAR = "2026/2027";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private ContingentSnapshotRepository snapshotRepository;
    @Mock
    private ContingentStudentRepository contingentStudentRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private StudentSupportStatusRepository supportStatusRepository;
    @Mock
    private IupPlanRepository iupPlanRepository;
    @Mock
    private IupSubjectLineRepository iupSubjectLineRepository;
    @Mock
    private IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    @Mock
    private StudentGroupMembershipRepository groupMembershipRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private NosologyCatalogEntryRepository nosologyCatalogEntryRepository;

    @InjectMocks
    private StudentSupportServiceImpl service;

    @Test
    void iupReplacesUnderlyingCategoryOnlyInClassHeadcount() {
        ContingentSnapshot snapshot = snapshot();
        List<StudentProfile> profiles = List.of(
                profile(1L, "Нормальный Ребёнок"),
                profile(2L, "Ребёнок К2"),
                profile(3L, "Ребёнок К3"),
                profile(4L, "Ребёнок К2 на ИУП")
        );
        List<ContingentStudent> rows = List.of(
                contingentRow(1L),
                contingentRow(2L),
                contingentRow(3L),
                contingentRow(4L),
                contingentRow(null)
        );
        StudentSupportStatus k2 = status(profiles.get(1), StudentCategory.K2);
        StudentSupportStatus k3 = status(profiles.get(2), StudentCategory.K3);
        StudentSupportStatus iupUnderlyingK2 = status(profiles.get(3), StudentCategory.K2);
        IupPlan iup = new IupPlan();
        iup.setId(80L);
        iup.setStudent(profiles.get(3));
        iup.setAcademicYear(YEAR);
        iup.setStatus(IupStatus.ACTIVE);
        iup.setValidFrom(DATE.minusDays(1));
        iup.setVersionNumber(1);

        when(snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(YEAR, DATE))
                .thenReturn(Optional.of(snapshot));
        when(contingentStudentRepository.findAllBySnapshotId(10L)).thenReturn(rows);
        when(studentProfileRepository.findAllById(any())).thenReturn(profiles);
        when(supportStatusRepository.findAllByAcademicYear(YEAR))
                .thenReturn(List.of(k2, k3, iupUnderlyingK2));
        when(iupPlanRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(iup));

        StudentSupportDtos.SummaryResponse summary = service.getSummary(YEAR, DATE, DATE);

        assertEquals(5, summary.getTotalStudents());
        assertEquals(1, summary.getUnlinkedStudents());
        assertEquals(1, summary.getClasses().size());
        StudentSupportDtos.ClassSummary classSummary = summary.getClasses().get(0);
        assertEquals(5, classSummary.getTotal());
        assertEquals(2, classSummary.getNormal());
        assertEquals(1, classSummary.getK2());
        assertEquals(1, classSummary.getK3());
        assertEquals(1, classSummary.getIup());
        StudentSupportDtos.RegisterRow iupRow = summary.getRegisterRows().stream()
                .filter(StudentSupportDtos.RegisterRow::getHasIup)
                .findFirst()
                .orElseThrow();
        assertEquals(StudentCategory.K2, iupRow.getUnderlyingCategory());
        assertTrue(summary.getWarnings().get(0).contains("1"));
    }

    @Test
    void categoryDerivedFromMseHasPriorityOverLegacyManualStatus() {
        ContingentSnapshot snapshot = snapshot();
        StudentProfile student = profile(1L, "Ребёнок со справкой МСЭ");
        StudentSupportStatus automatic = status(student, StudentCategory.K3);
        automatic.setSourceDocumentId(500L);
        automatic.setValidFrom(DATE.minusMonths(2));
        StudentSupportStatus manual = status(student, StudentCategory.K2);
        manual.setId(202L);
        manual.setValidFrom(DATE.minusDays(1));
        when(snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(YEAR, DATE))
                .thenReturn(Optional.of(snapshot));
        when(contingentStudentRepository.findAllBySnapshotId(10L)).thenReturn(List.of(contingentRow(1L)));
        when(studentProfileRepository.findAllById(any())).thenReturn(List.of(student));
        when(supportStatusRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(automatic, manual));
        when(iupPlanRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of());

        StudentSupportDtos.SummaryResponse summary = service.getSummary(YEAR, DATE, DATE);

        assertEquals(0, summary.getClasses().get(0).getK2());
        assertEquals(1, summary.getClasses().get(0).getK3());
        assertEquals(StudentCategory.K3, summary.getRegisterRows().get(0).getUnderlyingCategory());
    }

    @Test
    void referenceDataShowsEveryLinkedChildByPermanentCardAndReportsUnlinkedRows() {
        ContingentSnapshot snapshot = snapshot();
        StudentProfile first = profile(11L, "Иванов Иван Иванович");
        first.setBirthDate(LocalDate.of(2018, 2, 1));
        StudentProfile second = profile(12L, "Петрова Анна Сергеевна");
        when(snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(YEAR))
                .thenReturn(Optional.of(snapshot));
        when(contingentStudentRepository.findAllBySnapshotId(10L)).thenReturn(List.of(
                contingentRow(11L), contingentRow(12L), contingentRow(null)
        ));
        when(studentProfileRepository.findAllById(any())).thenReturn(List.of(first, second));

        StudentSupportDtos.ReferenceDataResponse references = service.getReferenceData(YEAR);

        assertEquals(2, references.getStudents().size());
        assertEquals(11L, references.getStudents().get(0).getStudentId());
        assertEquals(LocalDate.of(2018, 2, 1), references.getStudents().get(0).getBirthDate());
        assertEquals(3, references.getTotalContingentStudents());
        assertEquals(1, references.getUnlinkedStudents());
    }

    @Test
    void splitSubjectRequiresGroupWhenIupChildAttendsClass() {
        StudentProfile student = profile(1L, "Иванов Иван");
        CurriculumPlanEntry curriculum = new CurriculumPlanEntry();
        curriculum.setId(25L);
        curriculum.setAcademicYear(YEAR);
        curriculum.setClassName("5-А");
        curriculum.setSubjectName("Иностранный язык");
        curriculum.setSubgroupRequired(true);
        curriculum.setSubgroupCount(2);

        StudentSupportDtos.SubjectLineRequest subject = new StudentSupportDtos.SubjectLineRequest();
        subject.setCurriculumEntryId(25L);
        subject.setParticipationMode(IupParticipationMode.WITH_CLASS);
        subject.setClassHours(BigDecimal.valueOf(2));
        subject.setIndividualHours(BigDecimal.ZERO);

        StudentSupportDtos.IupSaveRequest request = new StudentSupportDtos.IupSaveRequest();
        request.setStudentId(1L);
        request.setStatus(IupStatus.ACTIVE);
        request.setOrderNumber("15");
        request.setOrderDate(DATE);
        request.setValidFrom(DATE);
        request.setSubjects(List.of(subject));

        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(iupPlanRepository.findAllByStudent_IdAndAcademicYearOrderByVersionNumberDesc(1L, YEAR))
                .thenReturn(List.of());
        when(iupPlanRepository.save(any(IupPlan.class))).thenAnswer(invocation -> {
            IupPlan plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });
        when(curriculumPlanEntryRepository.findById(25L)).thenReturn(Optional.of(curriculum));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveIup(YEAR, request)
        );

        assertTrue(exception.getMessage().contains("выберите группу"));
    }

    @Test
    void fractionalIupSubjectHoursAreRejected() {
        StudentProfile student = profile(1L, "Иванов Иван");
        StudentSupportDtos.SubjectLineRequest subject = new StudentSupportDtos.SubjectLineRequest();
        subject.setSubjectName("Математика");
        subject.setParticipationMode(IupParticipationMode.INDIVIDUAL);
        subject.setClassHours(BigDecimal.ZERO);
        subject.setIndividualHours(new BigDecimal("1.5"));

        StudentSupportDtos.IupSaveRequest request = draftIupRequest(subject);
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(iupPlanRepository.findAllByStudent_IdAndAcademicYearOrderByVersionNumberDesc(1L, YEAR))
                .thenReturn(List.of());
        when(iupPlanRepository.save(any(IupPlan.class))).thenAnswer(invocation -> {
            IupPlan plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveIup(YEAR, request)
        );

        assertTrue(exception.getMessage().contains("целым числом"));
    }

    @Test
    void fractionalTeacherHoursAreRejected() {
        StudentProfile student = profile(1L, "Иванов Иван");
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(7L);
        teacher.setFioTeacher("Петров Пётр");

        StudentSupportDtos.TeacherAssignmentRequest assignment =
                new StudentSupportDtos.TeacherAssignmentRequest();
        assignment.setTeacherId(7L);
        assignment.setHoursPerWeek(new BigDecimal("1.5"));

        StudentSupportDtos.SubjectLineRequest subject = new StudentSupportDtos.SubjectLineRequest();
        subject.setSubjectName("Математика");
        subject.setParticipationMode(IupParticipationMode.INDIVIDUAL);
        subject.setClassHours(BigDecimal.ZERO);
        subject.setIndividualHours(BigDecimal.ONE);
        subject.setTeachers(List.of(assignment));

        StudentSupportDtos.IupSaveRequest request = draftIupRequest(subject);
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(iupPlanRepository.findAllByStudent_IdAndAcademicYearOrderByVersionNumberDesc(1L, YEAR))
                .thenReturn(List.of());
        when(iupPlanRepository.save(any(IupPlan.class))).thenAnswer(invocation -> {
            IupPlan plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });
        when(iupSubjectLineRepository.save(any(IupSubjectLine.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(teacherDirectoryRepository.findById(7L)).thenReturn(Optional.of(teacher));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveIup(YEAR, request)
        );

        assertTrue(exception.getMessage().contains("целым числом"));
    }

    private StudentSupportDtos.IupSaveRequest draftIupRequest(
            StudentSupportDtos.SubjectLineRequest subject
    ) {
        StudentSupportDtos.IupSaveRequest request = new StudentSupportDtos.IupSaveRequest();
        request.setStudentId(1L);
        request.setStatus(IupStatus.DRAFT);
        request.setValidFrom(DATE);
        request.setSubjects(List.of(subject));
        return request;
    }

    private ContingentSnapshot snapshot() {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(10L);
        snapshot.setAcademicYear(YEAR);
        snapshot.setSnapshotDate(DATE);
        snapshot.setSourceFileName("Контингент.xlsx");
        return snapshot;
    }

    private StudentProfile profile(Long id, String fullName) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        profile.setCurrentFullName(fullName);
        profile.setNormalizedFullName(fullName.toLowerCase());
        return profile;
    }

    private ContingentStudent contingentRow(Long studentId) {
        ContingentStudent row = new ContingentStudent();
        row.setStudentId(studentId);
        row.setClassName("5-А");
        return row;
    }

    private StudentSupportStatus status(StudentProfile student, StudentCategory category) {
        StudentSupportStatus status = new StudentSupportStatus();
        status.setId(student.getId() + 100);
        status.setStudent(student);
        status.setAcademicYear(YEAR);
        status.setCategory(category);
        status.setValidFrom(DATE.minusDays(1));
        return status;
    }
}
