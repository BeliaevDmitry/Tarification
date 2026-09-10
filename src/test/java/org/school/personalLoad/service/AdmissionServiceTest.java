package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.AdmissionDtos;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.model.AdmissionAccessRole;
import org.school.personalLoad.model.AdmissionCandidate;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.AdmissionCandidateRepository;
import org.school.personalLoad.repository.AdmissionRoleAssignmentRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private AdmissionCandidateRepository repository;
    @Mock
    private AdmissionRoleAssignmentRepository roleRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private ContingentService contingentService;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private ContingentSnapshotRepository snapshotRepository;
    @Mock
    private ContingentStudentRepository contingentStudentRepository;

    private AdmissionService service;
    private List<AdmissionCandidate> stored;

    @BeforeEach
    void setUp() {
        stored = new ArrayList<>();
        service = new AdmissionService(repository, roleRepository, userRepository, contingentService,
                studentProfileRepository, enrollmentRepository, snapshotRepository, contingentStudentRepository);
        lenient().when(repository.save(any(AdmissionCandidate.class))).thenAnswer(invocation -> {
            AdmissionCandidate candidate = invocation.getArgument(0);
            if (candidate.getId() == null) {
                candidate.setId((long) (stored.size() + 1));
                stored.add(candidate);
            }
            return candidate;
        });
        lenient().when(repository.findAllByAcademicYearOrderByProcessedAscUpdatedAtDescIdDesc("2026/2027"))
                .thenAnswer(ignored -> stored.stream()
                        .filter(candidate -> "2026/2027".equals(candidate.getAcademicYear())).toList());
        lenient().when(repository.findAll()).thenAnswer(ignored -> List.copyOf(stored));
        lenient().when(repository.findByIdAndAcademicYear(any(), any())).thenAnswer(invocation -> stored.stream()
                .filter(candidate -> candidate.getId().equals(invocation.getArgument(0)))
                .filter(candidate -> candidate.getAcademicYear().equals(invocation.getArgument(1)))
                .findFirst());
    }

    @Test
    void admissionWorkflowKeepsDecisionAndProcessedFlagSeparately() {
        AdmissionDtos.SaveRequest request = request("Иванов Иван Иванович", 5, "5-Б");

        var created = service.create("2026/2027", request, "Администратор");
        assertEquals(1, created.getTotal());
        assertEquals(1, created.getActive());
        assertEquals(5, created.getParallels().get(0).getRequestedParallel());

        AdmissionDtos.ActionRequest agree = new AdmissionDtos.ActionRequest();
        agree.setAction("AGREE");
        agree.setAssignedClass("5-Б");
        agree.setComment("Место подтверждено");
        service.action("2026/2027", 1L, agree, "Заместитель директора");
        AdmissionDtos.ActionRequest enroll = new AdmissionDtos.ActionRequest();
        enroll.setAction("SET_STATUS");
        enroll.setDocumentStatus(AdmissionDocumentStatus.ENROLLED);
        var enrolled = service.action("2026/2027", 1L, enroll, "Заместитель директора");
        assertEquals(1, enrolled.getEnrolled());
        assertEquals(1, enrolled.getActive());
        assertEquals(AdmissionDocumentStatus.ENROLLED, enrolled.getCandidates().get(0).getDocumentStatus());
        assertEquals("5-Б", enrolled.getCandidates().get(0).getAssignedClass());

        AdmissionDtos.ActionRequest processed = new AdmissionDtos.ActionRequest();
        processed.setAction("PROCESSED");
        var completed = service.action("2026/2027", 1L, processed, "Заместитель директора");
        assertEquals(AdmissionDecisionStatus.ENROLLED, completed.getCandidates().get(0).getDecisionStatus());
        assertEquals(0, completed.getActive());
        assertEquals(1, completed.getProcessed());
    }

    @Test
    void enrollmentRequiresAssignedClass() {
        service.create("2026/2027", request("Петрова Анна Сергеевна", 7, ""), "Администратор");
        AdmissionDtos.ActionRequest action = new AdmissionDtos.ActionRequest();
        action.setAction("ENROLL");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.action("2026/2027", 1L, action, "Администратор")
        );

        assertEquals("Перед зачислением укажите класс зачисления", error.getMessage());
    }

    @Test
    void statisticsAreGroupedByRequestedParallel() {
        service.create("2026/2027", request("Первый Ребёнок", 1, "1-А"), "Администратор");
        service.create("2026/2027", request("Второй Ребёнок", 1, "1-Б"), "Администратор");
        service.create("2026/2027", request("Третий Ребёнок", 8, ""), "Администратор");
        AdmissionDtos.ActionRequest refused = new AdmissionDtos.ActionRequest();
        refused.setAction("REFUSE");
        refused.setComment("Нет свободных мест");
        service.action("2026/2027", 2L, refused, "Администратор");

        var overview = service.overview("2026/2027");

        assertEquals(3, overview.getTotal());
        assertEquals(2, overview.getParallels().size());
        assertEquals(2, overview.getParallels().get(0).getTotal());
        assertEquals(1, overview.getParallels().get(0).getRefused());
        assertEquals(8, overview.getParallels().get(1).getRequestedParallel());
    }

    @Test
    void agreementRejectsClassFromAnotherParallelAndTestingIsIndependent() {
        service.create("2026/2027", request("Тестовый Ребёнок", 6, ""), "Секретарь");
        AdmissionDtos.ActionRequest testing = new AdmissionDtos.ActionRequest();
        testing.setAction("TESTING");
        testing.setTesting(true);
        assertEquals(true, service.action("2026/2027", 1L, testing, "Секретарь")
                .getCandidates().get(0).isTesting());

        AdmissionDtos.ActionRequest agree = new AdmissionDtos.ActionRequest();
        agree.setAction("AGREE");
        agree.setAssignedClass("7-А");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.action("2026/2027", 1L, agree, "Заместитель директора"));
        assertEquals("Можно выбрать только класс 6 параллели", error.getMessage());
    }

    @Test
    void secretaryEditsButOnlyAssignedDecisionMakerCanApproveOrRefuse() {
        SessionUser secretary = new SessionUser();
        secretary.setId(10L);
        secretary.setRole(UserRole.SECRETARY);
        var secretaryAccess = service.access(secretary);
        assertTrue(secretaryAccess.isCanView());
        assertTrue(secretaryAccess.isCanEdit());
        assertFalse(secretaryAccess.isCanDecide());

        SessionUser decisionMaker = new SessionUser();
        decisionMaker.setId(20L);
        decisionMaker.setRole(UserRole.EMPLOYEE);
        when(roleRepository.existsByUserIdAndRole(20L, AdmissionAccessRole.SECRETARY)).thenReturn(false);
        when(roleRepository.existsByUserIdAndRole(20L, AdmissionAccessRole.DECISION_MAKER)).thenReturn(true);
        var decisionAccess = service.access(decisionMaker);
        assertTrue(decisionAccess.isCanView());
        assertFalse(decisionAccess.isCanEdit());
        assertTrue(decisionAccess.isCanDecide());
    }

    @Test
    void overviewIncludesClassSizesForAgreementDialog() {
        ContingentDtos.ClassTotal classTotal = new ContingentDtos.ClassTotal();
        classTotal.setClassName("5-А");
        classTotal.setParallel(5);
        classTotal.setStudents(27);
        ContingentDtos.AddressColumn address = new ContingentDtos.AddressColumn();
        address.setClasses(List.of(classTotal));
        ContingentDtos.BuildingColumn building = new ContingentDtos.BuildingColumn();
        building.setAddresses(List.of(address));
        ContingentDtos.StatsResponse stats = new ContingentDtos.StatsResponse();
        stats.setColumns(List.of(building));
        when(contingentService.getStats("2026/2027", null)).thenReturn(stats);

        var overview = service.overview("2026/2027");

        assertEquals(1, overview.getClassOptions().size());
        assertEquals("5-А", overview.getClassOptions().get(0).getClassName());
        assertEquals(27, overview.getClassOptions().get(0).getStudents());
    }

    @Test
    void additionalInfoShowsPreviousDecisionAndFormerContingentClass() {
        AdmissionCandidate previous = new AdmissionCandidate();
        previous.setId(99L);
        previous.setAcademicYear("2025/2026");
        previous.setFullName("Иванов Иван Иванович");
        previous.setRequestedParallel(5);
        previous.setDocumentStatus(AdmissionDocumentStatus.PLACE_OFFERED);
        previous.setDecisionStatus(AdmissionDecisionStatus.REFUSED);
        stored.add(previous);

        StudentProfile profile = new StudentProfile();
        profile.setId(42L);
        profile.setCurrentFullName("Иванов Иван Иванович");
        profile.setNormalizedFullName("иванов иван иванович");
        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setStudent(profile);
        enrollment.setAcademicYear("2024/2025");
        enrollment.setClassName("4-А");
        ContingentSnapshot currentSnapshot = new ContingentSnapshot();
        currentSnapshot.setId(501L);

        when(studentProfileRepository.findAllByNormalizedFullNameIn(any())).thenReturn(List.of(profile));
        when(enrollmentRepository.findAllByStudent_IdIn(any())).thenReturn(List.of(enrollment));
        when(snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2026/2027"))
                .thenReturn(Optional.of(currentSnapshot));
        when(contingentStudentRepository.findAllBySnapshotId(501L)).thenReturn(List.of());

        var overview = service.create("2026/2027",
                request("Иванов Иван Иванович", 6, ""), "Секретарь");
        String info = overview.getCandidates().get(0).getAdditionalInfo();

        assertTrue(info.contains("Ранее подавался (2025/2026): отказ"));
        assertTrue(info.contains("4-А — 2024/2025"));
        assertTrue(info.contains("в последней выгрузке отсутствует"));
    }

    private AdmissionDtos.SaveRequest request(String fullName, int parallel, String assignedClass) {
        AdmissionDtos.SaveRequest request = new AdmissionDtos.SaveRequest();
        request.setFullName(fullName);
        request.setRequestedParallel(parallel);
        request.setDocumentStatus(AdmissionDocumentStatus.MOS_RU_SUBMITTED);
        request.setProblems("Нет оригинала документа");
        request.setComment("Пригласить на беседу");
        request.setAssignedClass(assignedClass);
        return request;
    }
}
