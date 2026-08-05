package org.school.personalLoad.vsoko.mcko;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentNameHistory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentLinkStatus;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentResultLinkerTest {

    @Test
    void linksByStableStudentCodeBeforeName() {
        StudentProfile profile = profile(7L, "Иванова Мария Сергеевна", "9116-0190");
        StudentResultLinker linker = linker(List.of(profile), List.of(), List.of());

        StudentResultLinker.LinkResult result = linker.buildIndex()
                .resolve(" 9116-0190 ", "Совсем Другое Имя", "2025/2026", "7-К");

        assertEquals(7L, result.studentId());
        assertEquals(MckoStudentLinkStatus.LINKED_BY_CODE, result.status());
    }

    @Test
    void linksByHistoricalNameAndClassWhenFioChanged() {
        StudentProfile profile = profile(8L, "Сидорова Анна Игоревна", null);
        StudentNameHistory oldName = new StudentNameHistory();
        oldName.setStudent(profile);
        oldName.setFullName("Петрова Анна Игоревна");
        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setStudent(profile);
        enrollment.setAcademicYear("2024/2025");
        enrollment.setClassName("6-А");
        StudentResultLinker linker = linker(List.of(profile), List.of(oldName), List.of(enrollment));

        StudentResultLinker.LinkResult result = linker.buildIndex()
                .resolve(null, "Петрова Анна", "2024/2025", "6А");

        assertEquals(8L, result.studentId());
        assertEquals(MckoStudentLinkStatus.LINKED_BY_NAME_AND_CLASS, result.status());
    }

    private StudentResultLinker linker(List<StudentProfile> profiles,
                                       List<StudentNameHistory> names,
                                       List<StudentClassEnrollment> enrollments) {
        StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
        StudentNameHistoryRepository nameRepository = mock(StudentNameHistoryRepository.class);
        StudentClassEnrollmentRepository enrollmentRepository = mock(StudentClassEnrollmentRepository.class);
        when(profileRepository.findAll()).thenReturn(profiles);
        when(nameRepository.findAll()).thenReturn(names);
        when(enrollmentRepository.findAll()).thenReturn(enrollments);
        return new StudentResultLinker(profileRepository, nameRepository, enrollmentRepository);
    }

    private StudentProfile profile(Long id, String name, String code) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        profile.setCurrentFullName(name);
        profile.setNormalizedRecordNumber(code);
        return profile;
    }
}
