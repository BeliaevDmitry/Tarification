package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.StudentIdentityMatchStatus;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentIdentityServiceImplTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private StudentNameHistoryRepository nameHistoryRepository;
    @Mock private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock private ContingentSnapshotRepository snapshotRepository;
    @Mock private ContingentStudentRepository contingentStudentRepository;
    @Mock private ClassroomLeadershipRepository classroomLeadershipRepository;

    private StudentIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentIdentityServiceImpl(
                studentProfileRepository,
                nameHistoryRepository,
                enrollmentRepository,
                snapshotRepository,
                contingentStudentRepository,
                classroomLeadershipRepository
        );
    }

    @Test
    void compactRowReusesUniquePermanentProfileByFullName() {
        StudentProfile existing = new StudentProfile();
        existing.setId(15L);
        existing.setCurrentFullName("Иванов Иван Иванович");
        existing.setNormalizedFullName("иванов иван иванович");
        existing.setFirstSeenDate(LocalDate.of(2024, 9, 1));
        existing.setLastSeenDate(LocalDate.of(2025, 5, 31));

        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        when(studentProfileRepository.findAll()).thenReturn(List.of(existing));
        when(nameHistoryRepository.findAll()).thenReturn(List.of());
        when(enrollmentRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());

        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(22L);
        snapshot.setAcademicYear("2025/2026");
        snapshot.setSnapshotDate(LocalDate.of(2025, 9, 1));
        ContingentStudent row = new ContingentStudent();
        row.setRecordNumber(UUID.randomUUID().toString());
        row.setFullName("Иванов Иван Иванович");
        row.setBirthDate("");
        row.setClassName("2-А");
        row.setPhone("+7 900 111-22-33");
        row.setRepresentativeName("Иванова Мария Петровна");
        row.setRepresentativePhone("+7 900 444-55-66");

        var result = service.linkStudents(snapshot, List.of(row));

        assertEquals(1, result.linked());
        assertEquals(0, result.created());
        assertEquals(15L, row.getStudentId());
        assertEquals(StudentIdentityMatchStatus.LINKED_BY_NAME_ONLY, row.getIdentityMatchStatus());
        assertEquals("+7 900 111-22-33", existing.getChildPhone());
        assertEquals("Иванова Мария Петровна", existing.getRepresentativeName());
        assertEquals("+7 900 444-55-66", existing.getRepresentativePhone());
    }

    @Test
    void duplicateNamesInOneCompactFileRemainAmbiguous() {
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(23L);
        snapshot.setAcademicYear("2025/2026");
        snapshot.setSnapshotDate(LocalDate.of(2025, 9, 1));
        ContingentStudent first = compactRow("Петров Алексей Сергеевич", "2-А");
        ContingentStudent second = compactRow("Петров Алексей Сергеевич", "2-Б");

        var result = service.linkStudents(snapshot, List.of(first, second));

        assertEquals(0, result.linked());
        assertEquals(0, result.created());
        assertEquals(2, result.ambiguous());
        assertEquals(StudentIdentityMatchStatus.AMBIGUOUS, first.getIdentityMatchStatus());
        assertEquals(StudentIdentityMatchStatus.AMBIGUOUS, second.getIdentityMatchStatus());
    }

    @Test
    void rowsWithBirthDatesAreCreatedAndLinkedInBatches() {
        when(classroomLeadershipRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(studentProfileRepository.findAll()).thenReturn(List.of());
        when(studentProfileRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<StudentProfile> profiles = invocation.getArgument(0);
            long id = 100L;
            for (StudentProfile profile : profiles) {
                profile.setId(id++);
            }
            return profiles;
        });
        when(nameHistoryRepository.findAll()).thenReturn(List.of());
        when(enrollmentRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());

        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(24L);
        snapshot.setAcademicYear("2026/2027");
        snapshot.setSnapshotDate(LocalDate.of(2026, 8, 16));
        ContingentStudent first = compactRow("Иванов Иван Иванович", "3-А");
        first.setBirthDate("01.02.2018");
        first.setPhone("9001002030");
        first.setRepresentativeName("Иванова Мария Сергеевна");
        first.setRepresentativePhone("9112223344");
        ContingentStudent second = compactRow("Петрова Анна Олеговна", "3-Б");
        second.setBirthDate("03.04.2018");

        var result = service.linkStudents(snapshot, List.of(first, second));

        assertEquals(2, result.created());
        assertEquals(0, result.linked());
        assertEquals(100L, first.getStudentId());
        assertEquals(101L, second.getStudentId());
        assertEquals(StudentIdentityMatchStatus.CREATED, first.getIdentityMatchStatus());
    }

    private ContingentStudent compactRow(String fullName, String className) {
        ContingentStudent row = new ContingentStudent();
        row.setRecordNumber(UUID.randomUUID().toString());
        row.setFullName(fullName);
        row.setBirthDate("");
        row.setClassName(className);
        return row;
    }
}
