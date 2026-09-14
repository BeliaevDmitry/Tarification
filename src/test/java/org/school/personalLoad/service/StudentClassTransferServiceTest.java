package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.StudentClassTransferDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.StudentClassTransferHistory;
import org.school.personalLoad.model.StudentClassTransferRequest;
import org.school.personalLoad.model.StudentClassTransferStatus;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassTransferHistoryRepository;
import org.school.personalLoad.repository.StudentClassTransferRequestRepository;
import org.school.personalLoad.repository.StudentProfileRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentClassTransferServiceTest {

    @Mock private StudentClassTransferRequestRepository requestRepository;
    @Mock private StudentClassTransferHistoryRepository historyRepository;
    @Mock private StudentProfileRepository studentRepository;
    @Mock private ContingentSnapshotRepository snapshotRepository;
    @Mock private ContingentStudentRepository contingentStudentRepository;

    private StudentClassTransferService service;
    private StudentProfile student;
    private final List<StudentClassTransferRequest> requests = new ArrayList<>();
    private final List<StudentClassTransferHistory> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StudentClassTransferService(requestRepository, historyRepository, studentRepository,
                snapshotRepository, contingentStudentRepository);
        student = new StudentProfile();
        student.setId(42L);
        student.setCurrentFullName("Иванов Иван Иванович");
        student.setNormalizedFullName("иванов иван иванович");

        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(10L);
        ContingentStudent current = new ContingentStudent();
        current.setStudentId(42L);
        current.setFullName(student.getCurrentFullName());
        current.setClassName("7-А");
        ContingentStudent targetClassStudent = new ContingentStudent();
        targetClassStudent.setStudentId(43L);
        targetClassStudent.setFullName("Петров Пётр Петрович");
        targetClassStudent.setClassName("7-Б");

        when(snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc("2026/2027"))
                .thenReturn(Optional.of(snapshot));
        when(contingentStudentRepository.findAllBySnapshotId(10L)).thenReturn(List.of(current, targetClassStudent));
        when(studentRepository.findById(42L)).thenReturn(Optional.of(student));
        lenient().when(studentRepository.findAllById(any())).thenReturn(List.of(student));
        lenient().when(requestRepository.save(any(StudentClassTransferRequest.class))).thenAnswer(invocation -> {
            StudentClassTransferRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(1L);
                requests.add(entity);
            }
            return entity;
        });
        lenient().when(historyRepository.save(any(StudentClassTransferHistory.class))).thenAnswer(invocation -> {
            StudentClassTransferHistory entity = invocation.getArgument(0);
            entity.setId((long) (history.size() + 1));
            history.add(0, entity);
            return entity;
        });
        lenient().when(requestRepository.findAllByAcademicYearOrderByUpdatedAtDescIdDesc("2026/2027"))
                .thenAnswer(ignored -> List.copyOf(requests));
        lenient().when(requestRepository.findByIdAndAcademicYear(1L, "2026/2027"))
                .thenAnswer(ignored -> requests.stream().findFirst());
        lenient().when(historyRepository.findAllByTransferRequest_IdInOrderByChangedAtDescIdDesc(any()))
                .thenAnswer(ignored -> List.copyOf(history));
    }

    @Test
    void keepsWaitingPromiseAndEveryChangeInHistory() {
        StudentClassTransferDtos.Access access = access();
        StudentClassTransferDtos.SaveRequest create = request("7-Б");

        var created = service.create("2026/2027", create, "Секретарь", access);

        assertEquals("7-А", created.getRequests().get(0).getFromClassName());
        assertEquals("7-Б", created.getRequests().get(0).getTargetClassName());
        assertEquals(StudentClassTransferStatus.WAITING_FOR_PLACE, created.getRequests().get(0).getStatus());
        assertEquals("Ждём место после окончания четверти", created.getRequests().get(0).getComment());
        assertEquals(1, created.getWaiting());
        assertEquals(1, created.getRequests().get(0).getHistory().size());

        StudentClassTransferDtos.SaveRequest update = request("7-Б");
        update.setStatus(StudentClassTransferStatus.PROMISED);
        update.setPromisedDate(LocalDate.of(2026, 10, 1));
        update.setPromiseNote("Перевести при появлении места");
        var updated = service.update("2026/2027", 1L, update, "Заместитель директора", access);

        assertEquals(LocalDate.of(2026, 10, 1), updated.getRequests().get(0).getPromisedDate());
        assertEquals(2, updated.getRequests().get(0).getHistory().size());
        assertEquals("Данные заявления изменены", updated.getRequests().get(0).getHistory().get(0).getAction());
    }

    @Test
    void rejectsTransferToAnotherParallel() {
        StudentClassTransferDtos.SaveRequest request = request("8-А");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create("2026/2027", request, "Секретарь", access()));

        assertEquals("Перевод можно зарегистрировать только внутри одной параллели", error.getMessage());
    }

    private StudentClassTransferDtos.SaveRequest request(String targetClass) {
        StudentClassTransferDtos.SaveRequest request = new StudentClassTransferDtos.SaveRequest();
        request.setStudentId(42L);
        request.setTargetClassName(targetClass);
        request.setRequestDate(LocalDate.of(2026, 9, 13));
        request.setReason("Просьба родителей");
        request.setComment("Ждём место после окончания четверти");
        return request;
    }

    private StudentClassTransferDtos.Access access() {
        StudentClassTransferDtos.Access access = new StudentClassTransferDtos.Access();
        access.setCanView(true);
        access.setCanEdit(true);
        return access;
    }
}
