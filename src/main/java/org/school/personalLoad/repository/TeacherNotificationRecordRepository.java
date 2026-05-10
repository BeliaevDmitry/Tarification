package org.school.personalLoad.repository;

import org.school.personalLoad.model.TeacherNotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherNotificationRecordRepository extends JpaRepository<TeacherNotificationRecord, Long> {
    List<TeacherNotificationRecord> findAllByAcademicYear(String academicYear);
    Optional<TeacherNotificationRecord> findByAcademicYearAndFioTeacherIgnoreCase(String academicYear, String fioTeacher);
}
