package org.school.personalLoad.repository;

import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherDirectoryRepository extends JpaRepository<TeacherDirectoryEntry, Long> {
    Optional<TeacherDirectoryEntry> findByFioTeacher(String fioTeacher);
}
