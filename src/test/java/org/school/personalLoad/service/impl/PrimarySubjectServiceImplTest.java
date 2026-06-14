package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.PrimarySubjectRuleRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherPrimarySubjectAssignmentRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrimarySubjectServiceImplTest {

    @Mock
    private PrimarySubjectRuleRepository ruleRepository;
    @Mock
    private TeacherPrimarySubjectAssignmentRepository assignmentRepository;
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadRepository;

    private PrimarySubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrimarySubjectServiceImpl(
                ruleRepository,
                assignmentRepository,
                teacherRepository,
                manualLoadRepository
        );
        lenient().when(ruleRepository.count()).thenReturn(2L);
        lenient().when(ruleRepository.findAllByOrderByPriorityAscPrimarySubjectAsc())
                .thenReturn(List.of(
                        rule("Русский язык и литература", "русск, литерат", 10),
                        rule("Математика", "математ, алгебр, геометр", 20)
                ));
    }

    @Test
    void determineSelectsRuleWithMostTeacherHours() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
        when(assignmentRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(
                load(1L, "Алгебра", "7-А", 12),
                load(1L, "Русский язык", "7-А", 5)
        ));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.determine("2026/2027");

        assertEquals(1, result.assigned());
        verify(assignmentRepository).save(argThat(assignment ->
                assignment.getMode() == PrimarySubjectAssignmentMode.AUTO
                        && "Математика".equals(assignment.getPrimarySubject())
        ));
    }

    @Test
    void determinePreservesManualAssignment() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        TeacherPrimarySubjectAssignment manual = new TeacherPrimarySubjectAssignment();
        manual.setAcademicYear("2026/2027");
        manual.setTeacherId(1L);
        manual.setPrimarySubject("Физика");
        manual.setMode(PrimarySubjectAssignmentMode.MANUAL);
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
        when(assignmentRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(manual));
        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(1L, "Алгебра", "7-А", 18)));

        var result = service.determine("2026/2027");

        assertEquals(1, result.preservedManual());
        verify(assignmentRepository, never()).save(any());
    }

    private PrimarySubjectRule rule(String subject, String value, int priority) {
        PrimarySubjectRule rule = new PrimarySubjectRule();
        rule.setPrimarySubject(subject);
        rule.setRuleType(PrimarySubjectRuleType.KEYWORDS);
        rule.setRuleValue(value);
        rule.setPriority(priority);
        return rule;
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private ManualLoadEntry load(Long teacherId, String subject, String className, int hours) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setTeacherId(teacherId);
        row.setFioTeacher("Иванов Иван Иванович");
        row.setSubjectName(subject);
        row.setClassName(className);
        row.setLoad(hours);
        return row;
    }
}
