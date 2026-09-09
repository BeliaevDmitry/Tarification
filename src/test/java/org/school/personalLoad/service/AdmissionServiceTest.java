package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.AdmissionDtos;
import org.school.personalLoad.model.AdmissionCandidate;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;
import org.school.personalLoad.repository.AdmissionCandidateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private AdmissionCandidateRepository repository;

    private AdmissionService service;
    private List<AdmissionCandidate> stored;

    @BeforeEach
    void setUp() {
        stored = new ArrayList<>();
        service = new AdmissionService(repository);
        when(repository.save(any(AdmissionCandidate.class))).thenAnswer(invocation -> {
            AdmissionCandidate candidate = invocation.getArgument(0);
            if (candidate.getId() == null) {
                candidate.setId((long) (stored.size() + 1));
                stored.add(candidate);
            }
            return candidate;
        });
        when(repository.findAllByAcademicYearOrderByProcessedAscUpdatedAtDescIdDesc("2026/2027"))
                .thenAnswer(ignored -> List.copyOf(stored));
        when(repository.findByIdAndAcademicYear(any(), any())).thenAnswer(invocation -> stored.stream()
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

        AdmissionDtos.ActionRequest enroll = new AdmissionDtos.ActionRequest();
        enroll.setAction("ENROLL");
        var enrolled = service.action("2026/2027", 1L, enroll, "Заместитель директора");
        assertEquals(1, enrolled.getEnrolled());
        assertEquals(1, enrolled.getActive());
        assertEquals(AdmissionDocumentStatus.ENROLLMENT, enrolled.getCandidates().get(0).getDocumentStatus());

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
        service.action("2026/2027", 2L, refused, "Администратор");

        var overview = service.overview("2026/2027");

        assertEquals(3, overview.getTotal());
        assertEquals(2, overview.getParallels().size());
        assertEquals(2, overview.getParallels().get(0).getTotal());
        assertEquals(1, overview.getParallels().get(0).getRefused());
        assertEquals(8, overview.getParallels().get(1).getRequestedParallel());
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
